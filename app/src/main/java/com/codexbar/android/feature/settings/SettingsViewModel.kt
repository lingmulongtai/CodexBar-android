package com.codexbar.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.repository.QuotaRepository
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.security.PrivacySettings
import com.codexbar.android.di.ClaudeRepository
import com.codexbar.android.di.CodexRepository
import com.codexbar.android.di.GeminiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ClaudeRepository private val claudeRepository: QuotaRepository,
    @CodexRepository private val codexRepository: QuotaRepository,
    @GeminiRepository private val geminiRepository: QuotaRepository,
    private val prefsManager: EncryptedPrefsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            prefsManager.warmCache()
            loadSavedCredentials()
            _uiState.update {
                it.copy(
                    refreshIntervalMinutes = prefsManager.getRefreshInterval(),
                    notificationsEnabled = prefsManager.isNotificationsEnabled(),
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
                is Credential.GeminiCredential -> ServiceCredentialState(
                    accessToken = credential.accessToken,
                    refreshToken = credential.refreshToken,
                    oauthClientId = credential.oauthClientId,
                    oauthClientSecret = credential.oauthClientSecret,
                    expiresAtDisplay = formatExpiryMs(credential.expiresAtMs),
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
                "oauthClientId" -> current.copy(oauthClientId = value, validationResult = null, hasUnsavedChanges = true)
                "oauthClientSecret" -> current.copy(oauthClientSecret = value, validationResult = null, hasUnsavedChanges = true)
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
            AiService.GEMINI -> {
                if (state.refreshToken.isBlank() || state.oauthClientId.isBlank() || state.oauthClientSecret.isBlank()) {
                    return null
                }
                Credential.GeminiCredential(
                    accessToken = state.accessToken,
                    refreshToken = state.refreshToken,
                    expiresAtMs = System.currentTimeMillis() + 3600_000, // default 1h
                    oauthClientId = state.oauthClientId,
                    oauthClientSecret = state.oauthClientSecret
                )
            }
        }
    }

    fun validateCredential(service: AiService) {
        val repo = when (service) {
            AiService.CLAUDE -> claudeRepository
            AiService.CODEX -> codexRepository
            AiService.GEMINI -> geminiRepository
        }

        val state = _uiState.value.serviceStates[service] ?: return
        val credential = buildCredential(service, state)
        if (credential == null) {
            _uiState.update { currentState ->
                val current = currentState.serviceStates[service] ?: ServiceCredentialState()
                currentState.copy(
                    serviceStates = currentState.serviceStates + (service to current.copy(
                        validationResult = ValidationResult.Failure("Required credential fields are incomplete")
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
            val previousCredential = prefsManager.loadCredential(service)
            prefsManager.saveCredential(service, credential)

            val result = repo.validateCredential()
            val validationResult = when (result) {
                is Result.Success -> ValidationResult.Success
                is Result.Failure -> {
                    if (previousCredential != null) {
                        prefsManager.saveCredential(service, previousCredential)
                    } else {
                        prefsManager.deleteCredential(service)
                    }
                    ValidationResult.Failure(formatAppError(result.error))
                }
            }

            _uiState.update { state ->
                val current = state.serviceStates[service] ?: ServiceCredentialState()
                state.copy(
                    serviceStates = state.serviceStates + (service to current.copy(
                        isValidating = false,
                        validationResult = validationResult,
                        isConnected = validationResult is ValidationResult.Success || previousCredential != null,
                        hasUnsavedChanges = validationResult !is ValidationResult.Success
                    ))
                )
            }
        }
    }

    fun setRefreshInterval(minutes: Long) {
        _uiState.update { it.copy(refreshIntervalMinutes = minutes) }
        viewModelScope.launch {
            prefsManager.setRefreshInterval(minutes)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _uiState.update { it.copy(notificationsEnabled = enabled) }
        viewModelScope.launch {
            prefsManager.setNotificationsEnabled(enabled)
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
        }
    }

    fun deleteAllCredentials() {
        _uiState.update {
            SettingsUiState(
                refreshIntervalMinutes = it.refreshIntervalMinutes,
                notificationsEnabled = it.notificationsEnabled,
                privacySettings = it.privacySettings
            )
        }
        viewModelScope.launch {
            prefsManager.deleteAllCredentials()
        }
    }

    private fun formatExpiryMs(expiresAtMs: Long): String {
        return try {
            val instant = Instant.ofEpochMilli(expiresAtMs)
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault())
            formatter.format(instant)
        } catch (_: Exception) {
            "Unknown"
        }
    }

    private fun formatAppError(error: AppError): String {
        return when (error) {
            is AppError.NetworkError -> "Network error: ${error.message}"
            is AppError.AuthError -> if (error.isTerminal) "Authentication failed (re-login required)" else "Authentication error"
            is AppError.RateLimited -> error.retryAt?.let { "Rate limited until $it" }
                ?: "Rate limited — try again later"
            is AppError.ParseError -> "Parse error: ${error.message}"
            is AppError.CredentialNotFound -> "No credentials saved"
            is AppError.ServiceUnavailable -> "Service temporarily unavailable"
        }
    }
}
