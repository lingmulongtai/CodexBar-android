package com.codexbar.android.core.network.github

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

internal const val GITHUB_ACTIVITY_QUERY = """
query RepositoryActivity(${'$'}year: DateTime!, ${'$'}end: DateTime!) {
  viewer { year: contributionsCollection(from: ${'$'}year, to: ${'$'}end) {
    commits: commitContributionsByRepository(maxRepositories: 100) {
      repository { nameWithOwner } contributions(first: 100) { nodes { commitCount } pageInfo { hasNextPage } }
    }
    issues: issueContributionsByRepository(maxRepositories: 100) {
      repository { nameWithOwner } contributions(first: 1) { totalCount }
    }
    pulls: pullRequestContributionsByRepository(maxRepositories: 100) {
      repository { nameWithOwner } contributions(first: 1) { totalCount }
    }
    reviews: pullRequestReviewContributionsByRepository(maxRepositories: 100) {
      repository { nameWithOwner } contributions(first: 1) { totalCount }
    }
  } }
}
"""

@Serializable internal data class GitHubActivityResponse(val data: GitHubActivityData? = null, val errors: List<JsonObject> = emptyList())
@Serializable internal data class GitHubActivityData(val viewer: GitHubActivityViewer? = null)
@Serializable internal data class GitHubActivityViewer(val year: GitHubActivityCollection? = null)
@Serializable internal data class GitHubActivityCollection(
    val commits: List<GitHubCommitActivity>? = null,
    val issues: List<GitHubOtherActivity>? = null,
    val pulls: List<GitHubOtherActivity>? = null,
    val reviews: List<GitHubOtherActivity>? = null
)
@Serializable internal data class GitHubActivityRepo(val nameWithOwner: String)
@Serializable internal data class GitHubCommitActivity(val repository: GitHubActivityRepo, val contributions: GitHubCommitConnection)
@Serializable internal data class GitHubCommitConnection(val nodes: List<GitHubCommitDay> = emptyList(), val pageInfo: GitHubActivityPageInfo)
@Serializable internal data class GitHubCommitDay(val commitCount: Int)
@Serializable internal data class GitHubActivityPageInfo(val hasNextPage: Boolean)
@Serializable internal data class GitHubOtherActivity(val repository: GitHubActivityRepo, val contributions: GitHubOtherConnection)
@Serializable internal data class GitHubOtherConnection(val totalCount: Int)
internal data class GitHubRepositoryCounts(val commits: Int?, val contributions: Int?)
internal data class GitHubRepositoryActivity(val counts: Map<String, GitHubRepositoryCounts>, val complete: Boolean)

internal fun GitHubActivityCollection.toActivity(partial: Boolean): GitHubRepositoryActivity {
    val commitGroups = commits ?: return GitHubRepositoryActivity(emptyMap(), false)
    val issueGroups = issues ?: return GitHubRepositoryActivity(emptyMap(), false)
    val pullGroups = pulls ?: return GitHubRepositoryActivity(emptyMap(), false)
    val reviewGroups = reviews ?: return GitHubRepositoryActivity(emptyMap(), false)
    val complete = !partial && listOf(commitGroups.size, issueGroups.size, pullGroups.size, reviewGroups.size).all { it < 100 } &&
        commitGroups.none { it.contributions.pageInfo.hasNextPage }
    val commitMap = commitGroups.associate { it.repository.nameWithOwner to it.contributions }
    val issueMap = issueGroups.associate { it.repository.nameWithOwner to it.contributions.totalCount }
    val pullMap = pullGroups.associate { it.repository.nameWithOwner to it.contributions.totalCount }
    val reviewMap = reviewGroups.associate { it.repository.nameWithOwner to it.contributions.totalCount }
    val names = commitMap.keys + issueMap.keys + pullMap.keys + reviewMap.keys
    val counts = names.associateWith { name ->
        val connection = commitMap[name]
        val commitCount = when {
            connection?.pageInfo?.hasNextPage == true -> null
            connection != null -> connection.nodes.sumOf { it.commitCount }
            commitGroups.size < 100 && !partial -> 0
            else -> null
        }
        fun other(map: Map<String, Int>, size: Int): Int? = map[name] ?: if (size < 100 && !partial) 0 else null
        val values = listOf(commitCount, other(issueMap, issueGroups.size), other(pullMap, pullGroups.size), other(reviewMap, reviewGroups.size))
        GitHubRepositoryCounts(commitCount, if (values.all { it != null }) values.sumOf { it!! } else null)
    }
    return GitHubRepositoryActivity(counts, complete)
}
