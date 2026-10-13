package com.codexbar.android.core.network.desktop

import com.codexbar.android.core.data.DesktopQuotaSource
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.presentation.PrivacyPresentation
import com.codexbar.android.core.presentation.QuotaPresentationMapper
import java.time.Instant
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class DesktopLocalUsageTest {
    private val now = Instant.now()
    private val usage = DesktopLocalUsageDto(now.epochSecond - 60, "2026-10-14",
        todayTokens = 1234, thirtyDayTokens = 9999, todayCostUsd = 0.1, reportingPeriod = "rolling:30")
    private fun snapshot(local: DesktopLocalUsageDto) = DesktopBridgeSnapshot(1, "win-codexbar", now.epochSecond,
        listOf(DesktopProvider("codex", now.epochSecond, "ready", windows = emptyList(), localUsage = local)))

    @Test fun `local totals preserve unknowns and independent timestamps and obey privacy`() {
        DesktopBridgeClient(Json).validateSnapshot(snapshot(usage), now)
        val quota = with(DesktopQuotaSource) { snapshot(usage).providers.single().toQuota(AiService.CODEX, "pc", now) }
        assertEquals(now.epochSecond - 60, quota.desktopLocalUsage?.updatedAtEpochSeconds)
        assertEquals(1234L, quota.desktopLocalUsage?.todayTokens)
        assertNull(quota.desktopLocalUsage?.thirtyDayCostUsd)
        assertTrue(quota.isDesktopSource)
        val mapper = QuotaPresentationMapper()
        assertNotNull(mapper.map(listOf(quota)).services.single().desktopLocalUsage)
        assertNull(mapper.map(listOf(quota), privacy = PrivacyPresentation(redactSensitiveValues = true)).services.single().desktopLocalUsage)
    }

    @Test fun `reject malformed amounts dates models periods and counts`() {
        listOf(usage.copy(todayCostUsd = -0.1), usage.copy(todayTokens = -1),
            usage.copy(todayDate = "2026-99-10"), usage.copy(topModel = "person@example.com"),
            usage.copy(reportingPeriod = "rolling:366"), usage.copy(incompleteRequestCount = -1),
            usage.copy(updatedAtEpochSeconds = now.epochSecond + 121)).forEach { invalid ->
            assertThrows(DesktopBridgeProtocolException::class.java) { DesktopBridgeClient(Json).validateSnapshot(snapshot(invalid), now) }
        }
    }
}
