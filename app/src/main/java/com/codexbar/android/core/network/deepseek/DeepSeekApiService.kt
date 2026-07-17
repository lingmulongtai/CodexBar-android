package com.codexbar.android.core.network.deepseek

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers

interface DeepSeekApiService {
    @Headers("Accept: application/json")
    @GET("user/balance")
    suspend fun getBalance(
        @Header("Authorization") authorization: String
    ): Response<DeepSeekDto.BalanceResponse>
}
