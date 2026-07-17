package com.codexbar.android.core.network.zai

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

interface ZaiApiService {
    @GET("api/monitor/usage/quota/limit")
    suspend fun getQuotaLimits(
        @Header("Authorization") authorization: String
    ): Response<ZaiDto.QuotaEnvelope>
}
