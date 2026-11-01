package com.codexbar.android.feature.github

import com.codexbar.android.core.network.github.GitHubDashboardApi
import com.codexbar.android.core.network.github.GitHubHttpResponse
import com.codexbar.android.core.security.EncryptedPrefsManager
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.verify

@OptIn(ExperimentalCoroutinesApi::class)
class GitHubDashboardCancellationTest {
    @Test fun `sign out cannot be undone by a late device authorization`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val reply = CompletableDeferred<GitHubHttpResponse>()
        try {
            val prefs = mock(EncryptedPrefsManager::class.java)
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
            assertEquals(0, mockingDetails(prefs).invocations.count { it.method.name == "saveGitHubDashboardSession" })
            assertNull(vm.state.value.profile)
            assertNull(vm.state.value.userCode)
        } finally {
            reply.cancel()
            Dispatchers.resetMain()
        }
    }
}
