package com.codexbar.android.core.domain.model

import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

object AppAnimationSpeed {
    const val DEFAULT = 1f
    const val MIN = .25f
    const val MAX = 3f
    fun normalize(value: Float): Float = if (value.isFinite()) value.coerceIn(MIN, MAX) else DEFAULT
    fun durationScale(speed: Float): Float = 1f / normalize(speed)
    fun durationMillis(base: Long, speed: Float): Long = (base * durationScale(speed)).roundToLong()
}

@Serializable
enum class GlassSurfaceGroup { CONTAINERS, HEADER, NAVIGATION, CONTROLS }

enum class GlassParameter(val min: Float, val max: Float) {
    FROST(0f, 40f), BEND(0f, 2f), DISPERSION(0f, 1f), RIM(0f, 1f),
    SHINE(0f, 1f), SATURATION(0f, 2f), TINT(0f, 1f), REACH(.25f, 2f)
}

/** Null keeps each component's original material, including its pressed/resting differences. */
@Serializable
data class GlassTuning(
    val frost: Float? = null, val bend: Float? = null, val dispersion: Float? = null,
    val rim: Float? = null, val shine: Float? = null, val saturation: Float? = null,
    val tint: Float? = null, val reach: Float? = null
) {
    fun value(parameter: GlassParameter): Float? = when (parameter) {
        GlassParameter.FROST -> frost; GlassParameter.BEND -> bend; GlassParameter.DISPERSION -> dispersion
        GlassParameter.RIM -> rim; GlassParameter.SHINE -> shine; GlassParameter.SATURATION -> saturation
        GlassParameter.TINT -> tint; GlassParameter.REACH -> reach
    }
    fun withValue(parameter: GlassParameter, value: Float?): GlassTuning {
        val safe = value?.takeIf { it.isFinite() }?.coerceIn(parameter.min, parameter.max)
        return when (parameter) {
            GlassParameter.FROST -> copy(frost = safe); GlassParameter.BEND -> copy(bend = safe)
            GlassParameter.DISPERSION -> copy(dispersion = safe); GlassParameter.RIM -> copy(rim = safe)
            GlassParameter.SHINE -> copy(shine = safe); GlassParameter.SATURATION -> copy(saturation = safe)
            GlassParameter.TINT -> copy(tint = safe); GlassParameter.REACH -> copy(reach = safe)
        }
    }
    fun normalized(): GlassTuning = GlassParameter.entries.fold(this) { result, key -> result.withValue(key, value(key)) }
}

@Serializable
data class DeveloperSettings(
    val animationSpeed: Float = AppAnimationSpeed.DEFAULT,
    val containers: GlassTuning = GlassTuning(), val header: GlassTuning = GlassTuning(),
    val navigation: GlassTuning = GlassTuning(), val controls: GlassTuning = GlassTuning()
) {
    fun tuning(group: GlassSurfaceGroup): GlassTuning = when (group) {
        GlassSurfaceGroup.CONTAINERS -> containers; GlassSurfaceGroup.HEADER -> header
        GlassSurfaceGroup.NAVIGATION -> navigation; GlassSurfaceGroup.CONTROLS -> controls
    }
    fun withTuning(group: GlassSurfaceGroup, value: GlassTuning): DeveloperSettings = when (group) {
        GlassSurfaceGroup.CONTAINERS -> copy(containers = value.normalized())
        GlassSurfaceGroup.HEADER -> copy(header = value.normalized())
        GlassSurfaceGroup.NAVIGATION -> copy(navigation = value.normalized())
        GlassSurfaceGroup.CONTROLS -> copy(controls = value.normalized())
    }
    fun normalized(): DeveloperSettings = copy(animationSpeed = AppAnimationSpeed.normalize(animationSpeed),
        containers = containers.normalized(), header = header.normalized(), navigation = navigation.normalized(), controls = controls.normalized())
}
