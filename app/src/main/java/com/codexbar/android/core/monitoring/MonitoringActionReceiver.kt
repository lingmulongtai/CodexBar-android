package com.codexbar.android.core.monitoring

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.codexbar.android.core.notification.QuotaNotificationService
import com.codexbar.android.core.workmanager.WorkManagerInitializer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MonitoringActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var sessionStore: MonitoringSessionStore

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_STOP_MONITORING -> {
                sessionStore.stop()
                WorkManagerInitializer.stopMonitoringSession(context)
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(QuotaNotificationService.MONITORING_NOTIFICATION_ID)
            }
        }
    }

    companion object {
        const val ACTION_STOP_MONITORING = "com.codexbar.android.ACTION_STOP_MONITORING"
    }
}
