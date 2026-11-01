package com.codexbar.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.codexbar.android.core.domain.model.AppThemeStyle
import com.codexbar.android.ui.theme.LocalCodexBarThemeProfile

/**
 * A screen title in the design's voice: Expressive's top app bar, Liquid Glass's large title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemedTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    val profile = LocalCodexBarThemeProfile.current
    when (profile.style) {
        AppThemeStyle.MATERIAL_3 -> TopAppBar(
            title = {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = profile.topBarContainerColor,
                scrolledContainerColor = profile.topBarContainerColor
            )
        )
        AppThemeStyle.LIQUID_GLASS -> GlassPageHeader(title, modifier, navigationIcon, actions = actions)

    }
}

/** A fixed, bottom-rounded header sampling only the separate wallpaper backdrop. */
@Composable
fun GlassPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    subtitle: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
    val sharedHeight = LocalGlassHeaderHeight.current
    val direction = LocalTopLevelSlideDirection.current
    var naturalHeight by remember { mutableIntStateOf(0) }
    val onNaturalHeight = LocalGlassHeaderNaturalHeight.current
    val contentAlpha = remember { Animatable(if (direction == 0) 1f else 0f) }
    LaunchedEffect(contentAlpha) { contentAlpha.animateTo(1f, tween(200)) }
    LaunchedEffect(sharedHeight, naturalHeight) {
        if (sharedHeight != null && naturalHeight > 0) {
            if (sharedHeight.value <= 0f) sharedHeight.snapTo(naturalHeight.toFloat())
            else sharedHeight.animateTo(naturalHeight.toFloat(), tween(240))
        }
    }
    GlassSurface(
        backdrop = LocalGlassHeaderBackdrop.current ?: LocalGlassCardBackdrop.current,
        shape = shape,
        tint = Color.Transparent,
        fallback = Color.Transparent,
        rim = Color.White.copy(alpha = 0.13f),
        blurRadius = 0.dp, refraction = 0.45f, dispersion = 0.4f,
        // Glisse's reach is a fraction of the full short side; the native rim band is one quarter.
        allowAsymmetricCorners = true, bendReachScale = 4f, shaderRimShine = 0f, saturation = 1f, elevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(Modifier.matchParentSize().clip(shape).background(Brush.verticalGradient(
            0f to Color.Black,
            0.32f to Color.Black.copy(alpha = 0.98f),
            0.70f to Color.Black.copy(alpha = 0.76f),
            1f to Color.Black.copy(alpha = 0.16f)
        )))
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Row(
                Modifier.fillMaxWidth().clipToBounds().layout { measurable, constraints ->
                    val child = measurable.measure(constraints.copy(minHeight = 0))
                    val animated = sharedHeight?.value?.takeIf { it > 0f }?.roundToInt() ?: child.height
                    layout(child.width, constraints.constrainHeight(animated)) { child.placeRelative(0, 0) }
                }.onSizeChanged { naturalHeight = it.height; onNaturalHeight(it.height) }
                    .statusBarsPadding().heightIn(min = 72.dp)
                    .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.graphicsLayer { alpha = contentAlpha.value }) { navigationIcon() }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    subtitle?.invoke()
                }
                Row(Modifier.graphicsLayer { alpha = contentAlpha.value }) { actions() }
            }
        }
    }
}
