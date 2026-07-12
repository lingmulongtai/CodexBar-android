package com.codexbar.android.core.network

import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RetryAfterTest {

    private val now: Instant = Instant.parse("2026-07-13T00:00:00Z")
    private val maxDelay: Duration = Duration.ofSeconds(60)

    @Test
    fun `missing malformed and negative values are rejected`() {
        assertNull(RetryAfter.parseDelayMillis(null, now, maxDelay))
        assertNull(RetryAfter.parseDelayMillis("", now, maxDelay))
        assertNull(RetryAfter.parseDelayMillis("not-a-date", now, maxDelay))
        assertNull(RetryAfter.parseDelayMillis("-1", now, maxDelay))
    }

    @Test
    fun `large delta seconds are capped before millisecond conversion`() {
        assertEquals(
            60_000L,
            RetryAfter.parseDelayMillis(Long.MAX_VALUE.toString(), now, maxDelay)
        )
    }

    @Test
    fun `overflowed delta seconds are rejected`() {
        assertNull(RetryAfter.parseDelayMillis("${Long.MAX_VALUE}0", now, maxDelay))
    }

    @Test
    fun `http date values are converted to capped future delays`() {
        val retryAt = ZonedDateTime.ofInstant(now.plusSeconds(30), ZoneOffset.UTC)
            .format(DateTimeFormatter.RFC_1123_DATE_TIME)
        val excessiveRetryAt = ZonedDateTime.ofInstant(now.plusSeconds(120), ZoneOffset.UTC)
            .format(DateTimeFormatter.RFC_1123_DATE_TIME)

        assertEquals(30_000L, RetryAfter.parseDelayMillis(retryAt, now, maxDelay))
        assertEquals(60_000L, RetryAfter.parseDelayMillis(excessiveRetryAt, now, maxDelay))
    }

    @Test
    fun `past http dates are rejected`() {
        val retryAt = ZonedDateTime.ofInstant(now.minusSeconds(30), ZoneOffset.UTC)
            .format(DateTimeFormatter.RFC_1123_DATE_TIME)

        assertNull(RetryAfter.parseDelayMillis(retryAt, now, maxDelay))
    }
}
