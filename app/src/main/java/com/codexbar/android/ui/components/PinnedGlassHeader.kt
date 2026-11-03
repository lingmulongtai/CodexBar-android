package com.codexbar.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

/** Pages keep their own header measurements; only the selected header is drawn above the track. */
internal class PinnedGlassHeader {
    var content by mutableStateOf<(@Composable () -> Unit)?>(null)
}

internal val LocalPinnedGlassHeader = staticCompositionLocalOf<PinnedGlassHeader?> { null }

@Composable
internal fun PinnedGlassHeaderLayout(header: PinnedGlassHeader, content: @Composable () -> Unit) {
    val backdrop = rememberGlassBackdrop()
    val wallpaper = LocalGlassCardBackdrop.current
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().glassBackdropSource(backdrop, wallpaper)) { content() }
        CompositionLocalProvider(LocalGlassHeaderBackdrop provides backdrop) {
            Box(Modifier.fillMaxWidth()) { header.content?.invoke() }
        }
    }
}
