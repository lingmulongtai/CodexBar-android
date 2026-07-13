package com.codexbar.android.core.presentation

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.ExtraUsage
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.UsageWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

class QuotaPresentationMapperTest {
    private val now = Instant.parse("2026-07-13T00:00:00Z")
    private val mapper = QuotaPresentationMapper(Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `maps usage direction once for every surface`() {
        val snapshot = mapper.map(
            quotas = listOf(
                QuotaInfo(
                    service = AiService.CODEX,
                    windows = listOf(
                        UsageWindow("5h", 0.72, now.plusSeconds(7200)),
                        UsageWindow("weekly", 0.24, now.plusSeconds(172800))
                    ),
                    extraUsage = null,
                    tier = "Plus",
                    fetchedAt = now.minusSeconds(90)
                )
            ),
            locale = Locale.US
        )

        val service = snapshot.services.single()
        val primary = service.primaryMetric!!
        assertEquals("5h", primary.label)
        assertEquals(72, primary.usedPercent)
        assertEquals(28, primary.remainingPercent)
        assertEquals(0.28f, primary.barProgress, 0.001f)
        assertEquals("28% left", primary.remainingLabel)
        assertEquals("Resets in 2h 0m", primary.resetLabel)
        assertEquals("1m ago", service.freshness.ageLabel)
    }

    @Test
    fun `does not render error data as zero quota`() {
        val snapshot = mapper.map(
            quotas = emptyList(),
            errors = mapOf(AiService.GEMINI to AppError.RateLimited(now.plusSeconds(600))),
            generatedAt = now
        )

        val service = snapshot.services.single()
        assertEquals(ServiceQuotaStatus.RateLimited, service.status)
        assertNull(service.primaryMetric)
        assertEquals("No fresh data", service.freshness.ageLabel)
        assertEquals("Rate limited until 2026-07-13T00:10:00Z", service.freshness.staleReason)
        assertEquals(now.plusSeconds(600), service.freshness.nextRetryAt)
    }

    @Test
    fun `redaction removes sensitive values while preserving service shape`() {
        val snapshot = mapper.map(
            quotas = listOf(
                QuotaInfo(
                    service = AiService.CLAUDE,
                    windows = listOf(UsageWindow("weekly", 0.91, now.plusSeconds(3600))),
                    extraUsage = ExtraUsage(
                        isEnabled = true,
                        monthlyLimit = 100.0,
                        usedCredits = 91.0,
                        utilization = 0.91,
                        currency = "USD"
                    ),
                    tier = "Max",
                    fetchedAt = now
                )
            ),
            privacy = PrivacyPresentation(redactSensitiveValues = true)
        )

        val service = snapshot.services.single()
        val metric = service.metrics.single()
        assertEquals(ServiceQuotaStatus.Redacted, service.status)
        assertNull(service.tier)
        assertNull(metric.usedPercent)
        assertEquals("Remaining hidden", metric.remainingLabel)
        assertEquals(QuotaSeverity.Redacted, metric.severity)
        assertEquals("Used hidden", service.extraUsage!!.usedCreditsLabel)
    }
}
