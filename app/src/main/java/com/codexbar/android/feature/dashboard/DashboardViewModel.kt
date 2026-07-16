package com.codexbar.android.feature.dashboard

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codexbar.android.core.data.QuotaHistoryStore
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.repository.QuotaRepository
import com.codexbar.android.core.monitoring.MonitoringSessionStore
import com.codexbar.android.core.notification.QuotaNotificationService
import com.codexbar.android.core.presentation.AndroidQuotaPresentationText
import com.codexbar.android.core.presentation.PrivacyPresentation
import com.codexbar.android.core.presentation.QuotaPresentationMapper
import com.codexbar.android.core.presentation.QuotaPresentationSnapshot
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.widget.QuotaGlanceWidget
import com.codexbar.android.core.widget.WidgetPrefsManager
import com.codexbar.android.di.ClaudeRepository
import com.codexbar.android.di.CodexRepository
import com.codexbar.android.di.CopilotRepository
import com.codexbar.android.di.GeminiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ClaudeRepository private val claudeRepository: QuotaRepository,
    @CodexRepository private val codexRepository: QuotaRepository,
    @GeminiRepository private val geminiRepository: QuotaRepository,
    @CopilotRepository private val copilotRepository: QuotaRepository,
    private val prefsManager: EncryptedPrefsManager,
    private val quotaHistoryStore: QuotaHistoryStore,
    private val monitoringSessionStore: MonitoringSessionStore,
    private val notificationService: QuotaNotificationService,
    private val widgetPrefsManager: WidgetPrefsManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val presentationMapper = QuotaPresentationMapper(
        text = AndroidQuotaPresentationText(appContext)
    )

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true

        viewModelScope.launch {
            try {
                val repos = buildList {
                    if (prefsManager.loadCredential(AiService.CLAUDE) != null) add(AiService.CLAUDE to claudeRepository)
                    if (prefsManager.loadCredential(AiService.CODEX) != null) add(AiService.CODEX to codexRepository)
                    if (prefsManager.loadCredential(AiService.GEMINI) != null) add(AiService.GEMINI to geminiRepository)
                    if (prefsManager.loadCredential(AiService.COPILOT) != null) add(AiService.COPILOT to copilotRepository)
                }

                if (repos.isEmpty()) {
                    val snapshot = presentationMapper.map(
                        emptyList(),
                        generatedAt = Instant.now()
                    )
                    publishSnapshot(snapshot)
                    return@launch
                }

                val deferreds = repos.map { (service, repo) ->
                    async { service to repo.fetchQuota() }
                }

                val results = deferreds.map { it.await() }

                val successfulQuotas = mutableListOf<com.codexbar.android.core.domain.model.QuotaInfo>()
                val errors = mutableMapOf<AiService, AppError>()

                for ((service, result) in results) {
                    when (result) {
                        is Result.Success -> {
                            successfulQuotas.add(result.value)
                        }
                        is Result.Failure -> {
                            errors[service] = result.error
                        }
                    }
                }

                val now = Instant.now()
                quotaHistoryStore.record(successfulQuotas)
                val paceByMetricKey = quotaHistoryStore.paceFor(successfulQuotas, now)
                val privacySettings = prefsManager.getPrivacySettings()
                val privacy = PrivacyPresentation(
                    redactSensitiveValues = false,
                    lockScreenRedacted = privacySettings.lockScreenRedactionEnabled,
                    widgetRedacted = privacySettings.widgetRedactionEnabled
                )

                val snapshot = presentationMapper.map(
                    quotas = successfulQuotas,
                    errors = errors,
                    generatedAt = now,
                    privacy = privacy,
                    paceByMetricKey = paceByMetricKey
                )
                publishSnapshot(snapshot)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private suspend fun publishSnapshot(snapshot: QuotaPresentationSnapshot) {
        _uiState.value = DashboardUiState.Content(snapshot)
        notificationService.publishSnapshot(
            snapshot = snapshot,
            monitoringSession = monitoringSessionStore.activeSession()
        )

        // The dashboard already owns the freshest provider response. Persist and render that
        // exact snapshot instead of waiting for a second WorkManager network request.
        try {
            snapshot.services.forEach(widgetPrefsManager::cachePresentation)
            QuotaGlanceWidget().updateAll(appContext)
        } catch (_: Exception) {
            // Widget rendering must not turn a successful dashboard refresh into an app error.
        }
    }
}
