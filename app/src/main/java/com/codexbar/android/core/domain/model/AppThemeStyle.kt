package com.codexbar.android.core.domain.model

enum class AppThemeStyle {
    MATERIAL_3,
    LIQUID_GLASS,
    WINUI_3,
    AURORA;

    companion object {
        fun fromStoredValue(value: String?): AppThemeStyle {
            return entries.firstOrNull { it.name == value } ?: MATERIAL_3
        }
    }
}
