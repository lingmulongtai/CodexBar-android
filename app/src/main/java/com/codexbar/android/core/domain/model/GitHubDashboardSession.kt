package com.codexbar.android.core.domain.model

import kotlinx.serialization.Serializable

/** Credentials for the user's dashboard, separate from the Copilot provider. */
@Serializable
data class GitHubDashboardSession(
    val clientId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long
) {
    override fun toString(): String = "GitHubDashboardSession(redacted)"
}
