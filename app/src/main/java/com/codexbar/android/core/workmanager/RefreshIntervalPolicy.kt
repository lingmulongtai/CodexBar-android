package com.codexbar.android.core.workmanager

internal object RefreshIntervalPolicy {
    const val MANUAL_MINUTES = 0L
    const val MIN_MINUTES = 15L
    const val MAX_MINUTES = 120L
    const val STEP_MINUTES = 5L
    const val DEFAULT_MINUTES = 30L

    fun normalize(minutes: Long): Long {
        if (minutes <= MANUAL_MINUTES) return MANUAL_MINUTES
        val bounded = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
        return (((bounded + STEP_MINUTES / 2) / STEP_MINUTES) * STEP_MINUTES)
            .coerceIn(MIN_MINUTES, MAX_MINUTES)
    }
}
