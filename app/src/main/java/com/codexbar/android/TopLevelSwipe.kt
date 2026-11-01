package com.codexbar.android

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal val LocalTopLevelSwipeEnabled = staticCompositionLocalOf<MutableState<Boolean>?> { null }

internal fun swipeTarget(route: String, horizontal: Float, vertical: Float, threshold: Float, rtl: Boolean): String? {
    val routes = listOf("dashboard", "history", "connections", "github")
    val current = routes.indexOf(if (route == "spend") "history" else route)
    if (current < 0 || abs(horizontal) < threshold || abs(horizontal) < abs(vertical) * 1.5f) return null
    val direction = if ((horizontal < 0) != rtl) 1 else -1
    return routes.getOrNull(current + direction)
}

/** Observe descendants first, so sliders, segmented controls and drag handles keep their gestures. */
internal fun Modifier.topLevelSwipe(route: String, rtl: Boolean, enabled: Boolean, navigate: (String) -> Unit): Modifier =
    if (!enabled) this else pointerInput(route, rtl, enabled) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            var distance = Offset.Zero
            var blocked = false
            var target: String? = null
            val threshold = maxOf(60.dp.toPx(), viewConfiguration.touchSlop * 2)
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val delta = change.positionChangeIgnoreConsumed()
                val consumedByChild = change.isConsumed
                if (event.changes.any { it.id != down.id && it.pressed }) { blocked = true; target = null }
                if (target == null && consumedByChild && delta != Offset.Zero) blocked = true
                distance += delta
                if (!blocked && target == null) target = swipeTarget(route, distance.x, distance.y, threshold, rtl)
                if (target != null) change.consume()
                if (!change.pressed) {
                    val completed = swipeTarget(route, distance.x, distance.y, threshold, rtl)
                    if (!blocked && !consumedByChild && change.previousPressed && completed == target) completed?.let(navigate)
                    break
                }
            }
        }
    }
