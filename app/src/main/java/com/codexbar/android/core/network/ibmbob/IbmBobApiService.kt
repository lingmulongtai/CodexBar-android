package com.codexbar.android.core.network.ibmbob

import okhttp3.HttpUrl
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Url

interface IbmBobApiService {
    @Headers(
        "Accept: application/json",
        "Content-Type: application/json",
        "User-Agent: CodexBar-Android"
    )
    @GET("admin/v1/profile")
    suspend fun getProfile(
        @Header("Authorization") authorization: String
    ): Response<IbmBobDto.ProfileResponse>

    @Headers(
        "Accept: application/json",
        "Content-Type: application/json",
        "User-Agent: CodexBar-Android"
    )
    @GET
    suspend fun getTeamBudget(
        @Url url: HttpUrl,
        @Header("Authorization") authorization: String,
        @Header("x-instance-id") instanceId: String,
        @Header("x-team-id") teamId: String
    ): Response<IbmBobDto.TeamBudgetResponse>
}
