package com.codexbar.android.core.domain.model

import java.time.Instant
import java.time.LocalDate

data class CodexTelemetryCredential(
    val host: String,
    val port: Int,
    val companionId: String,
    val sharedKeyBase64Url: String
)

data class CodexTelemetry(
    val generatedAt: Instant,
    val currentContext: CodexContextUsage?,
    val tokenUsage: CodexTokenUsage
)

data class CodexContextUsage(
    val capturedAt: Instant,
    val model: String,
    val usedTokens: Long,
    val contextWindowTokens: Long,
    val sessionTokens: Long
)

data class CodexTokenUsage(
    val today: CodexTokenTotals,
    val last7Days: CodexTokenTotals,
    val last30Days: CodexTokenTotals,
    val daily: List<CodexDailyTokenUsage>,
    val models: List<CodexModelTokenUsage>
)

data class CodexTokenTotals(
    val inputTokens: Long,
    val cachedInputTokens: Long,
    val outputTokens: Long,
    val reasoningOutputTokens: Long,
    val totalTokens: Long
)

data class CodexDailyTokenUsage(
    val date: LocalDate,
    val totals: CodexTokenTotals
)

data class CodexModelTokenUsage(
    val model: String,
    val totals: CodexTokenTotals
)
