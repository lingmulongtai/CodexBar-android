package com.codexbar.android.core.network.oauth

import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface GitHubDeviceAuthService {

    @FormUrlEncoded
    @POST("login/device/code")
    suspend fun requestDeviceCode(
        @Field("client_id") clientId: String = GITHUB_COPILOT_CLIENT_ID,
        @Field("scope") scope: String = GITHUB_COPILOT_SCOPE
    ): Response<DeviceAuthDto.GitHubDeviceCodeResponse>

    @FormUrlEncoded
    @POST("login/oauth/access_token")
    suspend fun pollForAccessToken(
        @Field("client_id") clientId: String = GITHUB_COPILOT_CLIENT_ID,
        @Field("device_code") deviceCode: String,
        @Field("grant_type") grantType: String = GITHUB_DEVICE_GRANT_TYPE
    ): Response<DeviceAuthDto.GitHubAccessTokenResponse>

    companion object {
        const val BASE_URL = "https://github.com/"
        const val GITHUB_COPILOT_CLIENT_ID = "Iv1.b507a08c87ecfe98"
        const val GITHUB_COPILOT_SCOPE = "read:user"
        private const val GITHUB_DEVICE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
    }
}
