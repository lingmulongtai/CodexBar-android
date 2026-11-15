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
    @Test fun `containers use the tuned default when no container settings are stored`() {
        val expected = GlassTuning(frost = 8f, bend = .7f, dispersion = .3f, shine = .15f, saturation = 1.4f, tint = .2f, reach = 1f)
        assertEquals(expected, DeveloperSettings().tuning(GlassSurfaceGroup.CONTAINERS))
        assertEquals(expected, Json.decodeFromString<DeveloperSettings>("{}").tuning(GlassSurfaceGroup.CONTAINERS))
        assertEquals(expected, Json.decodeFromString<DeveloperSettings>("""{"animationSpeed":2}""").containers)
    }
    @Test fun `saved settings retain independent groups and reset only the selected group`() {
        val saved = DeveloperSettings(animationSpeed = 2f, containers = GlassTuning(frost = 0f, tint = .3f), header = GlassTuning(rim = .5f))
        assertEquals(saved, Json.decodeFromString<DeveloperSettings>(Json.encodeToString(saved)).normalized())
        assertEquals(GlassTuning(), Json.decodeFromString<DeveloperSettings>("""{"containers":{}}""").containers)
        assertEquals(GlassTuning(), saved.tuning(GlassSurfaceGroup.CONTROLS))
        assertEquals(saved.copy(containers = DeveloperSettings().containers), saved.resetTuning(GlassSurfaceGroup.CONTAINERS))
        assertEquals(saved.copy(header = GlassTuning()), saved.resetTuning(GlassSurfaceGroup.HEADER))
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
