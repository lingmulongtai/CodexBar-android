package com.codexbar.android.core.network.desktop

import java.time.Instant
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class DesktopSharedDataTest {
    private val now = Instant.now()
    private val history = DesktopQuotaHistoryDto("Current", listOf(DesktopHistoryPoint(now.epochSecond - 300, 0.0), DesktopHistoryPoint(now.epochSecond, 0.2)))
    private val provider = DesktopProvider("codex", now.epochSecond, "ready", windows = listOf(DesktopWindow("Current", 0.2)),
        historyScopeId = "A".repeat(43), history = listOf(history))
    private fun snapshot(provider: DesktopProvider = this.provider, spend: DesktopSpendDto? = null) =
        DesktopBridgeSnapshot(1, "win-codexbar", now.epochSecond, listOf(provider), spend)
    private val client = DesktopBridgeClient(Json)

    @Test fun `shared history validates ownership shape chronology and size`() {
        client.validateSnapshot(snapshot(), now)
        listOf(provider.copy(historyScopeId = null), provider.copy(historyScopeId = "bad"),
            provider.copy(history = listOf(history.copy(samples = history.samples.reversed()))),
            provider.copy(history = listOf(history.copy(label = "Other account window"))),
            provider.copy(history = listOf(history.copy(samples = listOf(DesktopHistoryPoint(now.epochSecond + 900, 0.2))))),
            provider.copy(history = listOf(history.copy(samples = (0..64).map { DesktopHistoryPoint(now.epochSecond - 1000 + it, 0.1) }))))
            .forEach { assertThrows(DesktopBridgeProtocolException::class.java) { client.validateSnapshot(snapshot(it), now) } }
    }

    @Test fun `spend preserves covered zero and unknown while rejecting inflated or unscanned totals`() {
        val row = DesktopSpendRowDto("codex", "USD", false, sevenDay = 0.0, thirtyDay = null,
            daily = listOf(DesktopSpendDayDto("2026-10-16", null, null, true), DesktopSpendDayDto("2026-10-17", 0, 0.0, true)))
        val spend = DesktopSpendDto(now.epochSecond, "rolling:30", "Asia/Tokyo", true, listOf(row))
        client.validateSnapshot(snapshot(spend = spend), now)
        assertThrows(DesktopBridgeProtocolException::class.java) {
            client.validateSnapshot(snapshot(spend = spend.copy(timeZone = "not-a-zone")), now)
        }
        assertNull(spend.rows.single().thirtyDay)
        assertEquals(0.0, spend.rows.single().sevenDay!!, 0.0)
        listOf(row.copy(sevenDay = Double.POSITIVE_INFINITY), row.copy(sevenDayTokens = -1),
            row.copy(daily = listOf(DesktopSpendDayDto("2026-10-17", 0, null, false))),
            row.copy(daily = row.daily.reversed()), row.copy(providerId = "anotherpc"))
            .forEach { invalid -> assertThrows(DesktopBridgeProtocolException::class.java) { client.validateSnapshot(snapshot(spend = spend.copy(rows = listOf(invalid))), now) } }
    }
}
