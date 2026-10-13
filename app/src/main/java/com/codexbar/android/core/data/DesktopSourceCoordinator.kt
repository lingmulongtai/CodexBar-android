package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.AcquisitionSettings
import com.codexbar.android.core.domain.model.DesktopBridgeCredential
import com.codexbar.android.core.monitoring.MonitoringSessionStore
import com.codexbar.android.core.notification.QuotaNotificationService
import com.codexbar.android.core.security.ConnectionHealthStore
import com.codexbar.android.core.widget.WidgetPrefsManager
import javax.inject.Inject
import javax.inject.Singleton

/** UI changes and discovered providers share the same source transaction. */
@Singleton
class DesktopSourceCoordinator @Inject constructor(
    private val monitor: QuotaResetMonitor,
    private val health: ConnectionHealthStore,
    private val widgets: WidgetPrefsManager,
    private val sessions: MonitoringSessionStore,
    private val notifications: QuotaNotificationService
) {
    suspend fun update(credential: DesktopBridgeCredential?, settings: AcquisitionSettings,
        valid: () -> Boolean = { true }): Boolean = monitor.updateDesktopSettings(credential, settings, valid) { affected ->
        affected.forEach { service -> health.clear(service); widgets.deleteServiceCache(service) }
        notifications.discardServices(affected, sessions.activeSession())
    }
}
