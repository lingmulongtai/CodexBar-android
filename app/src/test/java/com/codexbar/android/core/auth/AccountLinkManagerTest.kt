package com.codexbar.android.core.auth

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.network.oauth.CodexDeviceAuthService
import com.codexbar.android.core.network.oauth.DeviceAuthDto
import com.codexbar.android.core.network.oauth.GitHubDeviceAuthService
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import retrofit2.Response

class AccountLinkManagerTest {

    private lateinit var codexDeviceAuthService: CodexDeviceAuthService
    private lateinit var gitHubDeviceAuthService: GitHubDeviceAuthService
    private lateinit var manager: AccountLinkManager

    @Before
    fun setUp() {
        codexDeviceAuthService = mock(CodexDeviceAuthService::class.java)
        gitHubDeviceAuthService = mock(GitHubDeviceAuthService::class.java)
        manager = AccountLinkManager(codexDeviceAuthService, gitHubDeviceAuthService)
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

    private companion object {
        const val CODEX_CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
    }
}
