package com.codexbar.android.core.network.fireworks

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

interface FireworksApiService {
    @Headers("Accept: application/json")
    @GET("v1/accounts/{accountSlug}/billing/summary")
    suspend fun getBillingSummary(
        @Path("accountSlug") accountSlug: String,
        @Query("startTime") startTime: String,
        @Query("endTime") endTime: String,
        @Header("Authorization") authorization: String
    ): Response<FireworksDto.BillingSummaryResponse>
}
