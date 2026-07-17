package com.codexbar.android.core.network.kimi

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers

interface KimiApiService {
    @Headers("Accept: application/json")
    @GET("coding/v1/usages")
    suspend fun getUsage(
        @Header("Authorization") authorization: String
    ): Response<KimiDto.UsageEnvelope>
}
