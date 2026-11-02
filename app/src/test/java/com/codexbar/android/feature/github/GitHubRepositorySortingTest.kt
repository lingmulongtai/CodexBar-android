package com.codexbar.android.feature.github

import com.codexbar.android.core.network.github.GitHubRepository
import com.codexbar.android.core.network.github.GitHubRepositoryActivity
import com.codexbar.android.core.network.github.GitHubRepositoryCounts
import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubRepositorySortingTest {
    private val repos = listOf(
        GitHubRepository(1, "me/old", false, "https://github.com/me/old", stars = 30, updatedAt = "2025-01-01T00:00:00Z"),
        GitHubRepository(2, "me/new", false, "https://github.com/me/new", stars = 4, updatedAt = "2026-01-01T00:00:00Z"),
        GitHubRepository(3, "me/private", true, "https://github.com/me/private", stars = 1, updatedAt = "2024-01-01T00:00:00Z")
    )
    @Test fun `updated and stars sorts use their own fields`() {
        assertEquals(listOf(2L, 1L, 3L), sortRepositories(repos, RepositorySort.Updated, null).map { it.id })
        assertEquals(listOf(1L, 2L, 3L), sortRepositories(repos, RepositorySort.Stars, null).map { it.id })
    }
    @Test fun `unknown private contributions sort after known zero`() {
        val activity = GitHubRepositoryActivity(mapOf("me/old" to GitHubRepositoryCounts(5, 6)), complete = true)
        assertEquals(listOf(1L, 2L, 3L), sortRepositories(repos, RepositorySort.Commits, activity).map { it.id })
    }
}
