package com.codexbar.android.core.network.oauth

import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface GoogleDeviceAuthService {

    @FormUrlEncoded
    @POST("device/code")
    suspend fun requestDeviceCode(
        @Field("client_id") clientId: String,
        @Field("scope") scope: String = GOOGLE_CLOUD_PLATFORM_SCOPE
    ): Response<DeviceAuthDto.GeminiDeviceCodeResponse>

    @FormUrlEncoded
    @POST("token")
    suspend fun pollForToken(
        @Field("client_id") clientId: String,
        @Field("device_code") deviceCode: String,
        @Field("grant_type") grantType: String = GOOGLE_DEVICE_GRANT_TYPE
    ): Response<DeviceAuthDto.GeminiDeviceTokenResponse>

    companion object {
        const val BASE_URL = "https://oauth2.googleapis.com/"
        const val GOOGLE_DEVICE_VERIFICATION_URL = "https://www.google.com/device"
        const val GOOGLE_CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform"
        private const val GOOGLE_DEVICE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
    }
}
