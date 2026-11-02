package com.codexbar.android.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class GitHubContributionsSession(val ownerId: Long, val token: GitHubDashboardSession) {
    override fun toString(): String = "GitHubContributionsSession(redacted)"
}
