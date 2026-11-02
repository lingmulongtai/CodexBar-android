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
    @Test fun `calendar scale is continuous bounded and survives persistence`() {
        val widget = GitHubWidget(GitHubWidgetKind.CALENDAR, calendarScale = 0.47f)
        val layout = GitHubDashboardLayout(10, listOf(widget))
        val restored = Json.decodeFromString<GitHubDashboardLayout>(Json.encodeToString(layout)).normalized(10)
        assertEquals(0.47f, restored.widgets.single().effectiveCalendarScale, 0.0001f)
        assertEquals(0.42f, widget.copy(calendarScale = 0.01f).effectiveCalendarScale, 0.0001f)
        assertEquals(1.6f, widget.copy(calendarScale = 9f).effectiveCalendarScale, 0.0001f)
    }
    @Test fun `different account and duplicate or invalid pins cannot expose a previous layout`() {
        val old = GitHubDashboardLayout(10, listOf(GitHubWidget(GitHubWidgetKind.REPOSITORY, 42)))
        assertEquals(GitHubDashboardLayout.default(11), old.normalized(11))
        val broken = GitHubDashboardLayout(10, listOf(GitHubWidget(GitHubWidgetKind.REPOSITORY, 0),
            GitHubWidget(GitHubWidgetKind.REPOSITORY, 42), GitHubWidget(GitHubWidgetKind.REPOSITORY, 42)))
        assertEquals(listOf(GitHubWidget(GitHubWidgetKind.REPOSITORY, 42)), broken.normalized(10).widgets)
    }
}
