package com.codexbar.android.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codexbar.android.core.data.QuotaHistoryStore
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.repository.QuotaRepository
import com.codexbar.android.core.presentation.PrivacyPresentation
import com.codexbar.android.core.presentation.QuotaPresentationMapper
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.di.ClaudeRepository
import com.codexbar.android.di.CodexRepository
import com.codexbar.android.di.CopilotRepository
import com.codexbar.android.di.GeminiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val quotaHistoryStore: QuotaHistoryStore
) : ViewModel() {

    private val presentationMapper = QuotaPresentationMapper()

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_isRefreshing.value) return

        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val repos = buildList {
                    if (prefsManager.loadCredential(AiService.CLAUDE) != null) add(AiService.CLAUDE to claudeRepository)
                    if (prefsManager.loadCredential(AiService.CODEX) != null) add(AiService.CODEX to codexRepository)
                    if (prefsManager.loadCredential(AiService.GEMINI) != null) add(AiService.GEMINI to geminiRepository)
                    if (prefsManager.loadCredential(AiService.COPILOT) != null) add(AiService.COPILOT to copilotRepository)
                }

                if (repos.isEmpty()) {
                    _uiState.value = DashboardUiState.Content(
                        presentationMapper.map(emptyList(), generatedAt = Instant.now())
                    )
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
                    redactSensitiveValues = privacySettings.widgetRedactionEnabled,
                    lockScreenRedacted = privacySettings.lockScreenRedactionEnabled,
                    widgetRedacted = privacySettings.widgetRedactionEnabled
                )

                _uiState.value = DashboardUiState.Content(
                    presentationMapper.map(
                        quotas = successfulQuotas,
                        errors = errors,
                        generatedAt = now,
                        privacy = privacy,
                        paceByMetricKey = paceByMetricKey
                    )
                )
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
