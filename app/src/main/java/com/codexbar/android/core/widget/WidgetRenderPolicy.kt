package com.codexbar.android.core.widget

/** Bound the native view count and keep complete rows inside very short launcher allocations. */
internal object WidgetRenderPolicy {
    fun rowCount(heightDp: Float, fontScale: Float = 1f): Int =
        ((heightDp - 8) / (18 * fontScale.coerceIn(0.8f, 1.2f))).toInt().coerceIn(1, 6)
}
