package com.codexbar.android.core.network.github

import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import org.junit.Assert.*
import org.junit.Test

class GitHubDashboardApiTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun `device authorization accepts only the official verification page`() = runTest {
        val replies = mutableListOf<String>()
        val api = GitHubDashboardApi(json) { request ->
            assertEquals("github.com", request.url.host)
            val form = request.body as FormBody
            replies += (0 until form.size).associate { form.name(it) to form.value(it) }["scope"].orEmpty()
            GitHubHttpResponse(200, """{"device_code":"secret","user_code":"ABCD-1234","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}""")
        }
        val code = api.start("public-client-id")
        assertEquals("offline_access", replies.single())
        assertFalse(code.toString().contains("secret"))
        val unsafe = GitHubDashboardApi(json) { GitHubHttpResponse(200, """{"device_code":"secret","user_code":"ABCD-1234","verification_uri":"https://evil.example/device","expires_in":900}""") }
        try { unsafe.start("public-client-id"); fail("unsafe verification URL") } catch (_: IOException) { }
    }

    @Test fun `private repository requests stay on the GitHub API and parse visibility`() = runTest {
        val api = GitHubDashboardApi(json) { request ->
            assertEquals("https", request.url.scheme)
            assertEquals("api.github.com", request.url.host)
            assertEquals("Bearer test-token", request.header("Authorization"))
            GitHubHttpResponse(200, """[{"id":1,"full_name":"owner/private","private":true,"html_url":"https://github.com/owner/private"}]""")
        }
        val repo = api.repositories("test-token").single()
        assertTrue(repo.isPrivate)
        assertEquals("owner/private", repo.fullName)
    }

    @Test fun `rate limit and authorization errors are not treated as empty data`() = runTest {
        for (status in listOf(401, 403, 429, 500)) {
            val api = GitHubDashboardApi(json) { GitHubHttpResponse(status, "{}") }
            try { api.profile("test-token"); fail("expected error") }
            catch (error: GitHubDashboardException) { assertEquals(status, error.status) }
        }
    }
}
