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
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.codexbar.android.MainActivity
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.monitoring.MonitoringActionReceiver
import com.codexbar.android.core.monitoring.MonitoringSession
import com.codexbar.android.core.presentation.QuotaPresentationSnapshot
import com.codexbar.android.core.presentation.QuotaSeverity
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
        const val LIVE_CHANNEL_ID = "quota_live_monitor"
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
            localizedString(R.string.notification_channel_quota_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = localizedString(R.string.notification_channel_quota_description)
            setShowBadge(false)
        }

        val resetChannel = NotificationChannel(
            RESET_CHANNEL_ID,
            localizedString(R.string.notification_channel_reset_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = localizedString(R.string.notification_channel_reset_description)
        }

        val liveChannel = NotificationChannel(
            LIVE_CHANNEL_ID,
            localizedString(R.string.notification_channel_live_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = localizedString(R.string.notification_channel_live_description)
            setShowBadge(false)
        }

        manager.createNotificationChannels(listOf(monitorChannel, liveChannel, resetChannel))
    }

    /**
     * Publishes one immutable presentation snapshot to every enabled notification surface.
     * The persistent status and the user-started Live Update are intentionally independent.
     */
    fun publishSnapshot(
        snapshot: QuotaPresentationSnapshot,
        monitoringSession: MonitoringSession?
    ) {
        if (prefsManager.isPersistentNotificationEnabled()) {
            showQuotaNotification(snapshot)
        } else {
            cancelQuotaNotification()
        }

        if (monitoringSession != null) {
            showMonitoringNotification(snapshot, monitoringSession)
        } else {
            cancelMonitoringNotification()
        }
    }

    fun showQuotaNotification(snapshot: QuotaPresentationSnapshot) {
        val privacySettings = prefsManager.getPrivacySettings()
        val elapsed = snapshot.services.firstOrNull()?.freshness?.ageLabel
            ?: localizedString(R.string.notification_just_now)
        val title = localizedString(R.string.notification_quota_status_title)
        val hiddenText = localizedString(R.string.notification_quota_hidden)
        val updatedText = localizedString(R.string.notification_updated, elapsed)
        val mostUsedProgress = snapshot.services
            .mapNotNull { it.primaryMetric?.usedPercent }
            .maxOrNull()
            ?: 0
        val summary = if (privacySettings.notificationRedactionEnabled) {
            hiddenText
        } else {
            snapshot.services.firstOrNull()?.let { service ->
                localizedString(
                    R.string.notification_service_summary,
                    service.service.displayName,
                    formatRemaining(service)
                )
            } ?: localizedString(R.string.notification_no_quota_data)
        }
        val style = if (privacySettings.notificationRedactionEnabled) {
            NotificationCompat.InboxStyle()
                .setBigContentTitle(title)
                .addLine(hiddenText)
                .setSummaryText(updatedText)
        } else {
            NotificationCompat.InboxStyle()
                .setBigContentTitle(title)
                .setSummaryText(updatedText)
                .also { inbox ->
                    snapshot.services.take(5).forEach { service ->
                        inbox.addLine(
                            localizedString(
                                R.string.notification_service_summary,
                                service.service.displayName,
                                formatRemaining(service)
                            )
                        )
                    }
                }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quota)
            .setContentTitle(title)
            .setContentText(summary)
            .setSubText(updatedText)
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
                redactedTitle = title,
                redactedText = hiddenText
            )
            .addAction(
                R.drawable.ic_refresh,
                localizedString(R.string.action_refresh),
                refreshPendingIntent()
            )
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
            primaryMetric?.usedPercent?.coerceIn(0, 100) ?: 0
        }
        val remaining = session.remainingMinutes()
        val remainingDuration = localizedString(R.string.duration_minutes, remaining)
        val title = localizedString(R.string.notification_monitoring_title)
        val hiddenText = localizedString(R.string.notification_quota_hidden)
        val text = when {
            privacySettings.notificationRedactionEnabled -> {
                localizedString(R.string.notification_monitoring_hidden, remainingDuration)
            }
            primaryService == null || primaryMetric == null -> {
                localizedString(R.string.notification_monitoring_waiting, remainingDuration)
            }
            else -> {
                localizedString(
                    R.string.notification_service_summary,
                    primaryService.service.displayName,
                    formatRemaining(primaryService)
                )
            }
        }
        val subText = localizedString(R.string.notification_live_session, remainingDuration)
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
            NotificationCompat.Builder(context, LIVE_CHANNEL_ID)
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
                .setWhen(session.endsAtMillis)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setShowWhen(true)
                .applyPrivacy(
                    privacySettings = privacySettings,
                    channelId = LIVE_CHANNEL_ID,
                    redactedTitle = title,
                    redactedText = hiddenText
                )
                .addAction(
                    R.drawable.ic_refresh,
                    localizedString(R.string.action_refresh),
                    refreshPendingIntent()
                )
                .addAction(
                    R.drawable.ic_quota,
                    localizedString(R.string.action_stop),
                    stopMonitoringPendingIntent()
                )
                .build()
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(MONITORING_NOTIFICATION_ID, notification)
    }

    fun showMonitoringPlaceholder(session: MonitoringSession) {
        showMonitoringNotification(
            snapshot = QuotaPresentationSnapshot(
                generatedAt = Instant.now(),
                services = emptyList()
            ),
            session = session
        )
    }

    fun cancelMonitoringNotification() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(MONITORING_NOTIFICATION_ID)
    }

    fun cancelQuotaNotification() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }

    fun cancelAllNotifications() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancelAll()
    }

    fun showResetNotification(service: AiService, windowLabel: String) {
        val privacySettings = prefsManager.getPrivacySettings()
        val contentTitle = if (privacySettings.notificationRedactionEnabled) {
            localizedString(R.string.notification_reset_title)
        } else {
            localizedString(R.string.notification_reset_service_title, service.displayName)
        }
        val contentText = if (privacySettings.notificationRedactionEnabled) {
            localizedString(R.string.notification_reset_generic)
        } else {
            localizedString(R.string.notification_reset_window, windowLabel)
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
                redactedTitle = localizedString(R.string.notification_reset_title),
                redactedText = localizedString(R.string.notification_quota_hidden)
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
        val progressColor = when (primaryService?.primaryMetric?.severity) {
            QuotaSeverity.Good -> Color.rgb(52, 168, 83)
            QuotaSeverity.Warning -> Color.rgb(251, 188, 4)
            QuotaSeverity.Critical -> Color.rgb(234, 67, 53)
            else -> primaryService?.service?.brandColor?.toInt() ?: Color.GRAY
        }
        val progressStyle = Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgress(progress)
            .addProgressSegment(
                Notification.ProgressStyle.Segment(100)
                    .setColor(progressColor)
            )
            .setProgressTrackerIcon(
                Icon.createWithResource(context, R.drawable.ic_quota)
            )

        val publicVersion = Notification.Builder(context, LIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quota)
            .setContentTitle(title)
            .setContentText(localizedString(R.string.notification_quota_hidden))
            .setShowWhen(false)
            .build()
        val criticalText = if (privacySettings.notificationRedactionEnabled) {
            localizedString(R.string.notification_live_short)
        } else {
            "$progress%"
        }

        return Notification.Builder(context, LIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quota)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(subText)
            .setStyle(progressStyle)
            .setColor(progressColor)
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
                    localizedString(R.string.action_refresh),
                    refreshPendingIntent()
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_quota),
                    localizedString(R.string.action_stop),
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
        val dashboardIntent = Intent(context, MainActivity::class.java).apply {
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
            return service.freshness.staleReason
                ?: localizedString(R.string.notification_waiting_for_data)
        }
        val resetText = primaryMetric.resetLabel?.let { " - $it" } ?: ""
        val paceText = primaryMetric.pace.label.takeIf { it.isNotBlank() }?.let { " - $it" } ?: ""
        return "${primaryMetric.remainingLabel} (${primaryMetric.label})$resetText$paceText"
    }

    private fun localizedString(@StringRes resourceId: Int, vararg formatArgs: Any): String {
        return ContextCompat.getContextForLanguage(context)
            .getString(resourceId, *formatArgs)
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
