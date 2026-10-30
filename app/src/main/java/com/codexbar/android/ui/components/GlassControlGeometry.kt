package com.codexbar.android.ui.components

/** Logical tab position, with the lens centred under the finger and clamped to the end tabs. */
internal fun glassTabPosition(x: Float, width: Float, count: Int, rtl: Boolean): Float {
    if (count <= 1 || width <= 0f) return 0f
    val last = (count - 1).toFloat()
    val visual = (x / width * count - 0.5f).coerceIn(0f, last)
    return if (rtl) last - visual else visual
}

/** Relative movement avoids jumping when a switch is grabbed away from its thumb's centre. */
internal fun glassSwitchPosition(from: Float, delta: Float, travel: Float, rtl: Boolean): Float {
    if (travel <= 0f) return from.coerceIn(0f, 1f)
    return (from + delta / travel * if (rtl) -1f else 1f).coerceIn(0f, 1f)
}
