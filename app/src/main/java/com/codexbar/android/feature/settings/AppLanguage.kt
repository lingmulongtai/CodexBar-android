package com.codexbar.android.feature.settings

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.codexbar.android.R

enum class AppLanguage(
    val languageTag: String?,
    @StringRes val labelRes: Int
) {
    SYSTEM(null, R.string.language_system),
    ENGLISH("en", R.string.language_english),
    JAPANESE("ja", R.string.language_japanese);

    fun apply() {
        val locales = languageTag?.let(LocaleListCompat::forLanguageTags)
            ?: LocaleListCompat.getEmptyLocaleList()
        AppCompatDelegate.setApplicationLocales(locales)
    }

    companion object {
        fun current(): AppLanguage {
            val languageTag = AppCompatDelegate.getApplicationLocales()
                .get(0)
                ?.toLanguageTag()
            return fromLanguageTag(languageTag)
        }

        internal fun fromLanguageTag(languageTag: String?): AppLanguage {
            val language = languageTag
                ?.substringBefore('-')
                ?.lowercase()
            return entries.firstOrNull { it.languageTag == language } ?: SYSTEM
        }
    }
}
