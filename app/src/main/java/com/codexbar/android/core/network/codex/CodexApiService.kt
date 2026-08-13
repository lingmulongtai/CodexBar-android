package com.codexbar.android.core.network.codex

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers

interface CodexApiService {

    @GET("backend-api/wham/usage")
    suspend fun getUsage(
        @Header("Authorization") authorization: String,
        @Header("ChatGPT-Account-Id") accountId: String? = null,
        @Header("User-Agent") userAgent: String = "CodexBar-Android"
    ): Response<CodexDto.UsageResponse>

    @Headers(
        "OpenAI-Beta: codex-1",
        "originator: Codex Desktop"
    )
    @GET("backend-api/wham/rate-limit-reset-credits")
    suspend fun getRateLimitResetCredits(
        @Header("Authorization") authorization: String,
        @Header("ChatGPT-Account-ID") accountId: String? = null,
        @Header("User-Agent") userAgent: String = "CodexBar-Android"
    ): Response<CodexDto.ResetCreditsResponse>
}
