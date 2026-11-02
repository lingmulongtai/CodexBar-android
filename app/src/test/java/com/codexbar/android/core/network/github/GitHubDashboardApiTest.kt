package com.codexbar.android.core.network.github

import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import org.junit.Assert.*
import org.junit.Test

class GitHubDashboardApiTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun `public app identifier is fetched without sending a credential`() = runTest {
        val api = GitHubDashboardApi(json) { request ->
            assertEquals("api.github.com", request.url.host)
            assertEquals("/apps/codexbar-android-dashboard", request.url.encodedPath)
            assertNull(request.header("Authorization"))
            GitHubHttpResponse(200, """{"client_id":"public-client-id","slug":"codexbar-android-dashboard","owner":{"login":"lingmulongtai"}}""")
        }
        assertEquals("public-client-id", api.clientId())
    }

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

    @Test fun `contributions authorization requests read only profile scope`() = runTest {
        val api = GitHubDashboardApi(json) { request ->
            val form = request.body as FormBody
            val values = (0 until form.size).associate { form.name(it) to form.value(it) }
            assertEquals("read:user offline_access", values["scope"])
            GitHubHttpResponse(200, """{"device_code":"secret","user_code":"ABCD-1234","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}""")
        }
        api.startContributions("public-client-id")
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

    @Test fun `repository pagination collects later pages without losing visibility`() = runTest {
        val api = GitHubDashboardApi(json) { request ->
            val page = request.url.queryParameter("page")!!.toInt()
            val rows = if (page == 1) (1..100) else 101..101
            val body = rows.joinToString(prefix = "[", postfix = "]") { id ->
                """{"id":$id,"full_name":"owner/repo$id","private":${id == 101},"html_url":"https://github.com/owner/repo$id"}"""
            }
            GitHubHttpResponse(200, body)
        }
        val result = api.allRepositories("test-token")
        assertTrue(result.complete)
        assertEquals(101, result.items.size)
        assertTrue(result.items.last().isPrivate)
    }

    @Test fun `duplicate repository pages are marked incomplete`() = runTest {
        val body = (1..100).joinToString(prefix = "[", postfix = "]") { id ->
            """{"id":$id,"full_name":"owner/repo$id","html_url":"https://github.com/owner/repo$id"}"""
        }
        val api = GitHubDashboardApi(json) { request ->
            GitHubHttpResponse(200, if (request.url.queryParameter("page") == "1") body else
                """[{"id":1,"full_name":"owner/repo1","html_url":"https://github.com/owner/repo1"}]""")
        }
        assertFalse(api.allRepositories("test-token").complete)
    }

    @Test fun `starred public repositories are fetched without credentials`() = runTest {
        val api = GitHubDashboardApi(json) { request ->
            assertEquals("api.github.com", request.url.host)
            assertEquals("/users/example/starred", request.url.encodedPath)
            assertNull(request.header("Authorization"))
            GitHubHttpResponse(200, """[{"id":1,"full_name":"owner/public","html_url":"https://github.com/owner/public"}]""")
        }
        assertEquals(1, api.starred("example").items.size)
        try { api.starred("../secret"); fail("unsafe login") } catch (_: IllegalArgumentException) { }
    }

    @Test fun `rate limit and authorization errors are not treated as empty data`() = runTest {
        for (status in listOf(401, 403, 429, 500)) {
            val api = GitHubDashboardApi(json) { GitHubHttpResponse(status, "{}") }
            try { api.profile("test-token"); fail("expected error") }
            catch (error: GitHubDashboardException) { assertEquals(status, error.status) }
        }
    }
}
