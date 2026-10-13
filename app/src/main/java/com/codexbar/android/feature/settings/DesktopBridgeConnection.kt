package com.codexbar.android.feature.settings

import android.content.Context
import com.codexbar.android.R
import com.codexbar.android.core.data.DesktopQuotaSource
import com.codexbar.android.core.data.QuotaHistoryStore
import com.codexbar.android.core.data.QuotaResetMonitor
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.DesktopBridgeCredential
import com.codexbar.android.core.monitoring.MonitoringSessionStore
import com.codexbar.android.core.network.desktop.DesktopBridgeClient
import com.codexbar.android.core.network.desktop.DesktopBridgeAuthenticationException
import com.codexbar.android.core.network.desktop.DesktopBridgeProtocolException
import com.codexbar.android.core.network.desktop.DesktopBridgePairing
import com.codexbar.android.core.notification.QuotaNotificationService
import com.codexbar.android.core.security.ConnectionHealthStore
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.widget.WidgetPrefsManager
import com.codexbar.android.core.widget.WidgetUpdater
import com.codexbar.android.core.workmanager.WorkManagerInitializer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DesktopConnectionState(
    val pairingCode: String = "", val connected: Boolean = false, val busy: Boolean = false,
    val result: ValidationResult? = null, val services: Set<AiService> = emptySet(),
    val availableServices: Set<AiService> = emptySet()
)

/** Owns the pairing lifecycle; native credentials are never replaced here. */
internal class DesktopBridgeConnection(
    private val scope: CoroutineScope,
    private val client: DesktopBridgeClient,
    private val prefs: EncryptedPrefsManager,
    private val monitor: QuotaResetMonitor,
    private val health: ConnectionHealthStore,
    private val history: QuotaHistoryStore,
    private val widgets: WidgetPrefsManager,
    private val sessions: MonitoringSessionStore,
    private val notifications: QuotaNotificationService,
    private val context: Context
) {
    private val mutableState = MutableStateFlow(DesktopConnectionState())
    val state = mutableState.asStateFlow()
    @Volatile private var generation = 0L
    private var job: Job? = null

    fun initialize() {
        mutableState.update { it.copy(connected = prefs.desktopBridgeCredential() != null, services = prefs.desktopServices(), availableServices = prefs.desktopServices()) }
        if (prefs.desktopBridgeCredential() != null) refreshProviders()
    }

    fun updateCode(code: String) { mutableState.update { it.copy(pairingCode = code, result = null) } }

    fun connect() {
        val code = state.value.pairingCode
        operation {
            val credential = DesktopBridgePairing.parse(code)
            val snapshot = client.fetchSnapshot(credential)
            val selected = snapshot.providers.filter { it.status == "ready" }.mapNotNull { DesktopQuotaSource.serviceFor(it.id) }.toSet()
            if (apply(credential, selected, it)) mutableState.update { current -> current.copy(pairingCode = "", availableServices = snapshot.providers.mapNotNull { provider -> DesktopQuotaSource.serviceFor(provider.id) }.toSet(), result = ValidationResult.Success) }
        }
    }

    fun refreshProviders() = operation { expected ->
        val credential = prefs.desktopBridgeCredential() ?: return@operation
        val snapshot = client.fetchSnapshot(credential)
        if (expected == generation) mutableState.update { it.copy(availableServices = snapshot.providers.mapNotNull { provider -> DesktopQuotaSource.serviceFor(provider.id) }.toSet() + prefs.desktopServices()) }
    }

    fun select(service: AiService, enabled: Boolean) = operation {
        val credential = prefs.desktopBridgeCredential() ?: return@operation
        val selected = prefs.desktopServices().let { services -> if (enabled) services + service else services - service }
        apply(credential, selected, it)
    }

    fun disconnect() {
        cancelPending()
        operation {
            if (apply(null, emptySet(), it)) mutableState.update { current -> current.copy(pairingCode = "", availableServices = emptySet(), result = null) }
        }
    }

    fun cancelPending() {
        generation++
        job?.cancel()
        mutableState.update { it.copy(busy = false) }
    }

    private fun operation(action: suspend (Long) -> Unit) {
        if (state.value.busy) return
        val expected = ++generation
        mutableState.update { it.copy(busy = true, result = null) }
        job = scope.launch {
            try { action(expected)
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                val message = when (error) {
                    is IllegalArgumentException -> R.string.desktop_pairing_invalid
                    is DesktopBridgeAuthenticationException -> R.string.desktop_authentication_failed
                    is DesktopBridgeProtocolException -> R.string.desktop_response_invalid
                    else -> R.string.desktop_connection_failed
                }
                if (expected == generation) mutableState.update { it.copy(result = ValidationResult.Failure(context.getString(message))) }
            } finally {
                if (expected == generation) mutableState.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun apply(credential: DesktopBridgeCredential?, services: Set<AiService>, expected: Long): Boolean {
        val applied = monitor.updateDesktopSource(credential, services, valid = { expected == generation }) { affected ->
            affected.forEach { service -> health.clear(service); widgets.deleteServiceCache(service) }
            notifications.discardServices(affected, sessions.activeSession())
        }
        if (!applied || expected != generation) return false
        mutableState.update { it.copy(connected = credential != null, services = services) }
        WorkManagerInitializer.enqueueManualQuotaRefresh(context, source = "windows_bridge_changed")
        try { WidgetUpdater.updateAll(context) } catch (error: CancellationException) { throw error } catch (_: Exception) { /* Saved pairing and refresh remain valid. */ }
        return true
    }
}
