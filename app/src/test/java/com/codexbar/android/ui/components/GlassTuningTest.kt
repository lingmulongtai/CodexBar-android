package com.codexbar.android.ui.components

import com.codexbar.android.core.domain.model.GlassTuning
import org.junit.Assert.*
import org.junit.Test

class GlassTuningTest {
    private val header = GlassMaterial(0f, .45f, .4f, .13f, 0f, 1f, 0f, 4f)
    @Test fun `inherited material preserves every approved default including header bend reach`() {
        assertEquals(header, GlassTuning().resolve(header))
        assertEquals(1.8f, header.bend * header.reach, .0001f)
    }
    @Test fun `tuning preserves flat resting surfaces and scales moving lenses`() {
        val tuning = GlassTuning(bend = 2f, reach = 1.5f, frost = 24f, tint = .3f)
        assertEquals(0f, tuning.resolve(header.copy(bend = 0f)).bend, 0f)
        assertEquals(.9f, tuning.resolve(header).bend, .0001f)
        assertEquals(6f, tuning.resolve(header).reach, 0f)
        assertEquals(24f, tuning.resolve(header).frost, 0f)
        assertEquals(.3f, tuning.resolve(header).tint, 0f)
    }
}
