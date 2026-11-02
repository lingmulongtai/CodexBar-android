package com.codexbar.android.feature.github

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubAvatarUrlTest {
    @Test fun `avatar is restricted to GitHub HTTPS without embedded credentials`() {
        assertNotNull(safeGitHubAvatarUrl("https://avatars.githubusercontent.com/u/42?v=4"))
        assertNull(safeGitHubAvatarUrl("http://avatars.githubusercontent.com/u/42"))
        assertNull(safeGitHubAvatarUrl("https://avatars.githubusercontent.com.evil.example/u/42"))
        assertNull(safeGitHubAvatarUrl("https://user:password@avatars.githubusercontent.com/u/42"))
    }
}
