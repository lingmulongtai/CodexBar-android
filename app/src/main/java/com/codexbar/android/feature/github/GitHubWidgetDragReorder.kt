package com.codexbar.android.feature.github

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import com.codexbar.android.core.domain.model.GitHubWidget
import com.codexbar.android.ui.components.providerEdgeScrollDelta
import kotlinx.coroutines.delay

/** Mirrors the usage dashboard's pointer-following and edge-scroll behavior. */
internal class GitHubWidgetDragReorder(
    private val grid: LazyGridState,
    private val widgets: () -> List<GitHubWidget>,
    private val onChange: (List<GitHubWidget>) -> Unit,
    private val density: Float
) {
    var dragging by mutableStateOf<String?>(null)
        private set
    private var pointer by mutableStateOf(Offset.Zero)
    private var grabbedAt = Offset.Zero
    private fun item(key: String) = grid.layoutInfo.visibleItemsInfo.find { it.key == key }

    fun start(key: String, offset: Offset) {
        val item = item(key) ?: return
        dragging = key; grabbedAt = offset
        pointer = Offset(item.offset.x.toFloat(), item.offset.y.toFloat()) + offset
    }
    fun drag(delta: Offset) { pointer += delta; moveAtPointer() }
    fun stop() { dragging = null }
    fun translation(key: String): Offset {
        if (dragging != key) return Offset.Zero
        val item = item(key) ?: return Offset.Zero
        return pointer - grabbedAt - Offset(item.offset.x.toFloat(), item.offset.y.toFloat())
    }
    private fun moveAtPointer() {
        val from = dragging ?: return
        val target = grid.layoutInfo.visibleItemsInfo.firstOrNull {
            Rect(Offset(it.offset.x.toFloat(), it.offset.y.toFloat()), it.size.toSize()).contains(pointer)
        }?.key as? String ?: return
        val list = widgets().toMutableList()
        val a = list.indexOfFirst { it.key == from }; val b = list.indexOfFirst { it.key == target }
        if (a >= 0 && b >= 0 && a != b) { list.add(b, list.removeAt(a)); onChange(list) }
    }
    suspend fun scrollAtEdge() {
        val info = grid.layoutInfo
        val delta = providerEdgeScrollDelta(pointer.y, info.viewportStartOffset, info.viewportEndOffset, density)
        if (delta != 0f) { grid.scrollBy(delta); moveAtPointer() }
    }
}

@Composable
internal fun rememberGitHubWidgetDragReorder(grid: LazyGridState, widgets: List<GitHubWidget>,
    onChange: (List<GitHubWidget>) -> Unit): GitHubWidgetDragReorder {
    val currentWidgets = rememberUpdatedState(widgets)
    val currentChange = rememberUpdatedState(onChange)
    val density = LocalDensity.current.density
    val state = remember(grid, density) { GitHubWidgetDragReorder(grid, { currentWidgets.value }, { currentChange.value(it) }, density) }
    LaunchedEffect(widgets) { if (state.dragging !in widgets.map { it.key }) state.stop() }
    LaunchedEffect(state.dragging) { while (state.dragging != null) { state.scrollAtEdge(); delay(16) } }
    return state
}

@Composable
internal fun Modifier.gitHubWidgetDragReorder(state: GitHubWidgetDragReorder, key: String): Modifier {
    val haptic = LocalHapticFeedback.current
    return this.zIndex(if (state.dragging == key) 1f else 0f).graphicsLayer {
        val offset = state.translation(key)
        translationX = offset.x; translationY = offset.y
        shadowElevation = if (state.dragging == key) 12f else 0f
    }.pointerInput(key, state) {
        detectDragGesturesAfterLongPress(
            onDragStart = { state.start(key, it); haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
            onDragEnd = state::stop, onDragCancel = state::stop,
            onDrag = { change, amount -> change.consume(); state.drag(amount) })
    }
}
