package com.codexbar.android.core.network.github

import java.time.LocalDate
import java.time.YearMonth

internal data class GitHubPublicBadge(val slug: String, val tier: Int, val imageUrl: String)
internal data class GitHubProfileExtras(val badges: List<GitHubPublicBadge> = emptyList(),
    val badgesAvailable: Boolean = false, val stack: List<String> = emptyList(), val learning: List<String> = emptyList(),
    val mergedPullRequests: Int? = null)

internal fun safeGitHubBadgeImageUrl(value: String?): String? = value?.takeIf {
    it.matches(Regex("""https://github\.githubassets\.com/images/modules/profile/achievements/[a-z-]+-(default|bronze|silver|gold)-[a-fA-F0-9]+\.png"""))
}

internal fun parseGitHubBadges(html: String): List<GitHubPublicBadge> {
    val known = setOf("pull-shark", "starstruck", "pair-extraordinaire", "galaxy-brain", "quickdraw", "yolo", "public-sponsor")
    return Regex("""https://github\.githubassets\.com/images/modules/profile/achievements/([a-z-]+)-(default|bronze|silver|gold)-[a-fA-F0-9]+\.png""")
        .findAll(html).mapNotNull { match ->
            val slug = match.groupValues[1]
            if (slug !in known || !html.contains("achievement=$slug")) null else GitHubPublicBadge(slug,
                listOf("default", "bronze", "silver", "gold").indexOf(match.groupValues[2]) + 1, match.value)
        }.groupBy { it.slug }.map { (_, values) -> values.maxBy { it.tier } }.take(7)
}

internal fun parseGitHubStack(readme: String): Pair<List<String>, List<String>> {
    val lists = Regex("""https://skillicons\.dev/icons\?[^\s"<>)]*""").findAll(readme).map { m ->
        Regex("(?:[?&]|&amp;)i=([a-z0-9,]+)").find(m.value)?.groupValues?.get(1)?.split(',')
            .orEmpty().filter { it.matches(Regex("[a-z0-9]{1,24}")) }.distinct().take(40)
    }.filter { it.isNotEmpty() }.take(2).toList()
    return lists.getOrElse(0) { emptyList() } to lists.getOrElse(1) { emptyList() }
}

internal data class GitHubCalendarStats(val total: Int, val activeDays: Int, val days: Int,
    val bestDay: GitHubContributionDay?, val longestStreak: Int, val currentStreak: Int,
    val busiestWeekday: Int?, val monthly: List<Pair<YearMonth, Int>>)

internal fun GitHubContributionCalendar.statistics(today: LocalDate): GitHubCalendarStats {
    val dated = weeks.flatMap { it.contributionDays }.mapNotNull { day ->
        runCatching { LocalDate.parse(day.date) }.getOrNull()?.takeIf { !it.isAfter(today) && !it.isBefore(today.minusDays(364)) }
            ?.let { it to day.copy(contributionCount = day.contributionCount.coerceAtLeast(0)) }
    }.distinctBy { it.first }.sortedBy { it.first }
    var longest = 0; var streak = 0; var previous: LocalDate? = null
    dated.forEach { (date, day) ->
        streak = if (day.contributionCount > 0) { if (previous == date.minusDays(1)) streak + 1 else 1 } else 0
        longest = maxOf(longest, streak); previous = date
    }
    val counts = dated.associate { it.first to it.second.contributionCount }
    var end = if ((counts[today] ?: 0) > 0) today else today.minusDays(1)
    var current = 0
    while ((counts[end] ?: 0) > 0) { current++; end = end.minusDays(1) }
    val weekday = dated.groupBy { it.first.dayOfWeek.value }.filterValues { entries -> entries.sumOf { it.second.contributionCount } > 0 }
        .maxByOrNull { (_, entries) -> entries.sumOf { it.second.contributionCount }.toDouble() / entries.size }?.key
    val monthly = dated.groupBy { YearMonth.from(it.first) }.toSortedMap().map { (month, entries) -> month to entries.sumOf { it.second.contributionCount } }
    return GitHubCalendarStats(dated.sumOf { it.second.contributionCount }, dated.count { it.second.contributionCount > 0 }, dated.size,
        dated.maxByOrNull { it.second.contributionCount }?.second, longest, current, weekday, monthly)
}
