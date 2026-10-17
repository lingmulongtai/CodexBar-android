package com.codexbar.android.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Height that floating navigation covers at the bottom; scrolling screens pad by it. */
val LocalBottomChromeInset = compositionLocalOf<Dp> { 0.dp }
