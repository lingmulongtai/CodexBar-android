package com.codexbar.android.core.auth

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.network.codex.CodexDto
import com.codexbar.android.core.network.oauth.CodexDeviceAuthService
import com.codexbar.android.core.network.oauth.DeviceAuthDto
import com.codexbar.android.core.network.oauth.GitHubDeviceAuthService
import com.codexbar.android.core.network.oauth.GoogleDeviceAuthService
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

@Singleton
class AccountLinkManager @Inject constructor(
    private val codexDeviceAuthService: CodexDeviceAuthService,
    private val gitHubDeviceAuthService: GitHubDeviceAuthService,
    private val googleDeviceAuthService: GoogleDeviceAuthService
) {

    suspend fun requestDeviceCode(
        service: AiService,
        oauthClientId: String? = null
    ): DeviceAuthSession {
        return when (service) {
            AiService.CODEX -> requestCodexDeviceCode()
            AiService.COPILOT -> requestCopilotDeviceCode()
            AiService.GEMINI -> requestGeminiDeviceCode(oauthClientId)
            AiService.CLAUDE -> throw UnsupportedOperationException(
                "${service.displayName} does not expose a supported Android device-code flow."
            )
        }
    }

    suspend fun completeDeviceCode(session: DeviceAuthSession): Credential {
        return when (session.service) {
            AiService.CODEX -> completeCodexDeviceCode(session)
            AiService.COPILOT -> completeCopilotDeviceCode(session)
            AiService.GEMINI -> completeGeminiDeviceCode(session)
            AiService.CLAUDE -> throw UnsupportedOperationException(
                "${session.service.displayName} does not expose a supported Android device-code flow."
            )
        }
    }

    private suspend fun requestCodexDeviceCode(): DeviceAuthSession {
        val response = codexDeviceAuthService.requestUserCode(
            DeviceAuthDto.CodexUserCodeRequest(clientId = CodexDto.CODEX_CLIENT_ID)
        )
        if (!response.isSuccessful) {
            throw IOException("Codex device-code request failed with HTTP ${response.code()}")
        }

        val body = response.body() ?: throw IOException("Codex device-code response was empty")
        val userCode = body.effectiveUserCode
            ?: throw IOException("Codex device-code response did not contain a user code")

        return DeviceAuthSession(
            service = AiService.CODEX,
            verificationUrl = CodexDeviceAuthService.CODEX_DEVICE_VERIFICATION_URL,
            userCode = userCode,
            deviceCode = body.deviceAuthId,
            intervalSeconds = safePollIntervalSeconds(body.intervalSeconds),
            expiresAtEpochMs = System.currentTimeMillis() + CODEX_DEVICE_EXPIRY_MS
        )
    }

    private suspend fun completeCodexDeviceCode(session: DeviceAuthSession): Credential.CodexCredential {
        val deadline = System.currentTimeMillis() + CODEX_DEVICE_EXPIRY_MS
        while (System.currentTimeMillis() < deadline) {
            val response = codexDeviceAuthService.pollForAuthorizationCode(
                DeviceAuthDto.CodexTokenPollRequest(
                    deviceAuthId = session.deviceCode,
                    userCode = session.userCode
                )
            )

            if (response.isSuccessful) {
                val code = response.body()
                    ?: throw IOException("Codex authorization-code response was empty")
                val tokenResponse = codexDeviceAuthService.exchangeAuthorizationCode(
                    code = code.authorizationCode,
                    clientId = CodexDto.CODEX_CLIENT_ID,
                    codeVerifier = code.codeVerifier
                )
                if (!tokenResponse.isSuccessful) {
                    throw IOException("Codex token exchange failed with HTTP ${tokenResponse.code()}")
                }
                val tokens = tokenResponse.body()
                    ?: throw IOException("Codex token exchange response was empty")
                return Credential.CodexCredential(
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken,
                    accountId = null
                )
            }

            when (response.code()) {
                403, 404 -> delay(pollDelayMillis(session.intervalSeconds))
                else -> throw IOException("Codex device-code polling failed with HTTP ${response.code()}")
            }
        }

        throw IOException("Codex device-code login timed out")
    }

    private suspend fun requestCopilotDeviceCode(): DeviceAuthSession {
        val response = gitHubDeviceAuthService.requestDeviceCode()
        if (!response.isSuccessful) {
            throw IOException("GitHub device-code request failed with HTTP ${response.code()}")
        }

        val body = response.body() ?: throw IOException("GitHub device-code response was empty")
        return DeviceAuthSession(
            service = AiService.COPILOT,
            verificationUrl = body.verificationUriComplete ?: body.verificationUri,
            userCode = body.userCode,
            deviceCode = body.deviceCode,
            intervalSeconds = safePollIntervalSeconds(body.interval.toLong()),
            expiresAtEpochMs = System.currentTimeMillis() + body.expiresIn * 1000L
        )
    }

    private suspend fun requestGeminiDeviceCode(oauthClientId: String?): DeviceAuthSession {
        val clientId = oauthClientId?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw IllegalArgumentException("Gemini OAuth Client ID is required")

        val response = googleDeviceAuthService.requestDeviceCode(clientId = clientId)
        if (!response.isSuccessful) {
            throw IOException("Google device-code request failed with HTTP ${response.code()}")
        }

        val body = response.body() ?: throw IOException("Google device-code response was empty")
        return DeviceAuthSession(
            service = AiService.GEMINI,
            verificationUrl = body.effectiveVerificationUrl,
            userCode = body.userCode,
            deviceCode = body.deviceCode,
            intervalSeconds = safePollIntervalSeconds(body.interval.toLong()),
            expiresAtEpochMs = System.currentTimeMillis() + body.expiresIn * 1000L,
            oauthClientId = clientId
        )
    }

    private suspend fun completeCopilotDeviceCode(session: DeviceAuthSession): Credential.CopilotCredential {
        var intervalSeconds = safePollIntervalSeconds(session.intervalSeconds)
        while (System.currentTimeMillis() < session.expiresAtEpochMs) {
            delay(pollDelayMillis(intervalSeconds))
            val response = gitHubDeviceAuthService.pollForAccessToken(deviceCode = session.deviceCode)
            if (!response.isSuccessful) {
                throw IOException("GitHub token polling failed with HTTP ${response.code()}")
            }

            val body = response.body() ?: throw IOException("GitHub token response was empty")
            when (body.error) {
                null -> {
                    val token = body.accessToken
                        ?: throw IOException("GitHub token response did not contain an access token")
                    return Credential.CopilotCredential(accessToken = token)
                }
                "authorization_pending" -> Unit
                "slow_down" -> intervalSeconds = safePollIntervalSeconds(
                    intervalSeconds + SLOW_DOWN_INCREMENT_SECONDS
                )
                "expired_token" -> throw IOException("GitHub device-code login expired")
                else -> throw IOException(body.errorDescription ?: "GitHub device-code login failed: ${body.error}")
            }
        }

        throw IOException("GitHub device-code login timed out")
    }

    private suspend fun completeGeminiDeviceCode(session: DeviceAuthSession): Credential.GeminiCredential {
        val clientId = session.oauthClientId
            ?: throw IllegalArgumentException("Gemini OAuth Client ID is required")
        var intervalSeconds = safePollIntervalSeconds(session.intervalSeconds)

        while (System.currentTimeMillis() < session.expiresAtEpochMs) {
            delay(pollDelayMillis(intervalSeconds))
            val response = googleDeviceAuthService.pollForToken(
                clientId = clientId,
                deviceCode = session.deviceCode
            )
            if (!response.isSuccessful) {
                throw IOException("Google token polling failed with HTTP ${response.code()}")
            }

            val body = response.body() ?: throw IOException("Google token response was empty")
            when (body.error) {
                null -> {
                    val accessToken = body.accessToken
                        ?: throw IOException("Google token response did not contain an access token")
                    val refreshToken = body.refreshToken
                        ?: throw IOException("Google token response did not contain a refresh token")
                    val expiresIn = body.expiresIn ?: DEFAULT_GOOGLE_TOKEN_EXPIRY_SECONDS
                    return Credential.GeminiCredential(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresAtMs = System.currentTimeMillis() + expiresIn * 1000L,
                        oauthClientId = clientId
                    )
                }
                "authorization_pending" -> Unit
                "slow_down" -> intervalSeconds = safePollIntervalSeconds(
                    intervalSeconds + SLOW_DOWN_INCREMENT_SECONDS
                )
                "expired_token" -> throw IOException("Google device-code login expired")
                "access_denied" -> throw IOException("Google device-code login was denied")
                else -> throw IOException(body.errorDescription ?: "Google device-code login failed: ${body.error}")
            }
        }

        throw IOException("Google device-code login timed out")
    }

    companion object {
        const val CODEX_DEVICE_EXPIRY_MS = 15 * 60 * 1000L
        private const val SLOW_DOWN_INCREMENT_SECONDS = 5L
        private const val DEFAULT_GOOGLE_TOKEN_EXPIRY_SECONDS = 3600
    }
}

data class DeviceAuthSession(
    val service: AiService,
    val verificationUrl: String,
    val userCode: String,
    val deviceCode: String,
    val intervalSeconds: Long,
    val expiresAtEpochMs: Long,
    val oauthClientId: String? = null
)

internal fun safePollIntervalSeconds(intervalSeconds: Long): Long {
    return intervalSeconds.coerceIn(MIN_POLL_INTERVAL_SECONDS, MAX_SAFE_POLL_INTERVAL_SECONDS)
}

internal fun pollDelayMillis(intervalSeconds: Long): Long {
    return safePollIntervalSeconds(intervalSeconds) * MILLIS_PER_SECOND
}

private const val MIN_POLL_INTERVAL_SECONDS = 5L
private const val MILLIS_PER_SECOND = 1_000L
private const val MAX_SAFE_POLL_INTERVAL_SECONDS = Long.MAX_VALUE / MILLIS_PER_SECOND
