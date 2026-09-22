package com.codexbar.android.core.widget

/** Keeps Glance RemoteViews comfortably below launcher and Binder size limits. */
internal object WidgetRenderPolicy {
    fun isCompact(widthDp: Int, heightDp: Int): Boolean = heightDp < 180 || widthDp < 240

    // Compact rows include their progress track; reserve 8dp for the container's vertical padding.
    fun compactServices(heightDp: Int): Int = ((heightDp - 8) / 24).coerceIn(1, 3)

    fun maxServices(heightDp: Int): Int = if (heightDp >= 360) 2 else 1

    fun maxRows(heightDp: Int, configuredRows: Int): Int {
        val services = maxServices(heightDp)
        val perService = (heightDp - 32 - (services - 1) * 16) / services
        // Header/freshness consume 48dp; a metric with reset/pace text needs up to 62dp.
        val sizeLimit = ((perService - 48) / 62).coerceIn(1, 4)
        return configuredRows.coerceIn(1, sizeLimit)
    }
}
