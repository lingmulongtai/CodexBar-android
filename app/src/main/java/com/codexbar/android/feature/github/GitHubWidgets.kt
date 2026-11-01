package com.codexbar.android.feature.github

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
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

/** Widget content remains visible while its long-press reorder and resize handles are active. */
@Composable
internal fun GitHubWidgetFrame(widget: GitHubWidget, editing: Boolean, bounds: MutableMap<String, Rect>,
    onMove: (String, String) -> Unit, onResize: (GitHubWidgetSize) -> Unit, onRemove: () -> Unit,
    onDragPosition: (String, Offset?) -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val resizeStep = with(LocalDensity.current) { 30.dp.toPx() }
    var pointer by remember(widget.key) { mutableStateOf(Offset.Zero) }
    val currentMove by rememberUpdatedState(onMove)
    val currentSize by rememberUpdatedState(widget.size)
    val currentResize by rememberUpdatedState(onResize)
    Box(modifier.onGloballyPositioned { coordinates ->
        val origin = coordinates.positionInWindow()
        bounds[widget.key] = Rect(origin, androidx.compose.ui.geometry.Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
    }.then(if (editing) Modifier.border(1.dp, Color.White.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
        .pointerInput(widget.key) {
            detectDragGesturesAfterLongPress(onDragStart = { offset ->
                pointer = (bounds[widget.key]?.topLeft ?: Offset.Zero) + offset
                onDragPosition(widget.key, pointer)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }, onDragEnd = { onDragPosition(widget.key, null) }, onDragCancel = { onDragPosition(widget.key, null) },
            onDrag = { change, delta ->
                change.consume(); pointer += delta
                onDragPosition(widget.key, pointer)
                bounds.entries.firstOrNull { it.key != widget.key && it.value.contains(pointer) }?.let { currentMove(widget.key, it.key) }
            })
        } else Modifier)) {
        content()
        if (editing) {
            IconButtonGlass(onClick = onRemove, modifier = Modifier.align(Alignment.TopStart),
                description = stringResource(R.string.github_widgets_remove))
            if (widget.kind == GitHubWidgetKind.CALENDAR || widget.kind == GitHubWidgetKind.REPOSITORY) {
                Box(Modifier.align(Alignment.BottomEnd).size(44.dp).pointerInput(widget.key) {
                    var delta = 0f
                    detectDragGestures(onDragStart = { delta = 0f }, onDrag = { change, amount ->
                        change.consume(); delta += (amount.x + amount.y) / 2f
                        if (delta >= resizeStep || delta <= -resizeStep) {
                            val direction = if (delta > 0) 1 else -1
                            val sizes = GitHubWidgetSize.entries
                            val next = (sizes.indexOf(currentSize) + direction).coerceIn(0, sizes.lastIndex)
                            currentResize(sizes[next]); delta = 0f
                        }
                    })
                }, contentAlignment = Alignment.BottomEnd) {
                    Canvas(Modifier.size(28.dp).padding(6.dp)) {
                        drawLine(Color.White, Offset(size.width * .25f, size.height), Offset(size.width, size.height * .25f),
                            strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(Color.White, Offset(size.width * .58f, size.height), Offset(size.width, size.height * .58f),
                            strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
            }
        }
    }
    DisposableEffect(widget.key) { onDispose { bounds.remove(widget.key) } }
}

@Composable
private fun IconButtonGlass(onClick: () -> Unit, modifier: Modifier, description: String) {
    androidx.compose.material3.IconButton(onClick = onClick, modifier = modifier.size(36.dp)
        .padding(5.dp).clip(CircleShape).background(Color(0xDD20242B))) {
        Icon(Icons.Rounded.Remove, contentDescription = description, tint = Color.White, modifier = Modifier.size(19.dp))
    }
}

@Composable
internal fun GitHubWidgetPicker(state: GitHubDashboardState, headerHeight: Dp,
    onChange: (List<GitHubWidget>) -> Unit, onAdded: () -> Unit) {
    val widgets = state.layout?.widgets.orEmpty()
    var searching by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    val available = remember(state.repositories, widgets, search) {
        if (search.isBlank()) emptyList() else {
            val pinned = widgets.mapNotNull { it.repositoryId }.toSet()
            state.repositories.filter { it.id !in pinned && it.fullName.contains(search.trim(), ignoreCase = true) }.take(100)
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
        top = headerHeight + 8.dp, bottom = LocalBottomChromeInset.current + 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!searching) {
            item { CodexBarButton(onClick = { searching = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.github_widgets_add_repository))
            } }
            val missing = listOf(GitHubWidgetKind.PROFILE, GitHubWidgetKind.COMMITS, GitHubWidgetKind.CALENDAR, GitHubWidgetKind.ACTIVITY)
                .filter { kind -> widgets.none { it.kind == kind } }
            items(missing, key = { it.name }) { kind ->
                CodexBarCard(onClick = { onChange(widgets + GitHubWidget(kind)); onAdded() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(when (kind) {
                        GitHubWidgetKind.PROFILE -> R.string.github_widgets_profile
                        GitHubWidgetKind.COMMITS -> R.string.github_widgets_commits
                        GitHubWidgetKind.CALENDAR -> R.string.github_widgets_calendar
                        else -> R.string.github_widgets_activity
                    }), modifier = Modifier.padding(18.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
        } else {
            item { CodexBarOutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.github_widgets_search)) }, singleLine = true) }
            if (search.isNotBlank() && available.isEmpty()) item { Text(stringResource(R.string.github_widgets_no_results)) }
            items(available, key = { it.id }) { repo ->
                CodexBarCard(onClick = { onChange(widgets + GitHubWidget(GitHubWidgetKind.REPOSITORY, repo.id)); onAdded() },
                    modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(repo.fullName, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.github_widgets_add))
                    }
                }
            }
        }
        item { WallpaperFooter() }
    }
}
