package com.codexbar.android.ui.components

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** Share one gate across controls so overlapping gesture events cannot produce a buzz. */
internal class AppHapticFeedback(
    private val platform: HapticFeedback,
    private val enabled: () -> Boolean,
    private val clockMillis: () -> Long = SystemClock::uptimeMillis
) : HapticFeedback {
    private var lastPulse: Long? = null
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        if (!enabled()) return
        val now = clockMillis()
        if (lastPulse?.let { now - it < 80L } == true) return
        lastPulse = now
        platform.performHapticFeedback(hapticFeedbackType)
    }
}

@Composable
internal fun ProvideAppHaptics(enabled: Boolean, content: @Composable () -> Unit) {
    val platform = LocalHapticFeedback.current
    val currentEnabled = rememberUpdatedState(enabled)
    val feedback = remember(platform) { AppHapticFeedback(platform, { currentEnabled.value }) }
    CompositionLocalProvider(LocalHapticFeedback provides feedback, content = content)
}

/** One light pulse when the lens lifts; holding, dragging and release do not repeat it. */
@Composable
internal fun GlassLiftHaptics(lifted: Boolean) {
    val feedback = rememberUpdatedState(LocalHapticFeedback.current)
    LaunchedEffect(lifted) {
        if (lifted) feedback.value.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }
}
