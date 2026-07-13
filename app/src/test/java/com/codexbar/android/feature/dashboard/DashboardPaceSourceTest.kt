package com.codexbar.android.feature.dashboard

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardPaceSourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test
    fun `dashboard records history and maps calculated pace`() {
        val source = File(
            appDir,
            "src/main/java/com/codexbar/android/feature/dashboard/DashboardViewModel.kt"
        ).readText().replace("\r\n", "\n")

        val record = source.indexOf("quotaHistoryStore.record(successfulQuotas)")
        val calculate = source.indexOf("quotaHistoryStore.paceFor(successfulQuotas, now)")
        val map = source.indexOf("presentationMapper.map(", startIndex = calculate)
        val passPace = source.indexOf("paceByMetricKey = paceByMetricKey", startIndex = map)

        assertTrue("history must be recorded", record >= 0)
        assertTrue("pace must be calculated after recording", calculate > record)
        assertTrue("dashboard presentation must be mapped", map > calculate)
        assertTrue("calculated pace must reach the presentation mapper", passPace > map)
    }
}
