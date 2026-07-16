package com.codexbar.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.codexbar.android.R
import com.codexbar.android.core.auth.AccountLinkManager
import com.codexbar.android.core.auth.DeviceAuthSession
import com.codexbar.android.core.data.QuotaHistoryStore
import com.codexbar.android.core.data.QuotaRepositoryRegistry
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.ProviderSecretKind
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.monitoring.MonitoringSessionStore
import com.codexbar.android.core.network.gemini.GeminiCompanionPairing
import com.codexbar.android.core.notification.QuotaNotificationService
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.security.PrivacySettings
import com.codexbar.android.core.widget.WidgetPrefsManager
import com.codexbar.android.core.workmanager.RefreshIntervalPolicy
import com.codexbar.android.core.workmanager.WorkManagerInitializer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.UnknownHostException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repositoryRegistry: QuotaRepositoryRegistry,
    private val accountLinkManager: AccountLinkManager,
    private val prefsManager: EncryptedPrefsManager,
    private val quotaHistoryStore: QuotaHistoryStore,
    private val widgetPrefsManager: WidgetPrefsManager,
    private val monitoringSessionStore: MonitoringSessionStore,
    private val notificationService: QuotaNotificationService,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            prefsManager.warmCache()
            loadSavedCredentials()
            val monitoringSession = monitoringSessionStore.activeSession()
            _uiState.update {
                it.copy(
                    refreshIntervalMinutes = RefreshIntervalPolicy.normalize(
                        prefsManager.getRefreshInterval()
                    ),
                    persistentNotificationEnabled = prefsManager.isPersistentNotificationEnabled(),
                    isMonitoring = monitoringSession != null,
                    monitoringDurationMinutes = monitoringSessionStore.preferredDurationMinutes(),
                    monitoringRemainingMinutes = monitoringSession?.remainingMinutes(),
                    privacySettings = prefsManager.getPrivacySettings()
                )
            }
        }
    }

    private suspend fun loadSavedCredentials() {
        for (service in AiService.entries) {
            val credential = prefsManager.loadCredential(service) ?: continue
            val state = when (credential) {
                is Credential.ClaudeCredential -> ServiceCredentialState(
                    accessToken = credential.accessToken,
                    refreshToken = credential.refreshToken ?: "",
                    isConnected = true
                )
                is Credential.CodexCredential -> ServiceCredentialState(
                    accessToken = credential.accessToken,
                    refreshToken = credential.refreshToken,
                    accountId = credential.accountId ?: "",
                    isConnected = true
                )
                is Credential.GeminiCompanionCredential -> ServiceCredentialState(
                    isConnected = true
                )
                is Credential.CopilotCredential -> ServiceCredentialState(
                    accessToken = credential.accessToken,
                    isConnected = true
                )
                is Credential.ProviderSecretCredential -> ServiceCredentialState(
                    accessToken = credential.accessToken,
                    isConnected = true
                )
            }
            _uiState.update {
                it.copy(serviceStates = it.serviceStates + (service to state))
            }
        }
    }

    fun updateField(service: AiService, field: String, value: String) {
        _uiState.update { state ->
            val current = state.serviceStates[service] ?: ServiceCredentialState()
            val updated = when (field) {
                "accessToken" -> current.copy(accessToken = value, validationResult = null, hasUnsavedChanges = true)
                "refreshToken" -> current.copy(refreshToken = value, validationResult = null, hasUnsavedChanges = true)
                "accountId" -> current.copy(accountId = value, validationResult = null, hasUnsavedChanges = true)
                else -> current
            }
            state.copy(serviceStates = state.serviceStates + (service to updated))
        }
    }

    private fun buildCredential(service: AiService, state: ServiceCredentialState): Credential? {
        if (state.accessToken.isBlank()) return null

        return when (service) {
            AiService.CLAUDE -> Credential.ClaudeCredential(
                accessToken = state.accessToken,
                refreshToken = state.refreshToken.ifBlank { null }
            )
            AiService.CODEX -> {
                if (state.refreshToken.isBlank()) return null
                Credential.CodexCredential(
                    accessToken = state.accessToken,
                    refreshToken = state.refreshToken,
                    accountId = state.accountId.ifBlank { null }
                )
            }
            AiService.GEMINI -> null
            AiService.COPILOT -> Credential.CopilotCredential(
                accessToken = state.accessToken
            )
            AiService.CURSOR -> Credential.ProviderSecretCredential(
                service = service,
                kind = ProviderSecretKind.COOKIE_HEADER,
                accessToken = state.accessToken.trim()
            )
            AiService.ZAI -> Credential.ProviderSecretCredential(
                service = service,
                kind = ProviderSecretKind.API_KEY,
                accessToken = state.accessToken.trim()
            )
            AiService.ZENMUX -> Credential.ProviderSecretCredential(
                service = service,
                kind = ProviderSecretKind.API_KEY,
                accessToken = state.accessToken.trim()
            )
        }
    }

    fun validateCredential(service: AiService) {
        val repo = repositoryFor(service)

        val state = _uiState.value.serviceStates[service] ?: return
        val credential = buildCredential(service, state)
        if (credential == null) {
            _uiState.update { currentState ->
                val current = currentState.serviceStates[service] ?: ServiceCredentialState()
                currentState.copy(
                    serviceStates = currentState.serviceStates + (service to current.copy(
                        validationResult = ValidationResult.Failure(
                            appContext.getString(R.string.validation_required_fields)
                        )
                    ))
                )
            }
            return
        }

        _uiState.update { state ->
            val current = state.serviceStates[service] ?: ServiceCredentialState()
            state.copy(
                serviceStates = state.serviceStates + (service to current.copy(isValidating = true, validationResult = null))
            )
        }

        viewModelScope.launch {
            val hadPreviousCredential = prefsManager.loadCredential(service) != null
            val result = repo.validateCredential(credential)
            val validationResult = when (result) {
                is Result.Success -> {
                    prefsManager.saveCredential(service, credential)
                    ValidationResult.Success
                }
                is Result.Failure -> {
                    ValidationResult.Failure(formatAppError(result.error))
                }
            }

            _uiState.update { state ->
                val current = state.serviceStates[service] ?: ServiceCredentialState()
                state.copy(
                    serviceStates = state.serviceStates + (service to current.copy(
                        isValidating = false,
                        validationResult = validationResult,
                        isConnected = validationResult is ValidationResult.Success || hadPreviousCredential,
                        hasUnsavedChanges = validationResult !is ValidationResult.Success
                    ))
                )
            }
        }
    }

    fun startAccountLink(service: AiService) {
        if (!service.supportsDeviceAccountLink()) return

        _uiState.update { state ->
            val current = state.serviceStates[service] ?: ServiceCredentialState()
            state.copy(
                serviceStates = state.serviceStates + (service to current.copy(
                    isAccountLinking = true,
                    accountLinkPrompt = null,
                    validationResult = null,
                    hasUnsavedChanges = false
                ))
            )
        }

        viewModelScope.launch {
            try {
                val hadPreviousCredential = prefsManager.loadCredential(service) != null
                val session = accountLinkManager.requestDeviceCode(service)
                _uiState.updateAccountLinkPrompt(service, session)

                val credential = accountLinkManager.completeDeviceCode(session)
                val validationResult = when (
                    val result = repositoryFor(service).validateCredential(credential)
                ) {
                    is Result.Success -> {
                        prefsManager.saveCredential(service, credential)
                        ValidationResult.Success
                    }
                    is Result.Failure -> ValidationResult.Failure(formatAppError(result.error))
                }
                val validationSucceeded = validationResult is ValidationResult.Success

                _uiState.update { state ->
                    val current = state.serviceStates[service] ?: ServiceCredentialState()
                    state.copy(
                        serviceStates = state.serviceStates + (service to current.copy(
                            accessToken = if (validationSucceeded) credential.accessToken else current.accessToken,
                            refreshToken = if (validationSucceeded) {
                                credential.refreshToken ?: ""
                            } else {
                                current.refreshToken
                            },
                            isAccountLinking = false,
                            accountLinkPrompt = null,
                            validationResult = validationResult,
                            isConnected = validationSucceeded || hadPreviousCredential,
                            hasUnsavedChanges = false
                        ))
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { state ->
                    val current = state.serviceStates[service] ?: ServiceCredentialState()
                    state.copy(
                        serviceStates = state.serviceStates + (service to current.copy(
                            isAccountLinking = false,
                            accountLinkPrompt = null,
                            validationResult = ValidationResult.Failure(
                                if (e.hasUnknownHostCause()) {
                                    appContext.getString(
                                        R.string.validation_account_link_dns_failed,
                                        service.displayName
                                    )
                                } else {
                                    e.message?.let {
                                        appContext.getString(
                                            R.string.validation_account_link_failed_detail,
                                            it
                                        )
                                    } ?: appContext.getString(
                                        R.string.validation_account_link_failed
                                    )
                                }
                            ),
                            isConnected = current.isConnected
                        ))
                    )
                }
            }
        }
    }

    fun setRefreshInterval(minutes: Long) {
        val normalizedMinutes = RefreshIntervalPolicy.normalize(minutes)
        _uiState.update { it.copy(refreshIntervalMinutes = normalizedMinutes) }
        viewModelScope.launch {
            prefsManager.setRefreshInterval(normalizedMinutes)
            WorkManagerInitializer.applyRefreshPolicy(appContext, normalizedMinutes)
        }
    }

    fun refreshLocalizedSurfaces() {
        WorkManagerInitializer.enqueueManualQuotaRefresh(
            context = appContext,
            source = "language_changed"
        )
    }

    fun setPersistentNotificationEnabled(enabled: Boolean) {
        _uiState.update {
            it.copy(persistentNotificationEnabled = enabled)
        }
        viewModelScope.launch {
            prefsManager.setPersistentNotificationEnabled(enabled)
            if (enabled) {
                WorkManagerInitializer.enqueueManualQuotaRefresh(
                    appContext,
                    source = "persistent_notification_enabled"
                )
            } else {
                notificationService.cancelQuotaNotification()
            }
        }
    }

    fun setMonitoringDuration(minutes: Long) {
        val bounded = minutes.coerceIn(
            MonitoringSessionStore.MIN_DURATION_MINUTES,
            MonitoringSessionStore.MAX_DURATION_MINUTES
        )
        monitoringSessionStore.setPreferredDurationMinutes(bounded)
        _uiState.update { it.copy(monitoringDurationMinutes = bounded) }
    }

    fun startMonitoring() {
        viewModelScope.launch {
            val session = WorkManagerInitializer.startMonitoringSession(
                context = appContext,
                durationMinutes = _uiState.value.monitoringDurationMinutes
            )
            notificationService.showMonitoringPlaceholder(session)
            _uiState.update {
                it.copy(
                    isMonitoring = true,
                    monitoringRemainingMinutes = session.remainingMinutes()
                )
            }
        }
    }

    fun stopMonitoring() {
        WorkManagerInitializer.stopMonitoringSession(appContext)
        notificationService.cancelMonitoringNotification()
        _uiState.update {
            it.copy(isMonitoring = false, monitoringRemainingMinutes = null)
        }
    }

    fun syncMonitoringState() {
        val session = monitoringSessionStore.activeSession()
        _uiState.update {
            it.copy(
                isMonitoring = session != null,
                monitoringRemainingMinutes = session?.remainingMinutes()
            )
        }
    }

    fun setPrivacySettings(settings: PrivacySettings) {
        _uiState.update { it.copy(privacySettings = settings) }
        viewModelScope.launch {
            prefsManager.setPrivacySettings(settings)
        }
    }

    fun showDeleteConfirmDialog() {
        _uiState.update { it.copy(showDeleteConfirmDialog = true) }
    }

    fun dismissDeleteConfirmDialog() {
        _uiState.update { it.copy(showDeleteConfirmDialog = false) }
    }

    fun showDisconnectConfirmDialog(service: AiService) {
        _uiState.update { it.copy(disconnectConfirmService = service) }
    }

    fun dismissDisconnectConfirmDialog() {
        _uiState.update { it.copy(disconnectConfirmService = null) }
    }

    fun disconnectService(service: AiService) {
        _uiState.update { state ->
            state.copy(
                serviceStates = state.serviceStates + (service to ServiceCredentialState()),
                disconnectConfirmService = null
            )
        }
        viewModelScope.launch {
            prefsManager.deleteCredential(service)
            quotaHistoryStore.deleteService(service)
            widgetPrefsManager.deleteServiceCache(service)
        }
    }

    fun deleteAllCredentials() {
        _uiState.update {
            SettingsUiState(
                refreshIntervalMinutes = it.refreshIntervalMinutes,
                persistentNotificationEnabled = it.persistentNotificationEnabled,
                isMonitoring = it.isMonitoring,
                monitoringDurationMinutes = it.monitoringDurationMinutes,
                monitoringRemainingMinutes = it.monitoringRemainingMinutes,
                privacySettings = it.privacySettings
            )
        }
        viewModelScope.launch {
            prefsManager.deleteAllCredentials()
            AiService.entries.forEach { quotaHistoryStore.deleteService(it) }
            widgetPrefsManager.deleteAllServiceCaches()
        }
    }

    private fun formatExpiryMs(expiresAtMs: Long): String {
        return try {
            val instant = Instant.ofEpochMilli(expiresAtMs)
            val locale = appContext.resources.configuration.locales[0]
            val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(locale)
                .withZone(ZoneId.systemDefault())
            formatter.format(instant)
        } catch (_: Exception) {
            appContext.getString(R.string.validation_unknown)
        }
    }

    private fun repositoryFor(service: AiService) = repositoryRegistry.repositoryFor(service)

    private fun AiService.supportsDeviceAccountLink(): Boolean {
        return this == AiService.CODEX || this == AiService.COPILOT
    }

    fun updateGeminiPairingCode(value: String) {
        _uiState.update { state ->
            val current = state.serviceStates[AiService.GEMINI] ?: ServiceCredentialState()
            state.copy(
                serviceStates = state.serviceStates + (
                    AiService.GEMINI to current.copy(
                        geminiPairingCode = value.take(MAX_PAIRING_CODE_LENGTH),
                        validationResult = null,
                        hasUnsavedChanges = true
                    )
                )
            )
        }
    }

    fun importGeminiPairingCode(value: String) {
        updateGeminiPairingCode(value)
    }

    fun connectGeminiCompanion() {
        val state = _uiState.value.serviceStates[AiService.GEMINI] ?: return
        val credential = runCatching {
            GeminiCompanionPairing.parse(state.geminiPairingCode)
        }.getOrElse { error ->
            updateGeminiValidation(
                isValidating = false,
                validationResult = ValidationResult.Failure(
                    appContext.getString(
                        R.string.validation_gemini_pairing_invalid,
                        error.message ?: appContext.getString(R.string.validation_unknown)
                    )
                ),
                keepExistingConnection = true
            )
            return
        }

        updateGeminiValidation(
            isValidating = true,
            validationResult = null,
            keepExistingConnection = true
        )
        viewModelScope.launch {
            val hadPreviousConnection = prefsManager.loadCredential(AiService.GEMINI) != null
            val result = repositoryFor(AiService.GEMINI).validateCredential(credential)
            when (result) {
                is Result.Success -> {
                    prefsManager.saveCredential(AiService.GEMINI, credential)
                    updateGeminiValidation(
                        isValidating = false,
                        validationResult = ValidationResult.Success,
                        keepExistingConnection = false,
                        connected = true,
                        clearPairingCode = true
                    )
                    WorkManagerInitializer.enqueueManualQuotaRefresh(
                        appContext,
                        source = "gemini_companion_connected"
                    )
                }
                is Result.Failure -> {
                    updateGeminiValidation(
                        isValidating = false,
                        validationResult = ValidationResult.Failure(
                            appContext.getString(
                                R.string.validation_gemini_companion_failed,
                                formatAppError(result.error)
                            )
                        ),
                        keepExistingConnection = true,
                        connected = hadPreviousConnection
                    )
                }
            }
        }
    }

    private fun updateGeminiValidation(
        isValidating: Boolean,
        validationResult: ValidationResult?,
        keepExistingConnection: Boolean,
        connected: Boolean = false,
        clearPairingCode: Boolean = false
    ) {
        _uiState.update { state ->
            val current = state.serviceStates[AiService.GEMINI] ?: ServiceCredentialState()
            state.copy(
                serviceStates = state.serviceStates + (
                    AiService.GEMINI to current.copy(
                        geminiPairingCode = if (clearPairingCode) "" else current.geminiPairingCode,
                        isValidating = isValidating,
                        validationResult = validationResult,
                        isConnected = if (keepExistingConnection) {
                            current.isConnected || connected
                        } else {
                            connected
                        },
                        hasUnsavedChanges = !clearPairingCode && current.geminiPairingCode.isNotBlank()
                    )
                )
            )
        }
    }

    private fun MutableStateFlow<SettingsUiState>.updateAccountLinkPrompt(
        service: AiService,
        session: DeviceAuthSession
    ) {
        update { state ->
            val current = state.serviceStates[service] ?: ServiceCredentialState()
            state.copy(
                serviceStates = state.serviceStates + (service to current.copy(
                    isAccountLinking = true,
                    accountLinkPrompt = AccountLinkPrompt(
                        verificationUrl = session.verificationUrl,
                        userCode = session.userCode,
                        expiresAtDisplay = formatExpiryMs(session.expiresAtEpochMs)
                    )
                ))
            )
        }
    }

    private fun formatAppError(error: AppError): String {
        return when (error) {
            is AppError.NetworkError -> appContext.getString(
                R.string.validation_network_error,
                error.message
            )
            is AppError.AuthError -> appContext.getString(
                if (error.isTerminal) {
                    R.string.validation_authentication_required
                } else {
                    R.string.validation_authentication_error
                }
            )
            is AppError.RateLimited -> error.retryAt?.let {
                appContext.getString(R.string.validation_rate_limited_until, it)
            } ?: appContext.getString(R.string.validation_rate_limited)
            is AppError.ParseError -> appContext.getString(
                R.string.validation_parse_error,
                error.message
            )
            is AppError.CredentialNotFound -> appContext.getString(
                R.string.validation_no_credentials
            )
            is AppError.ServiceUnavailable -> appContext.getString(
                R.string.validation_service_unavailable
            )
        }
    }
}

internal fun Throwable.hasUnknownHostCause(): Boolean {
    val visited = mutableSetOf<Throwable>()
    var current: Throwable? = this
    while (current != null && visited.add(current)) {
        if (current is UnknownHostException) return true
        current = current.cause
    }
    return false
}

private const val MAX_PAIRING_CODE_LENGTH = 2048
