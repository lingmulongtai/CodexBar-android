package com.codexbar.android.core.network.github

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class GitHubProfileExtrasTest {
    @Test fun `badge requires public link and trusted image and keeps highest tier`() {
        val prefix = "https://github.githubassets.com/images/modules/profile/achievements/"
        val html = "achievement=pull-shark ${prefix}pull-shark-default-ab12.png ${prefix}pull-shark-silver-ab12.png " +
            "${prefix}yolo-default-ab12.png achievement=evil https://evil.example/yolo-gold-ab12.png"
        assertEquals(listOf(GitHubPublicBadge("pull-shark", 3, "${prefix}pull-shark-silver-ab12.png")), parseGitHubBadges(html))
        assertTrue(parseGitHubBadges("unavailable").isEmpty())
    }
    @Test fun `stack is authored and learning comes only from second icon list`() {
        val (stack, learning) = parseGitHubStack("![stack](https://skillicons.dev/icons?theme=dark&i=python,ts,python) " +
            "<img src=\"https://skillicons.dev/icons?i=swift,java\">")
        assertEquals(listOf("python", "ts"), stack)
        assertEquals(listOf("swift", "java"), learning)
        assertEquals(emptyList<String>() to emptyList<String>(), parseGitHubStack("Java developer"))
    }
    @Test fun `rolling stats handle zeros missing dates and current day without inventing activity`() {
        val today = LocalDate.of(2026, 11, 2)
        val calendar = GitHubContributionCalendar(999, listOf(GitHubContributionWeek(listOf(
            GitHubContributionDay("2026-10-28", 4), GitHubContributionDay("2026-10-29", 2),
            GitHubContributionDay("2026-10-31", 3), GitHubContributionDay("2026-11-01", 5),
            GitHubContributionDay("2026-11-02", 0), GitHubContributionDay("2026-11-03", 100),
            GitHubContributionDay("2025-01-01", 100), GitHubContributionDay("invalid", 20)))))
        val stats = calendar.statistics(today)
        assertEquals(14, stats.total)
        assertEquals(4, stats.activeDays)
        assertEquals(2, stats.longestStreak)
        assertEquals(2, stats.currentStreak)
        assertEquals("2026-11-01", stats.bestDay?.date)
        assertEquals(listOf(9, 5), stats.monthly.map { it.second })
    }
}
