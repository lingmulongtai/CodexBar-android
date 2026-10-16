package com.codexbar.android.core.network.desktop

import kotlinx.serialization.Serializable

@Serializable data class DesktopRemoteSettings(
    val allowed: Boolean, val revision: String, val refreshIntervalSecs: Long,
    val adaptiveRefresh: Boolean, val reportingPeriod: String
)
@Serializable data class DesktopRemoteSettingsResult(val status: String, val settings: DesktopRemoteSettings)
@Serializable internal data class DesktopControlRequest(
    val protocolVersion: Int, val companionId: String, val requestedAtEpochSeconds: Long,
    val nonce: String, val resource: String, val payload: String, val signature: String
)
@Serializable data class DesktopRemoteSettingsUpdate(
    val expectedRevision: String, val refreshIntervalSecs: Long? = null, val reportingPeriod: String? = null
)

internal fun DesktopRemoteSettingsResult.validate(): DesktopRemoteSettingsResult {
    if (status !in setOf("ok", "disabled", "conflict", "invalid", "unavailable") ||
        !settings.revision.matches(Regex("[a-f0-9]{64}")) || settings.refreshIntervalSecs !in 0..86400 ||
        !(settings.reportingPeriod in setOf("all", "month-to-date") || settings.reportingPeriod.removePrefix("rolling:")
            .toIntOrNull()?.let { settings.reportingPeriod == "rolling:$it" && it in 1..365 } == true)) {
        throw DesktopBridgeProtocolException("Invalid Windows settings response")
    }
    return this
}
