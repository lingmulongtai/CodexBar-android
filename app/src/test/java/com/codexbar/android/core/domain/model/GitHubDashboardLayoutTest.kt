package com.codexbar.android.core.domain.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class GitHubDashboardLayoutTest {
    @Test fun `layout round trip keeps pinned repository size and order`() {
        val layout = GitHubDashboardLayout(10, listOf(
            GitHubWidget(GitHubWidgetKind.CALENDAR, size = GitHubWidgetSize.LARGE),
            GitHubWidget(GitHubWidgetKind.REPOSITORY, 42, GitHubWidgetSize.SMALL),
            GitHubWidget(GitHubWidgetKind.COMMITS)))
        assertEquals(layout, Json.decodeFromString<GitHubDashboardLayout>(Json.encodeToString(layout)).normalized(10))
    }
    @Test fun `different account and duplicate or invalid pins cannot expose a previous layout`() {
        val old = GitHubDashboardLayout(10, listOf(GitHubWidget(GitHubWidgetKind.REPOSITORY, 42)))
        assertEquals(GitHubDashboardLayout.default(11), old.normalized(11))
        val broken = GitHubDashboardLayout(10, listOf(GitHubWidget(GitHubWidgetKind.REPOSITORY, 0),
            GitHubWidget(GitHubWidgetKind.REPOSITORY, 42), GitHubWidget(GitHubWidgetKind.REPOSITORY, 42)))
        assertEquals(listOf(GitHubWidget(GitHubWidgetKind.REPOSITORY, 42)), broken.normalized(10).widgets)
    }
}
