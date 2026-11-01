package com.codexbar.android.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class GitHubWidgetKind { PROFILE, COMMITS, CALENDAR, ACTIVITY, REPOSITORY }
@Serializable
enum class GitHubWidgetSize { SMALL, MEDIUM, LARGE }

@Serializable
data class GitHubWidget(
    val kind: GitHubWidgetKind,
    val repositoryId: Long? = null,
    val size: GitHubWidgetSize = GitHubWidgetSize.MEDIUM
) {
    val key: String get() = if (kind == GitHubWidgetKind.REPOSITORY) "repo:$repositoryId" else kind.name
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
    val valid = widgets.asSequence().filter { widget ->
        if (widget.kind == GitHubWidgetKind.REPOSITORY) (widget.repositoryId ?: 0L) > 0L
        else widget.repositoryId == null
    }.distinctBy { it.key }.take(40).map { widget ->
        if (widget.kind != GitHubWidgetKind.REPOSITORY) widget.copy(size = GitHubWidgetSize.MEDIUM) else widget
    }.toList()
    return copy(widgets = valid)
}
