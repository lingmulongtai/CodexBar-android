package com.codexbar.android.core.workmanager

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.codexbar.android.core.widget.QuotaGlanceWidget
import kotlinx.coroutines.CancellationException

/**
 * Re-renders a widget after the launcher has finished binding it.
 *
 * Some launchers can ignore the update sent while a configuration activity is still open.
 * This worker performs no provider I/O and is safe to run without a network connection.
 */
class WidgetRenderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val appWidgetId = inputData.getInt(KEY_APP_WIDGET_ID, INVALID_APP_WIDGET_ID)
        if (appWidgetId == INVALID_APP_WIDGET_ID) return Result.failure()

        return try {
            val glanceId = GlanceAppWidgetManager(applicationContext)
                .getGlanceIdBy(appWidgetId)
            QuotaGlanceWidget().update(applicationContext, glanceId)
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Widget render failed for id=$appWidgetId", error)
            if (runAttemptCount < MAX_RETRY_COUNT) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_APP_WIDGET_ID = "app_widget_id"
        const val INVALID_APP_WIDGET_ID = -1
        private const val MAX_RETRY_COUNT = 2
        private const val TAG = "CodexBarWidget"
    }
}
