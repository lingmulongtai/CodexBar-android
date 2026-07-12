package com.codexbar.android.core.network.copilot

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

interface CopilotApiService {

    @GET("copilot_internal/user")
    suspend fun getUsage(
        @Header("Authorization") authorization: String,
        @Header("Accept") accept: String = "application/json",
        @Header("Editor-Version") editorVersion: String = "vscode/1.96.2",
        @Header("Editor-Plugin-Version") editorPluginVersion: String = "copilot-chat/0.26.7",
        @Header("User-Agent") userAgent: String = "GitHubCopilotChat/0.26.7",
        @Header("X-Github-Api-Version") apiVersion: String = "2025-04-01"
    ): Response<CopilotDto.UsageResponse>
}
