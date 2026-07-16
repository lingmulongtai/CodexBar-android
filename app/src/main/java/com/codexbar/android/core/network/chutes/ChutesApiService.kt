package com.codexbar.android.core.network.chutes

import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers

interface ChutesApiService {
    @Headers("Accept: application/json")
    @GET("users/me/subscription_usage")
    suspend fun getSubscriptionUsage(
        @Header("Authorization") authorization: String
    ): Response<JsonElement>

    @Headers("Accept: application/json")
    @GET("users/me/quotas")
    suspend fun getQuotas(
        @Header("Authorization") authorization: String
    ): Response<JsonElement>
}
