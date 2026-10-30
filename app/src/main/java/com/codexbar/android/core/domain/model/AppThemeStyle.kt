package com.codexbar.android.core.domain.model

enum class AppThemeStyle {
    MATERIAL_3,
    LIQUID_GLASS;

    companion object {
        fun fromStoredValue(value: String?): AppThemeStyle {
            return when (value) {
                // Retired designs map to the closest remaining one instead of resetting choices.
                "WINUI_3" -> MATERIAL_3
                "AURORA", "INSTRUMENT" -> MATERIAL_3
                else -> entries.firstOrNull { it.name == value } ?: MATERIAL_3
            }
        }
    }
}
