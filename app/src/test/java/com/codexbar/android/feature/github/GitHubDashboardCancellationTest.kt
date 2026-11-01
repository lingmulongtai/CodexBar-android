package com.codexbar.android.feature.github

import com.codexbar.android.core.domain.model.GitHubDashboardSession
import com.codexbar.android.core.domain.model.GitHubDashboardLayout
import com.codexbar.android.core.network.github.GitHubDashboardApi
import com.codexbar.android.core.network.github.GitHubHttpResponse
import com.codexbar.android.core.security.EncryptedPrefsManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class GitHubDashboardCancellationTest {
    @Test fun `offline saved account can still be removed`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val prefs = mock(EncryptedPrefsManager::class.java)
            `when`(prefs.gitHubDashboardRevision).thenReturn(MutableStateFlow(0L))
            `when`(prefs.currentGitHubDashboardRevision()).thenReturn(0L)
            `when`(prefs.loadGitHubDashboardSession()).thenReturn(
                GitHubDashboardSession("client", "access", "refresh", Long.MAX_VALUE)
            )
            val api = GitHubDashboardApi(Json { ignoreUnknownKeys = true }) { throw java.io.IOException("offline") }
            val vm = GitHubDashboardViewModel(api, prefs)
            runCurrent()
            assertTrue(vm.state.value.hasStoredSession)
            assertNull(vm.state.value.profile)
            assertEquals(GitHubDashboardError.Unavailable, vm.state.value.error)
            vm.signOut()
            runCurrent()
            verify(prefs).deleteGitHubDashboardSession()
            assertFalse(vm.state.value.hasStoredSession)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `global credential removal cancels pending authorization`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val reply = CompletableDeferred<GitHubHttpResponse>()
        try {
            val prefs = mock(EncryptedPrefsManager::class.java)
            val revisions = MutableStateFlow(0L)
            `when`(prefs.gitHubDashboardRevision).thenReturn(revisions)
            `when`(prefs.currentGitHubDashboardRevision()).thenReturn(0L)
            val api = GitHubDashboardApi(Json { ignoreUnknownKeys = true }) { request ->
                if (request.url.encodedPath.startsWith("/apps/")) GitHubHttpResponse(200,
                    """{"client_id":"client-id","slug":"codexbar-android-dashboard","owner":{"login":"lingmulongtai"}}""")
                else if (request.url.encodedPath.endsWith("/device/code")) GitHubHttpResponse(200,
                    """{"device_code":"secret","user_code":"ABCD-1234","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}""")
                else withContext(NonCancellable) { reply.await() }
            }
            val vm = GitHubDashboardViewModel(api, prefs)
            runCurrent(); vm.signIn(); runCurrent(); advanceTimeBy(5001); runCurrent()
            revisions.value = 1L
            runCurrent()
            reply.complete(GitHubHttpResponse(200, """{"access_token":"access","refresh_token":"refresh","expires_in":28800}"""))
            runCurrent()
            assertEquals(0, mockingDetails(prefs).invocations.count { it.method.name == "saveGitHubDashboardSessionIfRevision" })
            assertNull(vm.state.value.userCode)
            assertNull(vm.state.value.profile)
        } finally {
            reply.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test fun `global credential removal clears a loaded profile`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val prefs = mock(EncryptedPrefsManager::class.java)
            val revisions = MutableStateFlow(0L)
            `when`(prefs.gitHubDashboardRevision).thenReturn(revisions)
            `when`(prefs.currentGitHubDashboardRevision()).thenReturn(0L)
            `when`(prefs.loadGitHubDashboardSession()).thenReturn(
                GitHubDashboardSession("client", "access", "refresh", Long.MAX_VALUE))
            val api = GitHubDashboardApi(Json { ignoreUnknownKeys = true }) { request ->
                when (request.url.encodedPath) {
                    "/user" -> GitHubHttpResponse(200, """{"login":"example","public_repos":1}""")
                    "/user/repos" -> GitHubHttpResponse(200, "[]")
                    else -> throw java.io.IOException("optional data unavailable")
                }
            }
            val vm = GitHubDashboardViewModel(api, prefs)
            runCurrent()
            assertEquals("example", vm.state.value.profile?.login)
            revisions.value = 1L
            runCurrent()
            assertNull(vm.state.value.profile)
            assertFalse(vm.state.value.hasStoredSession)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `contribution authorization rejects a different GitHub account`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val prefs = mock(EncryptedPrefsManager::class.java)
            `when`(prefs.gitHubDashboardRevision).thenReturn(MutableStateFlow(0L))
            `when`(prefs.currentGitHubDashboardRevision()).thenReturn(0L)
            `when`(prefs.loadGitHubDashboardSession()).thenReturn(
                GitHubDashboardSession("app", "app-access", "app-refresh", Long.MAX_VALUE))
            `when`(prefs.loadGitHubDashboardLayout(1L)).thenReturn(GitHubDashboardLayout.default(1L))
            val api = GitHubDashboardApi(Json { ignoreUnknownKeys = true }) { request ->
                when (request.url.encodedPath) {
                    "/user" -> if (request.header("Authorization") == "Bearer oauth-access")
                        GitHubHttpResponse(200, """{"login":"other","id":2}""")
                        else GitHubHttpResponse(200, """{"login":"example","id":1}""")
                    "/user/repos" -> GitHubHttpResponse(200, "[]")
                    "/login/device/code" -> GitHubHttpResponse(200, """{"device_code":"secret","user_code":"ABCD-1234","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}""")
                    "/login/oauth/access_token" -> GitHubHttpResponse(200, """{"access_token":"oauth-access","refresh_token":"oauth-refresh","expires_in":28800,"scope":"read:user","token_type":"bearer"}""")
                    else -> throw java.io.IOException("optional data unavailable")
                }
            }
            val vm = GitHubDashboardViewModel(api, prefs)
            runCurrent()
            vm.signInContributions("client")
            runCurrent(); advanceTimeBy(5001); runCurrent()
            assertEquals(0, mockingDetails(prefs).invocations.count { it.method.name == "saveGitHubContributionsSessionIfCurrent" })
            assertFalse(vm.state.value.privateContributionsConnected)
            assertEquals(GitHubDashboardError.Unavailable, vm.state.value.contributionsLoginError)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `sign out cannot be undone by a late device authorization`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val reply = CompletableDeferred<GitHubHttpResponse>()
        try {
            val prefs = mock(EncryptedPrefsManager::class.java)
            `when`(prefs.gitHubDashboardRevision).thenReturn(MutableStateFlow(0L))
            `when`(prefs.currentGitHubDashboardRevision()).thenReturn(0L)
            val api = GitHubDashboardApi(Json { ignoreUnknownKeys = true }) { request ->
                if (request.url.encodedPath.startsWith("/apps/")) GitHubHttpResponse(200, """{"client_id":"client-id","slug":"codexbar-android-dashboard","owner":{"login":"lingmulongtai"}}""")
                else if (request.url.encodedPath.endsWith("/device/code")) GitHubHttpResponse(200,
                    """{"device_code":"secret","user_code":"ABCD-1234","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}""")
                else withContext(NonCancellable) { reply.await() }
            }
            val vm = GitHubDashboardViewModel(api, prefs)
            runCurrent()
            vm.signIn()
            runCurrent()
            advanceTimeBy(5001)
            runCurrent()
            vm.signOut()
            runCurrent()
            reply.complete(GitHubHttpResponse(200, """{"access_token":"access","refresh_token":"refresh","expires_in":28800}"""))
            runCurrent()
            verify(prefs).deleteGitHubDashboardSession()
            assertEquals(0, mockingDetails(prefs).invocations.count { it.method.name == "saveGitHubDashboardSessionIfRevision" })
            assertNull(vm.state.value.profile)
            assertNull(vm.state.value.userCode)
        } finally {
            reply.cancel()
            Dispatchers.resetMain()
        }
    }
}
