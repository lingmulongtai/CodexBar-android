package com.codexbar.android.core.network.cursor

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface CursorApiService {
    @GET("api/usage-summary")
    suspend fun getUsageSummary(
        @Header("Cookie") cookieHeader: String
    ): Response<CursorDto.UsageSummary>

    @GET("api/auth/me")
    suspend fun getUserInfo(
        @Header("Cookie") cookieHeader: String
    ): Response<CursorDto.UserInfo>

    @GET("api/usage")
    suspend fun getLegacyUsage(
        @Query("user") userId: String,
        @Header("Cookie") cookieHeader: String
    ): Response<CursorDto.LegacyUsageResponse>
}
