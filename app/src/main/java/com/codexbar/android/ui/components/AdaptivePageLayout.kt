package com.codexbar.android.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor

/** Widths describe the available app window, including split-screen and unfolded displays. */
internal object AdaptivePageLayout {
    val overviewWidth = 1440.dp
    val formWidth = 1120.dp
    val readingWidth = 1040.dp
    const val detailWidthDp = 384f
}

internal val LocalAppWindowHeight = staticCompositionLocalOf { Dp.Infinity }

/** Keep the glass chrome full width while centering its readable content independently. */
internal fun Modifier.pageContentWidth(maxWidth: Dp = AdaptivePageLayout.overviewWidth): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = maxWidth).fillMaxWidth()

internal fun adaptiveCardColumns(
    widthDp: Float,
    minimumCardWidthDp: Float,
    horizontalPaddingDp: Float = 32f,
    gapDp: Float = 12f,
    maximum: Int = 3
): Int = floor((widthDp - horizontalPaddingDp + gapDp) / (minimumCardWidthDp + gapDp))
    .toInt().coerceIn(1, maximum)
