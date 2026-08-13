package com.codexbar.android.core.network.clinepass

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers

interface ClinePassApiService {
    @Headers("Accept: application/json")
    @GET("api/v1/users/me/plan/usage-limits")
    suspend fun getUsageLimits(
        @Header("Authorization") authorization: String
    ): Response<ClinePassDto.UsageLimitsEnvelope>
}
