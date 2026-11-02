package com.codexbar.android.feature.github

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubDashboardUrlTest {
    @Test fun `repository links stay on the exact GitHub repository`() {
        assertTrue(isGitHubRepositoryUrl("https://github.com/owner/project", "owner/project"))
        listOf("http://github.com/owner/project", "https://github.com.evil.example/owner/project",
            "https://attacker@github.com/owner/project", "https://github.com/owner/other",
            "https://github.com/owner/project?next=evil", "https://github.com/owner/project#fragment")
            .forEach { assertFalse(it, isGitHubRepositoryUrl(it, "owner/project")) }
        assertFalse(isGitHubRepositoryUrl("https://github.com/owner/project", "owner/../project"))
    }
}
