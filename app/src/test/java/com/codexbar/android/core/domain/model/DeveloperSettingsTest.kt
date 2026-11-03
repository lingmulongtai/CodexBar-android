package com.codexbar.android.core.domain.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class DeveloperSettingsTest {
    @Test fun `speed means faster and invalid stored values recover to normal`() {
        assertEquals(1f, AppAnimationSpeed.durationScale(1f), 0f)
        assertEquals(.5f, AppAnimationSpeed.durationScale(2f), 0f)
        assertEquals(600L, AppAnimationSpeed.durationMillis(300, .5f))
        assertEquals(1f, AppAnimationSpeed.normalize(Float.NaN), 0f)
        assertEquals(1f, AppAnimationSpeed.normalize(Float.POSITIVE_INFINITY), 0f)
        assertEquals(.25f, AppAnimationSpeed.normalize(0f), 0f)
        assertEquals(3f, AppAnimationSpeed.normalize(10f), 0f)
    }
    @Test fun `saved settings retain independent groups and reset exactly to inherited defaults`() {
        val saved = DeveloperSettings(animationSpeed = 2f, containers = GlassTuning(frost = 0f, tint = .3f), header = GlassTuning(rim = .5f))
        assertEquals(saved, Json.decodeFromString<DeveloperSettings>(Json.encodeToString(saved)).normalized())
        assertEquals(GlassTuning(), saved.tuning(GlassSurfaceGroup.CONTROLS))
        assertEquals(GlassTuning(), saved.withTuning(GlassSurfaceGroup.CONTAINERS, GlassTuning()).containers)
        assertEquals(DeveloperSettings(), Json.decodeFromString<DeveloperSettings>("{}"))
    }
    @Test fun `every shader parameter is finite and bounded after normalization`() {
        GlassParameter.entries.forEach { key ->
            assertNull(GlassTuning().withValue(key, Float.NaN).value(key))
            assertNull(GlassTuning().withValue(key, Float.NEGATIVE_INFINITY).value(key))
            assertEquals(key.min, GlassTuning().withValue(key, -100f).value(key)!!, 0f)
            assertEquals(key.max, GlassTuning().withValue(key, 100f).value(key)!!, 0f)
        }
    }
}
