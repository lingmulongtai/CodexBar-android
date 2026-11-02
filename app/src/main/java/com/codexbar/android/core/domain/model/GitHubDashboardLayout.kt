package com.codexbar.android.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class GitHubWidgetKind { PROFILE, COMMITS, CALENDAR, ACTIVITY, REPOSITORY, PINNED_REPOSITORIES }
@Serializable
enum class GitHubWidgetSize { SMALL, MEDIUM, LARGE }

@Serializable
data class GitHubWidget(
    val kind: GitHubWidgetKind,
    val repositoryId: Long? = null,
    val size: GitHubWidgetSize = GitHubWidgetSize.MEDIUM,
    val calendarScale: Float? = null,
    val repositoryIds: List<Long> = emptyList()
) {
    val key: String get() = if (kind == GitHubWidgetKind.REPOSITORY) "repo:$repositoryId" else kind.name
    val effectiveCalendarScale: Float get() = calendarScale?.takeIf { it.isFinite() }?.coerceIn(0.42f, 1.6f)
        ?: when (size) { GitHubWidgetSize.SMALL -> 0.83f; GitHubWidgetSize.MEDIUM -> 1f; GitHubWidgetSize.LARGE -> 1.33f }
}

@Serializable
data class GitHubDashboardLayout(val ownerId: Long, val widgets: List<GitHubWidget>) {
    companion object {
        fun default(ownerId: Long) = GitHubDashboardLayout(ownerId, listOf(
            GitHubWidget(GitHubWidgetKind.PROFILE), GitHubWidget(GitHubWidgetKind.COMMITS),
            GitHubWidget(GitHubWidgetKind.CALENDAR), GitHubWidget(GitHubWidgetKind.ACTIVITY)))
    }
}

/** Keep only bounded, valid, unique entries belonging to the current account. */
fun GitHubDashboardLayout.normalized(ownerId: Long): GitHubDashboardLayout {
    if (ownerId <= 0L || this.ownerId != ownerId) return GitHubDashboardLayout.default(ownerId)
    val pinned = widgets.flatMap { widget -> when (widget.kind) {
        GitHubWidgetKind.REPOSITORY -> listOfNotNull(widget.repositoryId)
        GitHubWidgetKind.PINNED_REPOSITORIES -> widget.repositoryIds
        else -> emptyList()
    } }.filter { it > 0 }.distinct().take(40)
    val migrated = widgets.filter { it.kind != GitHubWidgetKind.REPOSITORY || (it.repositoryId ?: 0L) > 0L }.map { if (it.kind == GitHubWidgetKind.REPOSITORY) GitHubWidget(GitHubWidgetKind.PINNED_REPOSITORIES) else it }
    val valid = migrated.asSequence().filter { widget ->
        if (widget.kind == GitHubWidgetKind.REPOSITORY) (widget.repositoryId ?: 0L) > 0L
        else widget.repositoryId == null
    }.distinctBy { it.key }.take(40).map { widget ->
        when (widget.kind) {
            GitHubWidgetKind.CALENDAR -> widget.copy(repositoryIds = emptyList(), calendarScale = widget.calendarScale?.takeIf { it.isFinite() }?.coerceIn(0.42f, 1.6f))
            GitHubWidgetKind.PINNED_REPOSITORIES -> widget.copy(repositoryId = null, repositoryIds = pinned, size = GitHubWidgetSize.MEDIUM, calendarScale = null)
            GitHubWidgetKind.REPOSITORY -> widget.copy(calendarScale = null)
            else -> widget.copy(size = GitHubWidgetSize.MEDIUM, calendarScale = null, repositoryIds = emptyList())
        }
    }.toList()
    return copy(widgets = valid)
}
