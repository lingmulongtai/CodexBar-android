package com.codexbar.android.core.network.gemini

import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface GeminiTokenRefreshService {

    @FormUrlEncoded
    @POST("token")
    suspend fun refreshToken(
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String,
        @Field("client_id") clientId: String
    ): Response<GeminiDto.TokenRefreshResponse>

    companion object {
        const val BASE_URL = "https://oauth2.googleapis.com/"
    }
}
