package com.codexbar.android.ui.components

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import org.junit.Assert.assertEquals
import org.junit.Test

class AppHapticFeedbackTest {
    @Test fun `disabled feedback is silent and a fresh enable works immediately`() {
        val pulses = mutableListOf<HapticFeedbackType>()
        val platform = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { pulses += hapticFeedbackType }
        }
        var enabled = false
        val feedback = AppHapticFeedback(platform, { enabled }, { 0L })
        feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
        assertEquals(0, pulses.size)
        enabled = true
        feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
        assertEquals(listOf(HapticFeedbackType.SegmentTick), pulses)
        enabled = false
        feedback.performHapticFeedback(HapticFeedbackType.LongPress)
        assertEquals(1, pulses.size)
    }

    @Test fun `overlapping lens events coalesce without suppressing a later gesture`() {
        val pulses = mutableListOf<HapticFeedbackType>()
        val platform = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { pulses += hapticFeedbackType }
        }
        var time = 0L
        val feedback = AppHapticFeedback(platform, { true }, { time })
        feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
        time = 20
        feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
        time = 80
        feedback.performHapticFeedback(HapticFeedbackType.LongPress)
        assertEquals(listOf(HapticFeedbackType.SegmentTick, HapticFeedbackType.LongPress), pulses)
    }
}
