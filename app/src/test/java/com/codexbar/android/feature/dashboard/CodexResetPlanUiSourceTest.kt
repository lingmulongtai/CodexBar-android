package com.codexbar.android.feature.dashboard

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexResetPlanUiSourceTest {
    private val appDir: File = listOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    @Test
    fun `reset guidance is visible on dashboard cards and details`() {
        val sourceDir = File(
            appDir,
            "src/main/java/com/codexbar/android/feature/dashboard"
        )
        val banner = File(sourceDir, "ResetPlanBanner.kt").readText()
        val card = File(sourceDir, "ServiceCard.kt").readText()
        val details = File(sourceDir, "ServiceDetailSheet.kt").readText()

        assertTrue(banner.contains("plan.actionLabel"))
        assertTrue(banner.contains("plan.deadlineLabel"))
        assertTrue(banner.contains("plan.budgetLabel"))
        assertTrue(banner.contains("R.string.reset_plan_title"))
        assertTrue(card.windowed("ResetPlanBanner".length).count { it == "ResetPlanBanner" } >= 2)
        assertTrue(details.contains("metric.resetPlan?.let"))
        assertTrue(details.contains("ResetPlanBanner("))
    }

    @Test
    fun `reset guidance title is localized`() {
        val english = File(appDir, "src/main/res/values/strings.xml").readText()
        val japanese = File(appDir, "src/main/res/values-ja/strings.xml").readText()

        assertTrue(english.contains("name=\"reset_plan_title\""))
        assertTrue(japanese.contains("name=\"reset_plan_title\""))
    }
}
