package com.codexbar.android.core.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.appwidget.AppWidgetProvider
import android.os.Bundle
import android.util.Log
import com.codexbar.android.core.workmanager.WorkManagerInitializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class QuotaWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        render(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        render(context, intArrayOf(id))
    }

    private fun render(context: Context, ids: IntArray) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Backup is local-only; first display does not depend on WorkManager.
                ids.forEach {
                    runCatching { WorkManagerInitializer.enqueueWidgetRender(appContext, it) }
                        .onFailure { Log.w("CodexBarWidget", "Could not schedule render backup", it) }
                }
                withTimeoutOrNull(8_000L) { ids.forEach { WidgetUpdater.update(appContext, it) } }
            } catch (error: Exception) {
                Log.e("CodexBarWidget", "Widget broadcast render failed", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val widgetPrefs = WidgetPrefsManager(context)
        for (id in appWidgetIds) {
            widgetPrefs.deleteWidgetConfig(id)
        }
    }
}
