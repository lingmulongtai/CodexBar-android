package com.codexbar.android.core.network.oauth

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface CodexDeviceAuthService {

    @POST("api/accounts/deviceauth/usercode")
    suspend fun requestUserCode(
        @Body request: DeviceAuthDto.CodexUserCodeRequest
    ): Response<DeviceAuthDto.CodexUserCodeResponse>

    @POST("api/accounts/deviceauth/token")
    suspend fun pollForAuthorizationCode(
        @Body request: DeviceAuthDto.CodexTokenPollRequest
    ): Response<DeviceAuthDto.CodexAuthorizationCodeResponse>

    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun exchangeAuthorizationCode(
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String = CODEX_DEVICE_REDIRECT_URI,
        @Field("client_id") clientId: String,
        @Field("code_verifier") codeVerifier: String
    ): Response<DeviceAuthDto.CodexTokenExchangeResponse>

    companion object {
        const val BASE_URL = "https://auth.openai.com/"
        const val CODEX_DEVICE_VERIFICATION_URL = "https://auth.openai.com/codex/device"
        const val CODEX_DEVICE_REDIRECT_URI = "https://auth.openai.com/deviceauth/callback"
    }
}
