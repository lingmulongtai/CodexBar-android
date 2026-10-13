package com.codexbar.android.core.domain.model

data class DesktopBridgeCredential(
    val host: String,
    val port: Int,
    val companionId: String,
    val sharedKeyBase64Url: String
) {
    override fun toString(): String = "DesktopBridgeCredential(<redacted>)"
}
