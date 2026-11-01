package com.codexbar.android.feature.github

import com.codexbar.android.core.network.github.GitHubTokenResponse
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class GitHubContributionsScopeTest {
    @Test fun `only read user bearer tokens are accepted`() {
        val token = GitHubTokenResponse(accessToken = "a", refreshToken = "r", expiresIn = 100,
            scope = "read:user", tokenType = "bearer")
        assertEquals("a", token.toContributionsToken("client").accessToken)
        for (invalid in listOf(token.copy(scope = "repo,read:user"), token.copy(scope = ""), token.copy(tokenType = "other"))) {
            try { invalid.toContributionsToken("client"); fail("invalid scope accepted") } catch (_: IOException) { }
        }
    }
}
