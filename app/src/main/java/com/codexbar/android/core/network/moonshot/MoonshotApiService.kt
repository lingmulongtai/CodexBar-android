package com.codexbar.android.core.network.moonshot

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers

interface MoonshotApiService {
    @Headers("Accept: application/json")
    @GET("v1/users/me/balance")
    suspend fun getBalance(
        @Header("Authorization") authorization: String
    ): Response<MoonshotDto.BalanceResponse>
}
