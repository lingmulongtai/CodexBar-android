package com.codexbar.android.debug

import com.codexbar.android.core.domain.model.*
import com.codexbar.android.core.network.github.*
import com.codexbar.android.feature.github.GitHubDashboardState
import java.time.LocalDate

/** Fictional, deterministic data; never part of a release APK or written to credentials/preferences. */
internal fun gitHubDemoData(section: String?): GitHubDashboardState {
    val today = LocalDate.now()
    val first = today.minusDays(364)
    val days = (0L..364L).map { index ->
        val date = first.plusDays(index)
        val count = if (index == 328L) 1003 else if (index < 160 && index % 13L != 0L) 0
            else if (index % 9L == 0L && index < 354) 0 else ((index * 37) % 57).toInt() + 1
        GitHubContributionDay(date.toString(), count, when { count == 0 -> "NONE"; count < 10 -> "FIRST_QUARTILE"
            count < 25 -> "SECOND_QUARTILE"; count < 40 -> "THIRD_QUARTILE"; else -> "FOURTH_QUARTILE" })
    }
    val weeks = days.groupBy { LocalDate.parse(it.date).minusDays((LocalDate.parse(it.date).dayOfWeek.value % 7).toLong()) }
        .values.map { GitHubContributionWeek(it) }
    val calendar = GitHubContributionCalendar(days.sumOf { it.contributionCount }, weeks)
    val year = GitHubContributionCollection(4308, 128, 394, 247, contributionCalendar = calendar)
    val contributions = GitHubContributions(GitHubContributionViewer(true, GitHubContributionCollection(16),
        GitHubContributionCollection(184), GitHubContributionCollection(716), year,
        GitHubContributionCollection(contributionCalendar = calendar)), false)
    val repos = listOf(
        GitHubRepository(1, "demo-dev/CodexBar-android", htmlUrl = "https://github.com/lingmulongtai/CodexBar-android",
            description = "Your AI usage, beautifully in view.", stars = 24, language = "Kotlin"),
        GitHubRepository(2, "demo-dev/InstaFrame", htmlUrl = "https://github.com/lingmulongtai/InstaFrame",
            description = "Photo & video framing with EXIF captions.", stars = 18, language = "TypeScript"),
        GitHubRepository(3, "demo-dev/AeroGrid-3D", htmlUrl = "https://github.com/lingmulongtai/AeroGrid-3D",
            description = "Explore a living 3D aviation atlas.", stars = 12, language = "TypeScript"),
        GitHubRepository(4, "demo-dev/Studio", true, "https://github.com", "Ideas taking shape.", 7, language = "Rust"))
    val kinds = when (section) {
        "achievements" -> listOf(GitHubWidgetKind.ACHIEVEMENTS)
        "stats" -> listOf(GitHubWidgetKind.STATS, GitHubWidgetKind.CALENDAR)
        "stack" -> listOf(GitHubWidgetKind.STACK, GitHubWidgetKind.PORTRAIT)
        "pins" -> listOf(GitHubWidgetKind.PINNED_REPOSITORIES, GitHubWidgetKind.STACK)
        else -> listOf(GitHubWidgetKind.PROFILE, GitHubWidgetKind.COMMITS, GitHubWidgetKind.CALENDAR, GitHubWidgetKind.STATS,
            GitHubWidgetKind.ACHIEVEMENTS, GitHubWidgetKind.STACK, GitHubWidgetKind.PORTRAIT, GitHubWidgetKind.PINNED_REPOSITORIES)
    }
    return GitHubDashboardState(profile = GitHubProfile("demo-dev", "Ryuta · Demo", 36,
        "https://avatars.githubusercontent.com/u/218745625?v=4", "Building things, one idea at a time.", "Kyoto, Japan", followers = 24, following = 18, id = 999999),
        layout = GitHubDashboardLayout(999999, kinds.map { GitHubWidget(it,
            calendarScale = if (it == GitHubWidgetKind.CALENDAR) .46f else null,
            repositoryIds = if (it == GitHubWidgetKind.PINNED_REPOSITORIES) repos.map { repo -> repo.id } else emptyList()) }),
        repositories = repos, repositoriesComplete = true, starred = repos.take(2), starredComplete = true,
        contributions = contributions, privateContributionsConnected = true,
        profileExtras = GitHubProfileExtras(listOf(
            GitHubPublicBadge("pull-shark", 3, "https://github.githubassets.com/assets/pull-shark-silver-0643f87ac9fd.png"),
            GitHubPublicBadge("quickdraw", 1, "https://github.githubassets.com/assets/quickdraw-default-39c6aec8ff89.png"), GitHubPublicBadge("yolo", 1, "https://github.githubassets.com/assets/yolo-default-be0bbff04951.png"), GitHubPublicBadge("pair-extraordinaire", 1, "https://github.githubassets.com/assets/pair-extraordinaire-default-579438a20e01.png")), true,
            listOf("python","ts","js","kotlin","rust","cs","react","nextjs","nodejs","html","css","tailwind","arduino","firebase","vercel","git","github","vscode"),
            listOf("cpp","java","swift","fastapi","docker","raspberrypi","linux","matlab"), 394))
}
