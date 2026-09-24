package com.codexbar.android.core.network.devin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface DevinApiService {
    @GET("v3/organizations/{org_id}/consumption/daily")
    suspend fun getDailyConsumption(
        @Path("org_id") organizationId: String,
        @Query("time_after") timeAfter: Long,
        @Query("time_before") timeBefore: Long,
        @Header("Authorization") authorization: String
    ): Response<DevinConsumption>
}

@Serializable
data class DevinConsumption(@SerialName("total_acus") val totalAcus: Double)
