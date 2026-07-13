package com.codexbar.android.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun `language tags map to supported app choices`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTag(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTag(""))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTag("en-US"))
        assertEquals(AppLanguage.JAPANESE, AppLanguage.fromLanguageTag("ja-JP"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTag("fr"))
    }
}
