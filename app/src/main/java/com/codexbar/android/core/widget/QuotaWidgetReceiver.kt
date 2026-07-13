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
        WorkManagerInitializer.enqueueManualQuotaRefresh(
            context = context,
            source = "widget_update"
        )
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val widgetPrefs = WidgetPrefsManager(context)
        for (id in appWidgetIds) {
            widgetPrefs.deleteWidgetConfig(id)
        }
    }
}
