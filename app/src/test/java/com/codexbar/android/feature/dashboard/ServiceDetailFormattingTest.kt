package com.codexbar.android.feature.dashboard

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ServiceDetailFormattingTest {

    @Test
    fun `detail timestamps use the selected locale instead of ISO text`() {
        val instant = Instant.parse("2026-07-14T04:00:00Z")

        val formatted = formatDetailInstant(
            instant = instant,
            locale = Locale.US,
            zoneId = ZoneId.of("UTC")
        )

        assertEquals("7/14/26, 4:00 AM", formatted.normalizeUnicodeSpaces())
        assertNotEquals(instant.toString(), formatted)
    }

    private fun String.normalizeUnicodeSpaces(): String =
        replace(Regex("\\p{Zs}+"), " ")
}
