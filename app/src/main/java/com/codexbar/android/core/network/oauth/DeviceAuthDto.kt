package com.codexbar.android.core.network.oauth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

object DeviceAuthDto {

    @Serializable
    data class CodexUserCodeRequest(
        @SerialName("client_id") val clientId: String
    )

    @Serializable
    data class CodexUserCodeResponse(
        @SerialName("device_auth_id") val deviceAuthId: String,
        @SerialName("user_code") val userCode: String? = null,
        val usercode: String? = null,
        val interval: JsonElement? = null
    ) {
        val effectiveUserCode: String?
            get() = userCode ?: usercode

        val intervalSeconds: Long
            get() = interval.longValueOrNull() ?: DEFAULT_CODEX_INTERVAL_SECONDS
    }

    @Serializable
    data class CodexTokenPollRequest(
        @SerialName("device_auth_id") val deviceAuthId: String,
        @SerialName("user_code") val userCode: String
    )

    @Serializable
    data class CodexAuthorizationCodeResponse(
        @SerialName("authorization_code") val authorizationCode: String,
        @SerialName("code_verifier") val codeVerifier: String,
        @SerialName("code_challenge") val codeChallenge: String? = null
    )

    @Serializable
    data class CodexTokenExchangeResponse(
        @SerialName("id_token") val idToken: String? = null,
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String,
        @SerialName("token_type") val tokenType: String? = null,
        @SerialName("expires_in") val expiresIn: Int? = null
    )

    @Serializable
    data class GitHubDeviceCodeResponse(
        @SerialName("device_code") val deviceCode: String,
        @SerialName("user_code") val userCode: String,
        @SerialName("verification_uri") val verificationUri: String,
        @SerialName("verification_uri_complete") val verificationUriComplete: String? = null,
        @SerialName("expires_in") val expiresIn: Int,
        val interval: Int = DEFAULT_GITHUB_INTERVAL_SECONDS
    )

    @Serializable
    data class GitHubAccessTokenResponse(
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("token_type") val tokenType: String? = null,
        val scope: String? = null,
        val error: String? = null,
        @SerialName("error_description") val errorDescription: String? = null
    )

    @Serializable
    data class GeminiDeviceCodeResponse(
        @SerialName("device_code") val deviceCode: String,
        @SerialName("user_code") val userCode: String,
        @SerialName("verification_url") val verificationUrl: String? = null,
        @SerialName("verification_uri") val verificationUri: String? = null,
        @SerialName("expires_in") val expiresIn: Int,
        val interval: Int = DEFAULT_GEMINI_INTERVAL_SECONDS
    ) {
        val effectiveVerificationUrl: String
            get() = verificationUrl ?: verificationUri ?: GOOGLE_DEVICE_VERIFICATION_URL
    }

    @Serializable
    data class GeminiDeviceTokenResponse(
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Int? = null,
        @SerialName("token_type") val tokenType: String? = null,
        val scope: String? = null,
        val error: String? = null,
        @SerialName("error_description") val errorDescription: String? = null
    )

    private fun JsonElement?.longValueOrNull(): Long? {
        val primitive = this as? JsonPrimitive ?: return null
        return primitive.longOrNull
            ?: primitive.intOrNull?.toLong()
            ?: primitive.content.toLongOrNull()
    }

    private const val DEFAULT_CODEX_INTERVAL_SECONDS = 5L
    private const val DEFAULT_GITHUB_INTERVAL_SECONDS = 5
    private const val DEFAULT_GEMINI_INTERVAL_SECONDS = 5
    private const val GOOGLE_DEVICE_VERIFICATION_URL = "https://www.google.com/device"
}
