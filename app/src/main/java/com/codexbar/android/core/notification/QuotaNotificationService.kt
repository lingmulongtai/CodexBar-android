package com.codexbar.android.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.QuotaInfo
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
        const val RESET_NOTIFICATION_ID_BASE = 2000
        const val ACTION_REFRESH = "com.codexbar.android.ACTION_REFRESH"
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

    fun showQuotaNotification(quotas: List<QuotaInfo>) {
        val privacySettings = prefsManager.getPrivacySettings()
        val elapsed = formatElapsed(quotas.firstOrNull()?.fetchedAt)
        val mostUsedProgress = quotas
            .flatMap { it.windows }
            .maxOfOrNull { (it.utilization * 100).toInt().coerceIn(0, 100) }
            ?: 0
        val summary = if (privacySettings.notificationRedactionEnabled) {
            "Quota details hidden"
        } else {
            quotas.firstOrNull()?.let { quota ->
                "${quota.service.displayName}: ${formatRemaining(quota)}"
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
                    quotas.take(5).forEach { quota ->
                        inbox.addLine("${quota.service.displayName}: ${formatRemaining(quota)}")
                    }
                }
        }

        // Refresh action
        val refreshIntent = Intent(context, RefreshReceiver::class.java).apply {
            action = ACTION_REFRESH
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context, 0, refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dashboard tap intent
        val dashboardIntent = Intent().apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse("codexbar://dashboard")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val dashboardPendingIntent = PendingIntent.getActivity(
            context, 0, dashboardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quota)
            .setContentTitle("AI quota status")
            .setContentText(summary)
            .setSubText("Updated $elapsed")
            .setStyle(style)
            .setProgress(100, if (privacySettings.notificationRedactionEnabled) 0 else mostUsedProgress, false)
            .setContentIntent(dashboardPendingIntent)
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
            .addAction(R.drawable.ic_refresh, "Refresh", refreshPendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun showResetNotification(service: AiService, windowLabel: String) {
        val privacySettings = prefsManager.getPrivacySettings()
        val dashboardIntent = Intent().apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse("codexbar://dashboard")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val dashboardPendingIntent = PendingIntent.getActivity(
            context, 0, dashboardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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
            .setContentIntent(dashboardPendingIntent)
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

    private fun formatElapsed(fetchedAt: Instant?): String {
        if (fetchedAt == null) return "just now"
        val elapsed = Duration.between(fetchedAt, Instant.now())
        return when {
            elapsed.toMinutes() < 1 -> "just now"
            elapsed.toMinutes() < 60 -> "${elapsed.toMinutes()} min ago"
            else -> "${elapsed.toHours()}h ago"
        }
    }

    private fun formatRemaining(quota: QuotaInfo): String {
        val primaryWindow = quota.windows.maxByOrNull { it.utilization } ?: return "waiting for data"
        val remaining = ((1.0 - primaryWindow.utilization) * 100).toInt().coerceIn(0, 100)
        val resetText = primaryWindow.resetsAt?.let { resetAt ->
            val resetIn = formatDurationUntil(resetAt)
            if (resetIn.isNotEmpty()) " - resets in $resetIn" else ""
        } ?: ""
        return "$remaining% left (${primaryWindow.label})$resetText"
    }

    private fun formatDurationUntil(instant: Instant): String {
        val duration = Duration.between(Instant.now(), instant)
        if (duration.isNegative || duration.isZero) return ""
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60
        return when {
            hours >= 24 -> "${hours / 24}d ${hours % 24}h"
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
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
