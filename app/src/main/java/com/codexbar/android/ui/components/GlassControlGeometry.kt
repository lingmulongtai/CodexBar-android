package com.codexbar.android.ui.components

/** Logical tab position, with the lens centred under the finger and clamped to the end tabs. */
internal fun glassTabPosition(x: Float, width: Float, count: Int, rtl: Boolean): Float {
    if (count <= 1 || width <= 0f) return 0f
    val last = (count - 1).toFloat()
    val visual = (x / width * count - 0.5f).coerceIn(0f, last)
    return if (rtl) last - visual else visual
}

/** Preserve where the finger grabbed a tab instead of snapping the lens to its center. */
internal fun glassTabDragPosition(anchor: Float, delta: Float, slotWidth: Float, count: Int, rtl: Boolean): Float {
    if (count <= 1 || slotWidth <= 0f) return 0f
    return (anchor + delta / slotWidth * if (rtl) -1f else 1f).coerceIn(0f, (count - 1).toFloat())
}

/** Relative movement avoids jumping when a switch is grabbed away from its thumb's centre. */
internal fun glassSwitchPosition(from: Float, delta: Float, travel: Float, rtl: Boolean): Float {
    if (travel <= 0f) return from.coerceIn(0f, 1f)
    return (from + delta / travel * if (rtl) -1f else 1f).coerceIn(0f, 1f)
}

/** Resolve the reported value without snapping the thumb while a finger is moving. */
internal fun glassSliderValue(raw: Float, range: ClosedFloatingPointRange<Float>, steps: Int): Float {
    val span = range.endInclusive - range.start
    if (span <= 0f) return range.start
    val bounded = raw.coerceIn(range.start, range.endInclusive)
    if (steps <= 0) return bounded
    val intervals = steps + 1
    val index = kotlin.math.round((bounded - range.start) / span * intervals)
    return range.start + span * index / intervals
}
