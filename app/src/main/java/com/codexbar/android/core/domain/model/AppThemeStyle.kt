package com.codexbar.android.core.domain.model

enum class AppThemeStyle {
    MATERIAL_3,
    LIQUID_GLASS;

    companion object {
        // Legacy stored names are still readable, but Liquid Glass is the only app design.
        @Suppress("UNUSED_PARAMETER")
        fun fromStoredValue(value: String?): AppThemeStyle = LIQUID_GLASS
    }
}
