package com.codexbar.android.ui.components

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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.AiService
import kotlinx.coroutines.delay

/** Edge detection and scrolling use dp so a dense screen behaves like a low-density one. */
internal fun providerEdgeScrollDelta(pointerY: Float, start: Int, end: Int, density: Float): Float {
    val edge = 72f * density
    return when {
        pointerY < start + edge -> -6f * density
        pointerY > end - edge -> 6f * density
        else -> 0f
    }
}

/** Uses layout offsets rather than translated bounds, so crossing a row cannot feed back into dragging. */
internal class ProviderDragReorder(
    val grid: LazyGridState,
    private val services: () -> List<AiService>,
    private val onChange: (List<AiService>) -> Unit,
    private val density: Float
) {
    var dragging by mutableStateOf<AiService?>(null)
        private set
    private var pointer by mutableStateOf(Offset.Zero)
    private var grabbedAt = Offset.Zero

    private fun item(service: AiService) = grid.layoutInfo.visibleItemsInfo.find { it.key == service.name }

    fun start(service: AiService, offset: Offset) {
        val item = item(service) ?: return
        dragging = service
        grabbedAt = offset
        pointer = Offset(item.offset.x.toFloat(), item.offset.y.toFloat()) + offset
    }

    fun drag(delta: Offset) { pointer += delta; moveAtPointer() }
    fun stop() { dragging = null }

    fun translation(service: AiService): Offset {
        if (dragging != service) return Offset.Zero
        val item = item(service) ?: return Offset.Zero
        return pointer - grabbedAt - Offset(item.offset.x.toFloat(), item.offset.y.toFloat())
    }

    fun moveAtPointer() {
        val from = dragging ?: return
        val target = grid.layoutInfo.visibleItemsInfo.firstOrNull {
            Rect(Offset(it.offset.x.toFloat(), it.offset.y.toFloat()), it.size.toSize()).contains(pointer)
        }?.key as? String ?: return
        services().find { it.name == target && it != from }?.let { move(from, it) }
    }

    fun move(from: AiService, target: AiService) {
        val order = services().distinct().toMutableList()
        val fromIndex = order.indexOf(from)
        val targetIndex = order.indexOf(target)
        if (fromIndex < 0 || targetIndex < 0 || fromIndex == targetIndex) return
        order.add(targetIndex, order.removeAt(fromIndex))
        onChange(order)
    }

    suspend fun scrollAtEdge() {
        val info = grid.layoutInfo
        val delta = providerEdgeScrollDelta(pointer.y, info.viewportStartOffset, info.viewportEndOffset, density)
        if (delta != 0f) { grid.scrollBy(delta); moveAtPointer() }
    }
}

@Composable
internal fun rememberProviderDragReorder(
    grid: LazyGridState,
    services: List<AiService>,
    onChange: (List<AiService>) -> Unit
): ProviderDragReorder {
    val currentServices = rememberUpdatedState(services)
    val currentChange = rememberUpdatedState(onChange)
    val density = LocalDensity.current.density
    val state = remember(grid, density) { ProviderDragReorder(grid, { currentServices.value }, { currentChange.value(it) }, density) }
    LaunchedEffect(services) { if (state.dragging !in services) state.stop() }
    LaunchedEffect(state.dragging) {
        while (state.dragging != null) { state.scrollAtEdge(); delay(16) }
    }
    return state
}

@Composable
internal fun Modifier.providerDragReorder(state: ProviderDragReorder, service: AiService, services: List<AiService>): Modifier {
    val haptic = LocalHapticFeedback.current
    val up = stringResource(R.string.provider_order_move_up, service.displayName)
    val down = stringResource(R.string.provider_order_move_down, service.displayName)
    val index = services.indexOf(service)
    return this
        .zIndex(if (state.dragging == service) 1f else 0f)
        .graphicsLayer {
            val offset = state.translation(service)
            translationX = offset.x; translationY = offset.y
            shadowElevation = if (state.dragging == service) 12f else 0f
        }
        .semantics {
            customActions = buildList {
                if (index > 0) add(CustomAccessibilityAction(up) { state.move(service, services[index - 1]); true })
                if (index in 0 until services.lastIndex) add(CustomAccessibilityAction(down) { state.move(service, services[index + 1]); true })
            }
        }
        .pointerInput(service, state) {
            detectDragGesturesAfterLongPress(
                onDragStart = { state.start(service, it); haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                onDragEnd = state::stop,
                onDragCancel = state::stop,
                onDrag = { change, amount -> change.consume(); state.drag(amount) }
            )
        }
}
