package com.codexbar.android.core.network.zenmux

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

interface ZenMuxApiService {
    @GET("subscription/detail")
    suspend fun getSubscription(
        @Header("Authorization") authorization: String
    ): Response<ZenMuxDto.SubscriptionEnvelope>
}
