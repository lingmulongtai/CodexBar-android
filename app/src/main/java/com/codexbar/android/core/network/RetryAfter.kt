package com.codexbar.android.core.network

import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object RetryAfter {
    fun parseRetryAt(
        value: String?,
        now: Instant = Instant.now(),
        maxDelay: Duration = Duration.ofSeconds(60)
    ): Instant? {
        val delayMillis = parseDelayMillis(value, now, maxDelay) ?: return null
        return now.plusMillis(delayMillis)
    }

    fun parseDelayMillis(
        value: String?,
        now: Instant = Instant.now(),
        maxDelay: Duration = Duration.ofSeconds(60)
    ): Long? {
        val trimmed = value?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        val maxMillis = maxDelay.toMillis().coerceAtLeast(0)

        val deltaSeconds = trimmed.toLongOrNull()
        if (deltaSeconds != null) {
            if (deltaSeconds < 0) return null
            return deltaSeconds
                .coerceAtMost(maxMillis / 1000)
                .times(1000)
        }

        return try {
            val retryAt = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
            val delay = Duration.between(now, retryAt)
            if (delay.isNegative || delay.isZero) return null
            delay.toMillis().coerceAtMost(maxMillis)
        } catch (_: Exception) {
            null
        }
    }
}
