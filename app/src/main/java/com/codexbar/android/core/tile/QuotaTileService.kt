package com.codexbar.android.core.tile

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.widget.WidgetPrefsManager
import com.codexbar.android.core.workmanager.WorkManagerInitializer
import dagger.hilt.android.AndroidEntryPoint
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class QuotaTileService : TileService() {

    @Inject
    lateinit var prefsManager: EncryptedPrefsManager

    @Inject
    lateinit var widgetPrefsManager: WidgetPrefsManager

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (AiService.entries.any { prefsManager.hasCredential(it) }) {
            WorkManagerInitializer.enqueueManualQuotaRefresh(this, source = "tile")
            updateTile(subtitleOverride = "Refreshing...")
            return
        }

        val intent = Intent().apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse("codexbar://dashboard")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivityAndCollapse(intent)
    }

    private fun updateTile(subtitleOverride: String? = null) {
        val tile = qsTile ?: return

        val hasAnyCredential = AiService.entries.any { prefsManager.hasCredential(it) }

        if (!hasAnyCredential) {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "CodexBar"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Tap to set up"
            }
            tile.updateTile()
            return
        }

        tile.state = Tile.STATE_ACTIVE
        tile.label = "CodexBar"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitleOverride ?: buildSummarySubtitle()
        }
        tile.updateTile()
    }

    private fun buildSummarySubtitle(): String {
        val privacySettings = prefsManager.getPrivacySettings()
        if (privacySettings.widgetRedactionEnabled || privacySettings.notificationRedactionEnabled) {
            return "Quota hidden"
        }

        val services = AiService.entries.filter { prefsManager.hasCredential(it) }
        val cachedService = services
            .mapNotNull { service ->
                val labels = widgetPrefsManager.getCachedLabels(service)
                if (labels.isEmpty()) return@mapNotNull null
                val maxUtilization = widgetPrefsManager.getMaxCachedUtilization(service)
                val updatedAt = widgetPrefsManager.getCachedUpdatedAt(service)
                TileSnapshot(service, maxUtilization, updatedAt)
            }
            .maxByOrNull { it.utilization }

        if (cachedService == null) {
            return services.joinToString(" | ") { it.displayName }
        }

        val remaining = ((1f - cachedService.utilization) * 100).toInt().coerceIn(0, 100)
        val age = formatAge(cachedService.updatedAt)
        return "${cachedService.service.displayName}: $remaining% left$age"
    }

    private fun formatAge(updatedAtMillis: Long): String {
        if (updatedAtMillis <= 0) return ""
        val duration = Duration.between(Instant.ofEpochMilli(updatedAtMillis), Instant.now())
        if (duration.isNegative) return ""
        return when {
            duration.toMinutes() < 1 -> " - just now"
            duration.toMinutes() < 60 -> " - ${duration.toMinutes()}m ago"
            else -> " - ${duration.toHours()}h ago"
        }
    }

    private data class TileSnapshot(
        val service: AiService,
        val utilization: Float,
        val updatedAt: Long
    )
}
