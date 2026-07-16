package com.codexbar.android.core.widget

/** Keeps Glance RemoteViews comfortably below launcher and Binder size limits. */
internal object WidgetRenderPolicy {
    fun maxServices(heightDp: Int): Int = when {
        heightDp < 110 -> 1
        heightDp < 180 -> 2
        else -> 3
    }

    fun maxRows(heightDp: Int, configuredRows: Int): Int {
        val sizeLimit = when {
            heightDp < 110 -> 1
            heightDp < 180 -> 2
            else -> 4
        }
        return configuredRows.coerceIn(1, sizeLimit)
    }
}
