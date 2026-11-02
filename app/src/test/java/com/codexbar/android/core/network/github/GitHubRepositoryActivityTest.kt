package com.codexbar.android.core.network.github

import org.junit.Assert.*
import org.junit.Test

class GitHubRepositoryActivityTest {
    @Test fun `commit days sum commits rather than counting active days`() {
        val repo = GitHubActivityRepo("owner/repo")
        val data = GitHubActivityCollection(
            commits = listOf(GitHubCommitActivity(repo, GitHubCommitConnection(
                listOf(GitHubCommitDay(7), GitHubCommitDay(4)), GitHubActivityPageInfo(false)))),
            issues = listOf(GitHubOtherActivity(repo, GitHubOtherConnection(2))),
            pulls = listOf(GitHubOtherActivity(repo, GitHubOtherConnection(3))),
            reviews = emptyList()
        ).toActivity(false)
        assertTrue(data.complete)
        assertEquals(11, data.counts["owner/repo"]!!.commits)
        assertEquals(16, data.counts["owner/repo"]!!.contributions)
    }

    @Test fun `truncated commit days remain unknown instead of an invented total`() {
        val repo = GitHubActivityRepo("owner/repo")
        val data = GitHubActivityCollection(
            commits = listOf(GitHubCommitActivity(repo, GitHubCommitConnection(
                listOf(GitHubCommitDay(7)), GitHubActivityPageInfo(true)))),
            issues = emptyList(), pulls = emptyList(), reviews = emptyList()
        ).toActivity(false)
        assertFalse(data.complete)
        assertNull(data.counts["owner/repo"]!!.commits)
        assertNull(data.counts["owner/repo"]!!.contributions)
    }
}
