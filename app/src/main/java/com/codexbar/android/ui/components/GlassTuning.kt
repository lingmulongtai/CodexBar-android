package com.codexbar.android.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.codexbar.android.core.domain.model.DeveloperSettings
import com.codexbar.android.core.domain.model.GlassTuning

val LocalDeveloperSettings = staticCompositionLocalOf { DeveloperSettings() }

internal data class GlassMaterial(val frost: Float, val bend: Float, val dispersion: Float,
    val rim: Float, val shine: Float, val saturation: Float, val tint: Float, val reach: Float)

internal fun GlassTuning.resolve(base: GlassMaterial): GlassMaterial {
    val safe = normalized()
    return GlassMaterial(safe.frost ?: base.frost, base.bend * (safe.bend ?: 1f), safe.dispersion ?: base.dispersion,
        safe.rim ?: base.rim, safe.shine ?: base.shine, safe.saturation ?: base.saturation,
        safe.tint ?: base.tint, base.reach * (safe.reach ?: 1f))
}
