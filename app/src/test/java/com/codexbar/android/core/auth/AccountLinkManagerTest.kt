package com.codexbar.android.core.auth

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.network.oauth.CodexDeviceAuthService
import com.codexbar.android.core.network.oauth.DeviceAuthDto
import com.codexbar.android.core.network.oauth.GitHubDeviceAuthService
import com.codexbar.android.core.network.oauth.GoogleDeviceAuthService
import java.io.IOException
import java.net.UnknownHostException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import retrofit2.Response

class AccountLinkManagerTest {

    private lateinit var codexDeviceAuthService: CodexDeviceAuthService
    private lateinit var gitHubDeviceAuthService: GitHubDeviceAuthService
    private lateinit var googleDeviceAuthService: GoogleDeviceAuthService
    private lateinit var manager: AccountLinkManager

    @Before
    fun setUp() {
        codexDeviceAuthService = mock(CodexDeviceAuthService::class.java)
        gitHubDeviceAuthService = mock(GitHubDeviceAuthService::class.java)
        googleDeviceAuthService = mock(GoogleDeviceAuthService::class.java)
        manager = AccountLinkManager(codexDeviceAuthService, gitHubDeviceAuthService, googleDeviceAuthService)
    }

    @Test
    fun `codex device-code flow exchanges authorization code for credential`() = runTest {
        `when`(codexDeviceAuthService.requestUserCode(DeviceAuthDto.CodexUserCodeRequest(CODEX_CLIENT_ID)))
            .thenReturn(
                Response.success(
                    DeviceAuthDto.CodexUserCodeResponse(
                        deviceAuthId = "device-auth-id",
                        userCode = "ABCD-EFGH",
                        interval = JsonPrimitive("5")
                    )
                )
            )
        `when`(
            codexDeviceAuthService.pollForAuthorizationCode(
                DeviceAuthDto.CodexTokenPollRequest("device-auth-id", "ABCD-EFGH")
            )
        ).thenReturn(
            Response.success(
                DeviceAuthDto.CodexAuthorizationCodeResponse(
                    authorizationCode = "authorization-code",
                    codeVerifier = "code-verifier"
                )
            )
        )
        `when`(
            codexDeviceAuthService.exchangeAuthorizationCode(
                code = "authorization-code",
                clientId = CODEX_CLIENT_ID,
                codeVerifier = "code-verifier"
            )
        ).thenReturn(
            Response.success(
                DeviceAuthDto.CodexTokenExchangeResponse(
                    accessToken = "access-token",
                    refreshToken = "refresh-token"
                )
            )
        )

        val session = manager.requestDeviceCode(AiService.CODEX)
        val credential = manager.completeDeviceCode(session)

        assertTrue(credential is Credential.CodexCredential)
        credential as Credential.CodexCredential
        assertEquals("access-token", credential.accessToken)
        assertEquals("refresh-token", credential.refreshToken)
    }

    @Test
    fun `codex device-code flow retries transient DNS failures after browser sign-in`() = runTest {
        val pollRequest = DeviceAuthDto.CodexTokenPollRequest("device-auth-id", "ABCD-EFGH")
        `when`(codexDeviceAuthService.requestUserCode(DeviceAuthDto.CodexUserCodeRequest(CODEX_CLIENT_ID)))
            .thenReturn(
                Response.success(
                    DeviceAuthDto.CodexUserCodeResponse(
                        deviceAuthId = "device-auth-id",
                        userCode = "ABCD-EFGH",
                        interval = JsonPrimitive("5")
                    )
                )
            )
        `when`(codexDeviceAuthService.pollForAuthorizationCode(pollRequest))
            .thenAnswer { throw UnknownHostException("auth.openai.com") }
            .thenReturn(
                Response.success(
                    DeviceAuthDto.CodexAuthorizationCodeResponse(
                        authorizationCode = "authorization-code",
                        codeVerifier = "code-verifier"
                    )
                )
            )
        `when`(
            codexDeviceAuthService.exchangeAuthorizationCode(
                code = "authorization-code",
                clientId = CODEX_CLIENT_ID,
                codeVerifier = "code-verifier"
            )
        )
            .thenAnswer { throw UnknownHostException("auth.openai.com") }
            .thenReturn(
                Response.success(
                    DeviceAuthDto.CodexTokenExchangeResponse(
                        accessToken = "access-token",
                        refreshToken = "refresh-token"
                    )
                )
            )

        val session = manager.requestDeviceCode(AiService.CODEX)
        val credential = manager.completeDeviceCode(session) as Credential.CodexCredential

        assertEquals("access-token", credential.accessToken)
        assertEquals("refresh-token", credential.refreshToken)
        verify(codexDeviceAuthService, times(2)).pollForAuthorizationCode(pollRequest)
        verify(codexDeviceAuthService, times(2)).exchangeAuthorizationCode(
            code = "authorization-code",
            clientId = CODEX_CLIENT_ID,
            codeVerifier = "code-verifier"
        )
    }

    @Test
    fun `copilot device-code flow returns access-token credential`() = runTest {
        `when`(gitHubDeviceAuthService.requestDeviceCode())
            .thenReturn(
                Response.success(
                    DeviceAuthDto.GitHubDeviceCodeResponse(
                        deviceCode = "device-code",
                        userCode = "WXYZ-1234",
                        verificationUri = "https://github.com/login/device",
                        verificationUriComplete = null,
                        expiresIn = 900,
                        interval = 5
                    )
                )
            )
        `when`(gitHubDeviceAuthService.pollForAccessToken(deviceCode = "device-code"))
            .thenReturn(
                Response.success(
                    DeviceAuthDto.GitHubAccessTokenResponse(
                        accessToken = "github-token",
                        tokenType = "bearer",
                        scope = "read:user"
                    )
                )
            )

        val session = manager.requestDeviceCode(AiService.COPILOT)
        val credential = manager.completeDeviceCode(session)

        assertTrue(credential is Credential.CopilotCredential)
        assertEquals("github-token", credential.accessToken)
    }

