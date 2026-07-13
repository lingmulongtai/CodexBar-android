package com.codexbar.android.core.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.monitoring.MonitoringActionReceiver
import com.codexbar.android.core.monitoring.MonitoringSession
import com.codexbar.android.core.presentation.QuotaPresentationSnapshot
import com.codexbar.android.core.presentation.ServiceQuotaPresentation
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.security.PrivacySettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuotaNotificationService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefsManager: EncryptedPrefsManager
) {
    companion object {
        const val CHANNEL_ID = "quota_monitor"
        const val RESET_CHANNEL_ID = "quota_reset_alert"
        const val NOTIFICATION_ID = 1001
        const val MONITORING_NOTIFICATION_ID = 1002
        const val RESET_NOTIFICATION_ID_BASE = 2000
        const val ACTION_REFRESH = "com.codexbar.android.ACTION_REFRESH"
        private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val monitorChannel = NotificationChannel(
            CHANNEL_ID,
            "AI Quota Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows current AI service quota usage"
            setShowBadge(false)
        }

        val resetChannel = NotificationChannel(
            RESET_CHANNEL_ID,
            "Quota Reset Alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifies when AI service quotas have been reset"
        }

        manager.createNotificationChannels(listOf(monitorChannel, resetChannel))
    }

    fun showQuotaNotification(snapshot: QuotaPresentationSnapshot) {
        val privacySettings = prefsManager.getPrivacySettings()
        val elapsed = snapshot.services.firstOrNull()?.freshness?.ageLabel ?: "just now"
        val mostUsedProgress = snapshot.services
            .mapNotNull { it.primaryMetric?.usedPercent }
            .maxOrNull()
            ?: 0
        val summary = if (privacySettings.notificationRedactionEnabled) {
            "Quota details hidden"
        } else {
            snapshot.services.firstOrNull()?.let { service ->
                "${service.service.displayName}: ${formatRemaining(service)}"
            } ?: "No quota data"
        }
        val style = if (privacySettings.notificationRedactionEnabled) {
            NotificationCompat.InboxStyle()
                .setBigContentTitle("AI quota status")
                .addLine("Quota details hidden")
                .setSummaryText("Updated $elapsed")
        } else {
            NotificationCompat.InboxStyle()
                .setBigContentTitle("AI quota status")
                .setSummaryText("Updated $elapsed")
                .also { inbox ->
                    snapshot.services.take(5).forEach { service ->
                        inbox.addLine("${service.service.displayName}: ${formatRemaining(service)}")
                    }
                }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quota)
            .setContentTitle("AI quota status")
            .setContentText(summary)
            .setSubText("Updated $elapsed")
            .setStyle(style)
            .setProgress(100, if (privacySettings.notificationRedactionEnabled) 0 else mostUsedProgress, false)
            .setContentIntent(dashboardPendingIntent())
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .applyPrivacy(
                privacySettings = privacySettings,
                channelId = CHANNEL_ID,
                redactedTitle = "AI quota status",
                redactedText = "Quota details hidden"
            )
            .addAction(R.drawable.ic_refresh, "Refresh", refreshPendingIntent())
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun showMonitoringNotification(snapshot: QuotaPresentationSnapshot, session: MonitoringSession) {
        val privacySettings = prefsManager.getPrivacySettings()
        val primaryService = snapshot.services
            .maxByOrNull { service -> service.primaryMetric?.usedPercent ?: -1 }
        val primaryMetric = primaryService?.primaryMetric
        val progress = if (privacySettings.notificationRedactionEnabled) {
            0
        } else {
            primaryMetric?.barProgress?.times(100)?.toInt()?.coerceIn(0, 100) ?: 0
        }
        val remaining = session.remainingMinutes()
        val title = "Quota monitoring"
        val text = if (privacySettings.notificationRedactionEnabled || primaryService == null || primaryMetric == null) {
            "$remaining min left - quota details hidden"
        } else {
            "${primaryService.service.displayName}: ${formatRemaining(primaryService)}"
        }
        val subText = "Live session - $remaining min left"
        val notification = if (Build.VERSION.SDK_INT >= 36) {
            buildPlatformMonitoringNotification(
                title = title,
                text = text,
                subText = subText,
                progress = progress,
                primaryService = primaryService,
                privacySettings = privacySettings,
                endsAtMillis = session.endsAtMillis
            )
        } else {
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_quota)
                .setContentTitle(title)
                .setContentText(text)
                .setSubText(subText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setProgress(100, progress, primaryMetric == null)
                .setContentIntent(dashboardPendingIntent())
                .setOngoing(true)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .applyPrivacy(
                    privacySettings = privacySettings,
                    channelId = CHANNEL_ID,
                    redactedTitle = title,
                    redactedText = "Quota details hidden"
                )
                .addAction(R.drawable.ic_refresh, "Refresh", refreshPendingIntent())
                .addAction(R.drawable.ic_quota, "Stop", stopMonitoringPendingIntent())
                .build()
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(MONITORING_NOTIFICATION_ID, notification)
    }

    fun cancelMonitoringNotification() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(MONITORING_NOTIFICATION_ID)
    }

    fun cancelAllNotifications() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancelAll()
    }

    fun showResetNotification(service: AiService, windowLabel: String) {
        val privacySettings = prefsManager.getPrivacySettings()
        val contentTitle = if (privacySettings.notificationRedactionEnabled) {
            "Quota reset"
        } else {
            "${service.displayName} quota reset"
        }
        val contentText = if (privacySettings.notificationRedactionEnabled) {
            "A quota window has reset."
        } else {
            "$windowLabel window has been reset. Your quota is fully available."
        }

        val notification = NotificationCompat.Builder(context, RESET_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quota)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setContentIntent(dashboardPendingIntent())
            .setAutoCancel(true)
            .applyPrivacy(
                privacySettings = privacySettings,
                channelId = RESET_CHANNEL_ID,
                redactedTitle = "Quota reset",
                redactedText = "Quota details hidden"
            )
            .build()

        val notificationId = RESET_NOTIFICATION_ID_BASE + "${service.name}_$windowLabel".hashCode().and(0xFFFF)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }

    @RequiresApi(36)
    @SuppressLint("WrongConstant")
    private fun buildPlatformMonitoringNotification(
        title: String,
        text: String,
        subText: String,
        progress: Int,
        primaryService: ServiceQuotaPresentation?,
        privacySettings: PrivacySettings,
        endsAtMillis: Long
    ): Notification {
        val progressStyle = Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgress(progress)
            .addProgressSegment(
                Notification.ProgressStyle.Segment(100)
                    .setColor(primaryService?.service?.brandColor?.toInt() ?: Color.GRAY)
            )

        val publicVersion = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quota)
            .setContentTitle(title)
            .setContentText("Quota details hidden")
            .setShowWhen(false)
            .build()
        val criticalText = if (privacySettings.notificationRedactionEnabled) "Live" else "$progress%"

        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quota)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(subText)
            .setStyle(progressStyle)
            .setContentIntent(dashboardPendingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setWhen(endsAtMillis)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setShowWhen(true)
            .setShortCriticalText(criticalText)
            .addExtras(
                Bundle().apply {
                    putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
                }
            )
            .setPublicVersion(publicVersion.takeIf { privacySettings.lockScreenRedactionEnabled })
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_refresh),
                    "Refresh",
                    refreshPendingIntent()
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_quota),
                    "Stop",
                    stopMonitoringPendingIntent()
                ).build()
            )
            .build()
    }

    private fun refreshPendingIntent(): PendingIntent {
        val refreshIntent = Intent(context, RefreshReceiver::class.java).apply {
            action = ACTION_REFRESH
        }
        return PendingIntent.getBroadcast(
            context, 0, refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun stopMonitoringPendingIntent(): PendingIntent {
        val intent = Intent(context, MonitoringActionReceiver::class.java).apply {
            action = MonitoringActionReceiver.ACTION_STOP_MONITORING
        }
        return PendingIntent.getBroadcast(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun dashboardPendingIntent(): PendingIntent {
        val dashboardIntent = Intent().apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse("codexbar://dashboard")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return PendingIntent.getActivity(
            context, 0, dashboardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun formatRemaining(service: ServiceQuotaPresentation): String {
        val primaryMetric = service.primaryMetric
        if (primaryMetric == null) {
            return service.freshness.staleReason ?: "waiting for data"
        }
        val resetText = primaryMetric.resetLabel?.let { " - $it" } ?: ""
        val paceText = primaryMetric.pace.label.takeIf { it.isNotBlank() }?.let { " - $it" } ?: ""
        return "${primaryMetric.remainingLabel} (${primaryMetric.label})$resetText$paceText"
    }

    private fun NotificationCompat.Builder.applyPrivacy(
        privacySettings: PrivacySettings,
        channelId: String,
        redactedTitle: String,
        redactedText: String
    ): NotificationCompat.Builder {
        if (!privacySettings.lockScreenRedactionEnabled) {
            return setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        }

        val publicVersion = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_quota)
            .setContentTitle(redactedTitle)
            .setContentText(redactedText)
            .setShowWhen(false)
            .build()

        return setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
    }
}
