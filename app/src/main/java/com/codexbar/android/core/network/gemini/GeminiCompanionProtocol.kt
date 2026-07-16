package com.codexbar.android.core.network.gemini

import kotlinx.serialization.Serializable

@Serializable
internal data class GeminiCompanionRequest(
    val protocolVersion: Int,
    val companionId: String,
    val requestedAtEpochSeconds: Long,
    val nonce: String,
    val signature: String
)

@Serializable
internal data class GeminiCompanionEnvelope(
    val protocolVersion: Int,
    val companionId: String,
    val requestNonce: String,
    val sentAtEpochSeconds: Long,
    val iv: String,
    val ciphertext: String
)

@Serializable
data class GeminiCompanionSnapshot(
    val schemaVersion: Int,
    val source: String,
    val generatedAtEpochSeconds: Long,
    val cliVersion: String,
    val tier: String? = null,
    val windows: List<GeminiCompanionWindow>
)

@Serializable
data class GeminiCompanionWindow(
    val label: String,
    val usedFraction: Double,
    val resetsAtEpochSeconds: Long? = null
)
