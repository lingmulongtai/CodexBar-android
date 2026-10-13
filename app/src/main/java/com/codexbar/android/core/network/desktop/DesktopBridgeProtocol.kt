package com.codexbar.android.core.network.desktop

import kotlinx.serialization.Serializable

@Serializable internal data class DesktopBridgeRequest(
    val protocolVersion: Int, val companionId: String, val requestedAtEpochSeconds: Long,
    val nonce: String, val signature: String
)
@Serializable internal data class DesktopBridgeEnvelope(
    val protocolVersion: Int, val companionId: String, val requestNonce: String,
    val sentAtEpochSeconds: Long, val iv: String, val ciphertext: String
)
@Serializable data class DesktopBridgeSnapshot(
    val schemaVersion: Int, val source: String, val generatedAtEpochSeconds: Long,
    val providers: List<DesktopProvider>
)
@Serializable data class DesktopProvider(
    val id: String, val updatedAtEpochSeconds: Long, val status: String,
    val subjectId: String? = null, val windows: List<DesktopWindow>,
    val budget: DesktopBudget? = null, val balance: DesktopBalance? = null,
    val resetCredits: DesktopResetCredits? = null, val localUsage: DesktopLocalUsageDto? = null
)
@Serializable data class DesktopWindow(
    val label: String, val usedFraction: Double, val resetsAtEpochSeconds: Long? = null,
    val windowDurationSeconds: Long? = null
)
@Serializable data class DesktopBudget(val used: Double, val limit: Double, val currency: String)
@Serializable data class DesktopBalance(val amount: Double, val currency: String)
@Serializable data class DesktopResetCredits(val availableCount: Int, val expiresAtEpochSeconds: List<Long>)

@Serializable data class DesktopLocalUsageDto(
    val updatedAtEpochSeconds: Long, val todayDate: String,
    val todayTokens: Long? = null, val thirtyDayTokens: Long? = null, val periodTokens: Long? = null,
    val todayCostUsd: Double? = null, val thirtyDayCostUsd: Double? = null, val periodCostUsd: Double? = null,
    val reportingPeriod: String? = null, val topModel: String? = null, val incompleteRequestCount: Int? = null
)
