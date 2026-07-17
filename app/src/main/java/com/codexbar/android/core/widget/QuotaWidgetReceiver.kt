package com.codexbar.android.core.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.codexbar.android.core.workmanager.WorkManagerInitializer

class QuotaWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = QuotaGlanceWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // The parent performs the normal Glance update. A delayed, local-only render covers
        // launchers that dispatch onUpdate before their host has completed binding RemoteViews.
        appWidgetIds.forEach { appWidgetId ->
            WorkManagerInitializer.enqueueWidgetRender(context, appWidgetId)
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
