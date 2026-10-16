package com.codexbar.android.feature.dashboard

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codexbar.android.core.data.QuotaHistoryStore
import com.codexbar.android.core.data.QuotaResetHistoryStore
import com.codexbar.android.core.data.QuotaResetMonitor
import com.codexbar.android.core.data.QuotaRepositoryRegistry
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.monitoring.MonitoringSessionStore
import com.codexbar.android.core.notification.QuotaNotificationService
import com.codexbar.android.core.presentation.AndroidQuotaPresentationText
import com.codexbar.android.core.presentation.PrivacyPresentation
import com.codexbar.android.core.presentation.QuotaPresentationMapper
import com.codexbar.android.core.presentation.QuotaPresentationSnapshot
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.security.ConnectionHealthStore
import com.codexbar.android.core.widget.WidgetUpdater
import com.codexbar.android.core.widget.WidgetPrefsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repositoryRegistry: QuotaRepositoryRegistry,
    private val prefsManager: EncryptedPrefsManager,
    private val connectionHealthStore: ConnectionHealthStore,
    private val quotaHistoryStore: QuotaHistoryStore,
    private val quotaResetHistoryStore: QuotaResetHistoryStore,
    private val quotaResetMonitor: QuotaResetMonitor,
    private val monitoringSessionStore: MonitoringSessionStore,
    private val notificationService: QuotaNotificationService,
    private val widgetPrefsManager: WidgetPrefsManager,
    private val orderPrefs: DashboardOrderPrefs,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val presentationMapper = QuotaPresentationMapper(
        text = AndroidQuotaPresentationText(appContext)
    )

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    private var refreshPending = false
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _providerOrder = MutableStateFlow(orderPrefs.load())
    val providerOrder: StateFlow<List<AiService>> = _providerOrder.asStateFlow()

    fun setProviderOrder(order: List<AiService>) {
        _providerOrder.value = orderPrefs.save(order)
    }

    init {
        viewModelScope.launch {
            quotaResetMonitor.sourceChanges.collect { revision ->
                if (revision > 0) {
                    _uiState.value = DashboardUiState.Loading
                    refreshPending = true
                    refresh()
                }
            }
        }
        viewModelScope.launch {
            quotaResetHistoryStore.history.collect { history ->
                _uiState.update { state ->
                    if (state is DashboardUiState.Content) state.copy(snapshot = state.snapshot.copy(
                        services = state.snapshot.services.map { it.copy(resetHistory =
                            if (state.snapshot.privacy.redactSensitiveValues) emptyList() else history[it.service].orEmpty()) }
                    )) else state
                }
            }
        }
        refresh()
    }

    fun clearResetHistory(service: AiService) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { quotaResetHistoryStore.clearHistory(service) }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        refreshPending = false

        viewModelScope.launch {
            try {
                prefsManager.warmCache()
                val sourceRevision = repositoryRegistry.prepareRefresh()
                val repos = repositoryRegistry.entries().mapNotNull { (service, repository) ->
                    if (!prefsManager.hasQuotaSource(service)) return@mapNotNull null
                    service to repository
                }

                if (repos.isEmpty()) {
                    val snapshot = presentationMapper.map(
                        emptyList(),
                        generatedAt = Instant.now()
                    )
                    quotaResetMonitor.consumeIfCurrent(sourceRevision) { publishSnapshot(snapshot) }
                    return@launch
                }

                val deferreds = repos.map { (service, repo) ->
                    async { service to repo.fetchQuota() }
                }

                val results = deferreds.map { it.await() }
                // Desktop discovery acquires desktop -> source. Capture before the
                // source publication lock; consumeIfCurrent validates the batch revision.
                val desktopSpend = repositoryRegistry.desktopSpend()

                quotaResetMonitor.consumeIfCurrent(sourceRevision) {

                    val successfulQuotas = mutableListOf<com.codexbar.android.core.domain.model.QuotaInfo>()
                    val errors = mutableMapOf<AiService, AppError>()

                    for ((service, result) in results) {
                        connectionHealthStore.record(service, result)
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
                    quotaResetMonitor.observeCurrent(successfulQuotas)
                    quotaHistoryStore.record(successfulQuotas)
                    val paceByMetricKey = quotaHistoryStore.paceFor(successfulQuotas, now)
                    val historyByMetricKey = quotaHistoryStore.historyFor(successfulQuotas)
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
                        paceByMetricKey = paceByMetricKey,
                        historyByMetricKey = historyByMetricKey,
                        desktopServices = prefsManager.desktopServices(),
                        desktopSpendByService = desktopSpend
                    )
                    publishSnapshot(snapshot)
                }
            } finally {
                _isRefreshing.value = false
                if (refreshPending) refresh()
            }
        }
    }

    private suspend fun publishSnapshot(baseSnapshot: QuotaPresentationSnapshot) {
        val snapshot = baseSnapshot.copy(services = baseSnapshot.services.map {
            it.copy(resetHistory = if (baseSnapshot.privacy.redactSensitiveValues) emptyList() else
                quotaResetHistoryStore.history.value[it.service].orEmpty())
        })
        _uiState.value = DashboardUiState.Content(snapshot, prefsManager.acquisitionSettings().mode)
        notificationService.publishSnapshot(
            snapshot = snapshot,
            monitoringSession = monitoringSessionStore.activeSession()
        )

        // The dashboard already owns the freshest provider response. Persist and render that
        // exact snapshot instead of waiting for a second WorkManager network request.
        try {
            snapshot.services.forEach(widgetPrefsManager::cachePresentation)
            WidgetUpdater.updateAll(appContext)
        } catch (error: Exception) {
            // Widget rendering must not turn a successful dashboard refresh into an app error.
            Log.e(TAG, "Widget render failed after dashboard refresh", error)
        }
    }

    companion object {
        private const val TAG = "CodexBarDashboard"
    }
}
