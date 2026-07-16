package com.codexbar.android.core.network.elevenlabs

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers

interface ElevenLabsApiService {
    @Headers("Accept: application/json")
    @GET("v1/user/subscription")
    suspend fun getSubscription(
        @Header("xi-api-key") apiKey: String
    ): Response<ElevenLabsDto.Subscription>
}
