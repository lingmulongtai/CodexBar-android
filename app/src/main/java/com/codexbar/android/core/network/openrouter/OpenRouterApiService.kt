package com.codexbar.android.core.network.openrouter

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers

interface OpenRouterApiService {
    @Headers("Accept: application/json")
    @GET("key")
    suspend fun getCurrentKey(
        @Header("Authorization") authorization: String
    ): Response<OpenRouterDto.KeyEnvelope>
}