    @Test
    fun `copilot device-code flow retries transient DNS failures after browser sign-in`() = runTest {
        `when`(gitHubDeviceAuthService.requestDeviceCode())
            .thenReturn(
                Response.success(
                    DeviceAuthDto.GitHubDeviceCodeResponse(
                        deviceCode = "device-code",
                        userCode = "WXYZ-1234",
                        verificationUri = "https://github.com/login/device",
                        expiresIn = 900,
                        interval = 5
                    )
                )
            )
        `when`(gitHubDeviceAuthService.pollForAccessToken(deviceCode = "device-code"))
            .thenAnswer {
                throw IOException(
                    "temporary GitHub DNS failure",
                    UnknownHostException("github.com")
                )
            }
            .thenReturn(
                Response.success(
                    DeviceAuthDto.GitHubAccessTokenResponse(
                        accessToken = "github-token",
                        tokenType = "bearer",
                        scope = "read:user"
                    )
                )
            )

        val session = manager.requestDeviceCode(AiService.COPILOT)
        val credential = manager.completeDeviceCode(session) as Credential.CopilotCredential

        assertEquals("github-token", credential.accessToken)
        verify(gitHubDeviceAuthService, times(2))
            .pollForAccessToken(deviceCode = "device-code")
    }

    @Test
    fun `gemini device-code flow returns refreshable credential`() = runTest {
        `when`(googleDeviceAuthService.requestDeviceCode(clientId = "gemini-client-id"))
            .thenReturn(
                Response.success(
                    DeviceAuthDto.GeminiDeviceCodeResponse(
                        deviceCode = "google-device-code",
                        userCode = "GEMI-NI12",
                        verificationUrl = "https://www.google.com/device",
                        expiresIn = 900,
                        interval = 5
                    )
                )
            )
        `when`(
            googleDeviceAuthService.pollForToken(
                clientId = "gemini-client-id",
                deviceCode = "google-device-code"
            )
        ).thenReturn(
            Response.success(
                DeviceAuthDto.GeminiDeviceTokenResponse(
                    accessToken = "google-access-token",
                    refreshToken = "google-refresh-token",
                    expiresIn = 3600
                )
            )
        )

        val session = manager.requestDeviceCode(
            service = AiService.GEMINI,
            oauthClientId = "gemini-client-id"
        )
        val credential = manager.completeDeviceCode(session)

        assertTrue(credential is Credential.GeminiCredential)
        credential as Credential.GeminiCredential
        assertEquals("google-access-token", credential.accessToken)
        assertEquals("google-refresh-token", credential.refreshToken)
        assertEquals("gemini-client-id", credential.oauthClientId)
    }

    @Test
    fun `codex device-code interval is bounded before millisecond conversion`() = runTest {
        `when`(codexDeviceAuthService.requestUserCode(DeviceAuthDto.CodexUserCodeRequest(CODEX_CLIENT_ID)))
            .thenReturn(
                Response.success(
                    DeviceAuthDto.CodexUserCodeResponse(
                        deviceAuthId = "device-auth-id",
                        userCode = "ABCD-EFGH",
                        interval = JsonPrimitive(Long.MAX_VALUE.toString())
                    )
                )
            )

        val session = manager.requestDeviceCode(AiService.CODEX)

        assertEquals(Long.MAX_VALUE / 1_000L, session.intervalSeconds)
        assertTrue(pollDelayMillis(session.intervalSeconds) > 0L)
        assertEquals(5_000L, pollDelayMillis(Long.MIN_VALUE))
    }

    @Test
    fun `copilot rejects an untrusted verification URI`() = runTest {
        `when`(gitHubDeviceAuthService.requestDeviceCode())
            .thenReturn(
                Response.success(
                    DeviceAuthDto.GitHubDeviceCodeResponse(
                        deviceCode = "device-code",
                        userCode = "WXYZ-1234",
                        verificationUri = "intent://phishing",
                        verificationUriComplete = "https://example.com/login/device",
                        expiresIn = 900
                    )
                )
            )

        val session = manager.requestDeviceCode(AiService.COPILOT)

        assertEquals(GitHubDeviceAuthService.GITHUB_DEVICE_VERIFICATION_URL, session.verificationUrl)
    }

    @Test
    fun `gemini accepts only an HTTPS Google verification URI`() = runTest {
        `when`(googleDeviceAuthService.requestDeviceCode(clientId = "gemini-client-id"))
            .thenReturn(
                Response.success(
                    DeviceAuthDto.GeminiDeviceCodeResponse(
                        deviceCode = "google-device-code",
                        userCode = "GEMI-NI12",
                        verificationUrl = "file:///data/local/tmp/phishing.html",
                        verificationUri = "https://example.com/device",
                        expiresIn = 900
                    )
                )
            )

        val session = manager.requestDeviceCode(AiService.GEMINI, "gemini-client-id")

        assertEquals(GoogleDeviceAuthService.GOOGLE_DEVICE_VERIFICATION_URL, session.verificationUrl)
        assertEquals(
            "https://accounts.google.com/o/oauth2/device/usercode?user_code=ABCD",
            trustedGoogleDeviceVerificationUrl(
                "https://accounts.google.com/o/oauth2/device/usercode?user_code=ABCD"
            )
        )
    }

    private companion object {
        const val CODEX_CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
    }
}
