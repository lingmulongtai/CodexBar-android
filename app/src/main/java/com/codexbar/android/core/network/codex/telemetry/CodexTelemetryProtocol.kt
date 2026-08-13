package com.codexbar.android.core.network.codex.telemetry

import kotlinx.serialization.Serializable

@Serializable
internal data class CodexTelemetryRequest(
    val protocolVersion: Int,
    val companionId: String,
    val requestedAtEpochSeconds: Long,
    val nonce: String,
    val signature: String
)

@Serializable
internal data class CodexTelemetryEnvelope(
    val protocolVersion: Int,
    val companionId: String,
    val requestNonce: String,
    val sentAtEpochSeconds: Long,
    val iv: String,
    val ciphertext: String
)

@Serializable
internal data class CodexTelemetrySnapshot(
    val schemaVersion: Int,
    val source: String,
    val generatedAtEpochSeconds: Long,
    val currentContext: CodexContextDto? = null,
    val tokenUsage: CodexTokenUsageDto
)

@Serializable
internal data class CodexContextDto(
    val capturedAtEpochSeconds: Long,
    val model: String,
    val usedTokens: Long,
    val contextWindowTokens: Long,
    val sessionTokens: Long
)

@Serializable
internal data class CodexTokenUsageDto(
    val today: CodexTokenTotalsDto,
    val last7Days: CodexTokenTotalsDto,
    val last30Days: CodexTokenTotalsDto,
    val daily: List<CodexDailyTokenUsageDto>,
    val models: List<CodexModelTokenUsageDto>
)

@Serializable
internal data class CodexTokenTotalsDto(
    val inputTokens: Long,
    val cachedInputTokens: Long,
    val outputTokens: Long,
    val reasoningOutputTokens: Long,
    val totalTokens: Long
)

@Serializable
internal data class CodexDailyTokenUsageDto(
    val date: String,
    val inputTokens: Long,
    val cachedInputTokens: Long,
    val outputTokens: Long,
    val reasoningOutputTokens: Long,
    val totalTokens: Long
)

@Serializable
internal data class CodexModelTokenUsageDto(
    val model: String,
    val inputTokens: Long,
    val cachedInputTokens: Long,
    val outputTokens: Long,
    val reasoningOutputTokens: Long,
    val totalTokens: Long
)
