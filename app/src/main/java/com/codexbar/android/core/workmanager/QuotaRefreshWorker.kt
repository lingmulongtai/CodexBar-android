package com.codexbar.android.core.workmanager

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.repository.QuotaRepository
import com.codexbar.android.core.notification.QuotaNotificationService
import com.codexbar.android.core.presentation.PrivacyPresentation
import com.codexbar.android.core.presentation.QuotaPresentationMapper
import com.codexbar.android.core.presentation.RefreshSourcePresentation
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.tile.QuotaTileService
import com.codexbar.android.core.widget.QuotaGlanceWidget
import com.codexbar.android.core.widget.QuotaWidgetReceiver
import com.codexbar.android.core.widget.WidgetPrefsManager
import com.codexbar.android.di.ClaudeRepository
import com.codexbar.android.di.CodexRepository
import com.codexbar.android.di.CopilotRepository
import com.codexbar.android.di.GeminiRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Instant

@HiltWorker
class QuotaRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    @ClaudeRepository private val claudeRepository: QuotaRepository,
    @CodexRepository private val codexRepository: QuotaRepository,
    @GeminiRepository private val geminiRepository: QuotaRepository,
    @CopilotRepository private val copilotRepository: QuotaRepository,
    private val prefsManager: EncryptedPrefsManager,
    private val notificationService: QuotaNotificationService,
    private val widgetPrefsManager: WidgetPrefsManager
) : CoroutineWorker(context, workerParams) {

    private val presentationMapper = QuotaPresentationMapper()

    override suspend fun doWork(): Result {
        val repos = buildList {
            if (prefsManager.loadCredential(AiService.CLAUDE) != null) add(AiService.CLAUDE to claudeRepository)
            if (prefsManager.loadCredential(AiService.CODEX) != null) add(AiService.CODEX to codexRepository)
            if (prefsManager.loadCredential(AiService.GEMINI) != null) add(AiService.GEMINI to geminiRepository)
            if (prefsManager.loadCredential(AiService.COPILOT) != null) add(AiService.COPILOT to copilotRepository)
        }

        if (repos.isEmpty()) return Result.success()

        return try {
            val quotas = coroutineScope {
                repos.map { (_, repo) ->
                    async { repo.fetchQuota() }
                }.awaitAll()
            }

            val successfulQuotas = quotas.mapNotNull { result ->
                when (result) {
                    is com.codexbar.android.core.domain.model.Result.Success -> result.value
                    is com.codexbar.android.core.domain.model.Result.Failure -> null
                }
            }

            if (successfulQuotas.isNotEmpty()) {
                // Cache quota data for widgets
                cacheQuotaData(successfulQuotas)

                if (prefsManager.isNotificationsEnabled()) {
                    val privacySettings = prefsManager.getPrivacySettings()
                    val snapshot = presentationMapper.map(
                        quotas = successfulQuotas,
                        generatedAt = Instant.now(),
                        privacy = PrivacyPresentation(
                            redactSensitiveValues = privacySettings.notificationRedactionEnabled,
                            lockScreenRedacted = privacySettings.lockScreenRedactionEnabled,
                            widgetRedacted = privacySettings.widgetRedactionEnabled
                        ),
                        source = RefreshSourcePresentation.Trigger(
                            inputData.getString(WorkManagerInitializer.KEY_REFRESH_SOURCE) ?: "periodic"
                        )
                    )
                    notificationService.showQuotaNotification(snapshot)
                    checkForResets(successfulQuotas)
                }

                // Update all widgets
                QuotaGlanceWidget().updateAll(applicationContext)
            }

            // Request tile update
            TileService.requestListeningState(
                applicationContext,
                ComponentName(applicationContext, QuotaTileService::class.java)
            )

            if (successfulQuotas.isEmpty()) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun cacheQuotaData(quotas: List<QuotaInfo>) {
        for (quota in quotas) {
            val windows = quota.windows.map { window ->
                Triple(
                    window.label,
                    window.utilization,
                    window.resetsAt?.epochSecond
                )
            }
            widgetPrefsManager.cacheAllQuotaData(quota.service, windows)
            widgetPrefsManager.cacheTier(quota.service, quota.tier)
        }
    }

    private suspend fun checkForResets(quotas: List<QuotaInfo>) {
        val now = Instant.now()
        for (quota in quotas) {
            val previousResetTimes = prefsManager.loadResetTimes(quota.service)

            // Detect resets: previous resetsAt was in the future, now it's in the past
            for (window in quota.windows) {
                val previousResetAt = previousResetTimes[window.label] ?: continue
                if (previousResetAt.isBefore(now) && window.resetsAt != null && window.resetsAt.isAfter(now)) {
                    notificationService.showResetNotification(quota.service, window.label)
                }
            }

            // Save current reset times for next comparison
            prefsManager.saveResetTimes(
                quota.service,
                quota.windows.map { it.label to it.resetsAt }
            )
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = android.app.Notification.Builder(applicationContext, QuotaNotificationService.CHANNEL_ID)
            .setContentTitle("Refreshing quota data...")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .build()

        return ForegroundInfo(QuotaNotificationService.NOTIFICATION_ID + 1, notification)
    }
}
