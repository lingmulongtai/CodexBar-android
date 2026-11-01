package com.codexbar.android.feature.github

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.GitHubWidget
import com.codexbar.android.core.domain.model.GitHubWidgetKind
import com.codexbar.android.core.domain.model.GitHubWidgetSize
import com.codexbar.android.core.network.github.GitHubRepository
import com.codexbar.android.ui.components.*

@Composable
internal fun GitHubPinnedRepository(repo: GitHubRepository, size: GitHubWidgetSize, open: (String) -> Unit) {
    CodexBarCard(onClick = { open(repo.htmlUrl) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(if (size == GitHubWidgetSize.SMALL) 12.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(repo.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = if (size == GitHubWidgetSize.SMALL) 1 else 2, overflow = TextOverflow.Ellipsis)
            if (size != GitHubWidgetSize.SMALL) Text("★ ${repo.stars}" + (repo.language?.let { "  ·  $it" } ?: ""),
                style = MaterialTheme.typography.labelMedium)
            if (size == GitHubWidgetSize.LARGE) repo.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun GitHubWidgetEditor(state: GitHubDashboardState, headerHeight: Dp, onChange: (List<GitHubWidget>) -> Unit) {
    val widgets = state.layout?.widgets.orEmpty()
    val currentWidgets by rememberUpdatedState(widgets)
    val currentChange by rememberUpdatedState(onChange)
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    var search by remember { mutableStateOf("") }
    fun move(key: String, direction: Int) {
        val list = currentWidgets.toMutableList()
        val from = list.indexOfFirst { it.key == key }
        val to = from + direction
        if (from >= 0 && to in list.indices) { list.add(to, list.removeAt(from)); currentChange(list) }
    }
    val available = remember(state.repositories, widgets, search) {
        val pinned = widgets.mapNotNull { it.repositoryId }.toSet()
        state.repositories.filter { it.id !in pinned && it.fullName.contains(search.trim(), ignoreCase = true) }.take(100)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
        top = headerHeight + 8.dp, bottom = LocalBottomChromeInset.current + 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text(stringResource(R.string.github_widgets_hint), style = MaterialTheme.typography.bodyMedium) }
        items(widgets, key = { it.key }) { widget ->
            val index = widgets.indexOfFirst { it.key == widget.key }
            val name = when (widget.kind) {
                GitHubWidgetKind.PROFILE -> stringResource(R.string.github_widgets_profile)
                GitHubWidgetKind.COMMITS -> stringResource(R.string.github_widgets_commits)
                GitHubWidgetKind.CALENDAR -> stringResource(R.string.github_widgets_calendar)
                GitHubWidgetKind.ACTIVITY -> stringResource(R.string.github_widgets_activity)
                GitHubWidgetKind.REPOSITORY -> state.repositories.find { it.id == widget.repositoryId }?.fullName
                    ?: stringResource(R.string.github_widgets_missing)
            }
            CodexBarCard(Modifier.fillMaxWidth().animateItem().pointerInput(widget.key) {
                var delta = 0f
                detectDragGesturesAfterLongPress(onDragStart = { delta = 0f; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                    onDrag = { change, amount ->
                        change.consume(); delta += amount.y
                        if (delta >= threshold) { move(widget.key, 1); delta -= threshold }
                        else if (delta <= -threshold) { move(widget.key, -1); delta += threshold }
                    })
            }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.DragHandle, contentDescription = null)
                    Text(name, Modifier.weight(1f).padding(horizontal = 8.dp), maxLines = 2,
                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    CodexBarIconButton(onClick = { move(widget.key, -1) }, enabled = index > 0) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = stringResource(R.string.provider_order_move_up, name))
                    }
                    CodexBarIconButton(onClick = { move(widget.key, 1) }, enabled = index < widgets.lastIndex) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.provider_order_move_down, name))
                    }
                    CodexBarIconButton(onClick = { currentChange(currentWidgets.filterNot { it.key == widget.key }) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.github_widgets_remove))
                    }
                }
                if (widget.kind == GitHubWidgetKind.REPOSITORY || widget.kind == GitHubWidgetKind.CALENDAR) Row(Modifier.padding(start = 32.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GitHubWidgetSize.entries.forEach { size ->
                        CodexBarFilterChip(selected = widget.size == size, onClick = {
                            currentChange(currentWidgets.map { if (it.key == widget.key) it.copy(size = size) else it })
                        }, label = { Text(stringResource(when (size) {
                            GitHubWidgetSize.SMALL -> R.string.github_widgets_small
                            GitHubWidgetSize.MEDIUM -> R.string.github_widgets_medium
                            GitHubWidgetSize.LARGE -> R.string.github_widgets_large
                        })) })
                    }
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(GitHubWidgetKind.PROFILE, GitHubWidgetKind.COMMITS, GitHubWidgetKind.CALENDAR, GitHubWidgetKind.ACTIVITY)
                    .filter { kind -> widgets.none { it.kind == kind } }.forEach { kind ->
                        CodexBarFilterChip(selected = false, onClick = { onChange(widgets + GitHubWidget(kind)) }, label = {
                            Text(stringResource(when (kind) {
                                GitHubWidgetKind.PROFILE -> R.string.github_widgets_profile
                                GitHubWidgetKind.COMMITS -> R.string.github_widgets_commits
                                GitHubWidgetKind.CALENDAR -> R.string.github_widgets_calendar
                                else -> R.string.github_widgets_activity
                            }))
                        })
                    }
            }
        }
        item { Text(stringResource(R.string.github_widgets_add_repository), style = MaterialTheme.typography.titleMedium) }
        item { CodexBarOutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.github_widgets_search)) }, singleLine = true) }
        if (available.isEmpty()) item { Text(stringResource(R.string.github_widgets_no_results)) }
        items(available, key = { "available:${it.id}" }) { repo ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(repo.fullName, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                CodexBarIconButton(onClick = { onChange(widgets + GitHubWidget(GitHubWidgetKind.REPOSITORY, repo.id)) }) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.github_widgets_add))
                }
            }
        }
        item { WallpaperFooter() }
    }
}
