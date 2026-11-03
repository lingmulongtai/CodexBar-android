package com.codexbar.android

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import kotlin.math.abs
import kotlin.math.roundToInt

internal val LocalTopLevelSwipeEnabled = staticCompositionLocalOf<MutableState<Boolean>?> { null }
internal val LocalPageActive = staticCompositionLocalOf { true }
internal val ProgrammaticPageScrollConnection = object : NestedScrollConnection {}

internal data class PageLocation(val tab: Int = 0, val pane: Int = 0) {
    fun back(): PageLocation? = when {
        pane > 0 -> copy(pane = pane - 1)
        tab != 0 -> copy(tab = 0)
        else -> null
    }
}

/** Handle Back against the requested pane even before it crosses the middle of the viewport. */
internal fun backPageLocation(current: PageLocation, requested: PageLocation?, moving: Boolean): PageLocation? =
    requested?.back() ?: current.back() ?: if (requested != null || moving) PageLocation() else null

internal fun initialPageLocation(route: String) = when (route) {
    "history", "spend" -> PageLocation(tab = 1)
    "connections" -> PageLocation(tab = 2)
    "github" -> PageLocation(tab = 3)
    "settings" -> PageLocation(pane = 1)
    "developer-settings" -> PageLocation(pane = 2)
    else -> PageLocation()
}

/** animateScrollToPage pre-jumps distant pages. Scroll the complete track instead, with no snap on cancellation. */
internal suspend fun PagerState.animateContinuouslyToPage(page: Int) {
    scroll {
        val destination = page.coerceIn(0, pageCount - 1)
        updateTargetPage(destination)
        val pages = getOffsetDistanceInPages(destination)
        val distance = pages * (layoutInfo.pageSize + layoutInfo.pageSpacing)
        var consumed = 0f
        if (abs(distance) > .5f) animate(0f, distance,
            animationSpec = tween((abs(pages) * 180).roundToInt().coerceIn(320, 600))) { position, _ ->
            consumed += scrollBy(position - consumed)
        }
        scrollBy(getOffsetDistanceInPages(destination) * (layoutInfo.pageSize + layoutInfo.pageSpacing))
    }
}
