package com.codexbar.android.core.network.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

internal const val GITHUB_CONTRIBUTIONS_QUERY = """
query DashboardContributions(${'$'}today: DateTime!, ${'$'}week: DateTime!, ${'$'}month: DateTime!, ${'$'}year: DateTime!, ${'$'}end: DateTime!) {
  viewer {
    isDeveloperProgramMember
    today: contributionsCollection(from: ${'$'}today, to: ${'$'}end) { totalCommitContributions }
    week: contributionsCollection(from: ${'$'}week, to: ${'$'}end) { totalCommitContributions }
    month: contributionsCollection(from: ${'$'}month, to: ${'$'}end) { totalCommitContributions }
    year: contributionsCollection(from: ${'$'}year, to: ${'$'}end) {
      totalCommitContributions totalIssueContributions totalPullRequestContributions
      totalPullRequestReviewContributions restrictedContributionsCount
      contributionCalendar { totalContributions weeks { contributionDays { date contributionCount contributionLevel } } }
    }
  }
}
"""

@Serializable
internal data class GitHubContributionsResponse(
    val data: GitHubContributionsData? = null,
    val errors: List<JsonObject> = emptyList()
)
@Serializable
internal data class GitHubContributionsData(val viewer: GitHubContributionViewer? = null)
@Serializable
internal data class GitHubContributionViewer(
    val isDeveloperProgramMember: Boolean? = null,
    val today: GitHubContributionCollection? = null,
    val week: GitHubContributionCollection? = null,
    val month: GitHubContributionCollection? = null,
    val year: GitHubContributionCollection? = null
)
@Serializable
internal data class GitHubContributionCollection(
    val totalCommitContributions: Int? = null,
    val totalIssueContributions: Int? = null,
    val totalPullRequestContributions: Int? = null,
    val totalPullRequestReviewContributions: Int? = null,
    val restrictedContributionsCount: Int? = null,
    val contributionCalendar: GitHubContributionCalendar? = null
)
@Serializable
internal data class GitHubContributionCalendar(
    val totalContributions: Int = 0,
    val weeks: List<GitHubContributionWeek> = emptyList()
)
@Serializable
internal data class GitHubContributionWeek(val contributionDays: List<GitHubContributionDay> = emptyList())
@Serializable
internal data class GitHubContributionDay(val date: String, val contributionCount: Int = 0,
    val contributionLevel: String = "NONE")
internal data class GitHubContributions(val viewer: GitHubContributionViewer, val partial: Boolean)
