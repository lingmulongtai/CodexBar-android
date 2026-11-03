package com.codexbar.android

import com.codexbar.android.core.domain.model.GlassSurfaceGroup
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.codexbar.android.core.domain.model.AppThemeStyle
import com.codexbar.android.ui.components.BrandGlyph
import com.codexbar.android.ui.components.GlassBackdrop
import com.codexbar.android.ui.components.GlassSurface
import com.codexbar.android.ui.components.glassTabPosition
import com.codexbar.android.ui.components.glassTabDragPosition
import com.codexbar.android.ui.components.glassBackdropSource
import com.codexbar.android.ui.components.rememberGlassBackdrop
import com.codexbar.android.ui.theme.LocalCodexBarThemeProfile
import kotlin.math.roundToInt

internal data class NavigationEntry(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit
)

internal val GlassBarHeight = 60.dp
internal val GlassBarMargin = 12.dp

/** Bottom navigation for phones in the docked designs. */
@Composable
internal fun AppBottomBar(entries: List<NavigationEntry>, modifier: Modifier = Modifier) {
    val profile = LocalCodexBarThemeProfile.current
    NavigationBar(modifier = modifier, containerColor = profile.navigationContainerColor) {
        entries.forEach { entry ->
            NavigationBarItem(
                selected = entry.selected,
                onClick = entry.onClick,
                icon = { Icon(entry.icon, contentDescription = null) },
                label = {
                    Text(
                        entry.label,
                        fontWeight = if (entry.selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                )
            )
        }
    }
}

/** Side navigation for tablets and unfolded phones in the docked designs. */
@Composable
internal fun AppNavigationRail(entries: List<NavigationEntry>, modifier: Modifier = Modifier) {
    val profile = LocalCodexBarThemeProfile.current
    val colors = MaterialTheme.colorScheme
    NavigationRail(
        modifier = modifier.fillMaxHeight().width(96.dp),
        containerColor = profile.navigationContainerColor,
        header = {
            BrandGlyph(profile.style, Modifier.padding(vertical = 16.dp).size(44.dp))
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entries.forEach { entry ->
                NavigationRailItem(
                    selected = entry.selected,
                    onClick = entry.onClick,
                    icon = { Icon(entry.icon, contentDescription = null) },
                    label = { Text(entry.label, maxLines = 1) },
                    colors = NavigationRailItemDefaults.colors(
                        indicatorColor = colors.secondaryContainer,
                        selectedIconColor = colors.onSecondaryContainer
                    )
                )
            }
        }
    }
}

/**
 * Liquid Glass navigation: a floating capsule of tabs and a separate refresh button, both blurring
 * the content that scrolls beneath them. On wide screens the pair stays centered.
 */
@Composable
internal fun GlassNavigationBar(
    entries: List<NavigationEntry>,
    backdrop: GlassBackdrop,
    onRefresh: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val profile = LocalCodexBarThemeProfile.current
    val colors = MaterialTheme.colorScheme
    val glassTint = if (profile.isDark) Color(0xFF282828).copy(alpha = 0.62f) else Color.White.copy(alpha = 0.60f)
    val glassFallback = if (profile.isDark) Color(0xFF1C1C1E).copy(alpha = 0.97f) else Color.White.copy(alpha = 0.97f)
    val rim = if (profile.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)
    Row(
        modifier = modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = GlassBarMargin),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassTabRow(entries, backdrop, glassTint, glassFallback, rim,
            Modifier.weight(1f).height(GlassBarHeight))
        if (onRefresh != null) {
            val refreshLabel = stringResource(R.string.action_refresh)
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val focused by interaction.collectIsFocusedAsState()
            val scale by animateFloatAsState(
                if (pressed) 1.10f else 1f,
                animationSpec = spring(dampingRatio = 0.65f, stiffness = 500f),
                label = "glass_refresh_lift"
            )
            Box(
                modifier = Modifier.size(48.dp)
                    .then(if (focused) Modifier.border(2.dp, colors.primary, CircleShape) else Modifier)
                    .clip(CircleShape)
                    .clickable(interactionSource = interaction, indication = null, onClick = onRefresh)
                    .semantics { role = Role.Button; contentDescription = refreshLabel },
                contentAlignment = Alignment.Center
            ) {
                GlassSurface(
            tuningGroup = GlassSurfaceGroup.NAVIGATION,                    backdrop = backdrop, shape = CircleShape, tint = glassTint,
                    fallback = glassFallback, rim = rim, blurRadius = 3.dp, refraction = 0f,
                    elevation = if (profile.isDark) 0.dp else 1.dp,
                    modifier = Modifier.size(40.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                ) {}
                Icon(Icons.Rounded.Refresh, contentDescription = null, tint = colors.onSurface,
                    modifier = Modifier.size(20.dp))
            }
        }
    }
}


/** The lens refracts the bar and labels without scaling the original controls. */
@Composable
private fun GlassTabRow(
    entries: List<NavigationEntry>, backdrop: GlassBackdrop, glassTint: Color,
    glassFallback: Color, rim: Color, modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) return
    val profile = LocalCodexBarThemeProfile.current
    val colors = MaterialTheme.colorScheme
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val latestEntries by rememberUpdatedState(entries)
    val lensBackdrop = rememberGlassBackdrop()
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    var pressedIndex by remember { mutableStateOf<Int?>(null) }
    var anchor by remember { mutableStateOf(0f) }
    var downX by remember { mutableStateOf(0f) }
    var movement by remember { mutableStateOf(0f) }
    val selectedIndex = entries.indexOfFirst { it.selected }.coerceAtLeast(0)
    val target = dragPosition ?: pressedIndex?.toFloat() ?: selectedIndex.toFloat()
    val settledPosition by animateFloatAsState(target,
        spring(dampingRatio = if (dragPosition != null) 0.94f else 0.92f,
            stiffness = if (dragPosition != null) 2500f else 1024f), label = "glass_tab_position")
    val lift by animateFloatAsState(if (dragPosition != null || pressedIndex != null) 1f else 0f,
        spring(dampingRatio = 0.92f, stiffness = 676f), label = "glass_tab_lift")
    val selectedColor = if (profile.isDark) Color(0xFF0091FF) else Color(0xFF0088FF)
    BoxWithConstraints(
        modifier.selectableGroup()
            .pointerInput(entries.size, rtl) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val inset = 4.dp.toPx()
                    downX = down.position.x
                    pressedIndex = glassTabPosition(down.position.x - inset, size.width - 2 * inset,
                        latestEntries.size, rtl).roundToInt()
                    try { waitForUpOrCancellation() } finally { pressedIndex = null }
                }
            }
            .pointerInput(entries.size, rtl) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        val inset = 4.dp.toPx()
                        anchor = glassTabPosition(downX - inset, size.width - 2 * inset,
                            latestEntries.size, rtl).roundToInt().toFloat()
                        movement = 0f
                        dragPosition = anchor
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        movement = change.position.x - downX
                        val slot = (size.width - 8.dp.toPx()) / latestEntries.size
                        dragPosition = glassTabDragPosition(anchor, movement, slot, latestEntries.size, rtl)
                    },
                    onDragEnd = {
                        val index = dragPosition?.roundToInt()
                        dragPosition = null
                        latestEntries.getOrNull(index ?: -1)?.takeUnless { it.selected }?.onClick?.invoke()
                    },
                    onDragCancel = { dragPosition = null }
                )
            }
    ) {
        val cell = (maxWidth - 8.dp) / entries.size
        val logical = settledPosition
        val visual = if (rtl) entries.lastIndex - logical else logical
        // Keep the source independent from the lens to avoid recording it recursively.
        Box(Modifier.fillMaxSize().glassBackdropSource(lensBackdrop)) {
            GlassSurface(backdrop, CircleShape, glassTint, glassFallback, rim,
                tuningGroup = GlassSurfaceGroup.NAVIGATION,
                modifier = Modifier.fillMaxSize(), blurRadius = 3.dp, refraction = 0f,
                elevation = if (profile.isDark) 0.dp else 1.dp) {}
            Row(Modifier.fillMaxSize().padding(4.dp)) {
                entries.forEachIndexed { index, entry ->
                    val highlighted = index == (dragPosition?.roundToInt() ?: pressedIndex ?: selectedIndex)
                    val tint = if (highlighted) selectedColor else colors.onSurface
                    val interaction = remember { MutableInteractionSource() }
                    val focused by interaction.collectIsFocusedAsState()
                    Box(
                        Modifier.weight(1f).fillMaxHeight()
                            .then(if (focused) Modifier.border(2.dp, colors.primary, CircleShape) else Modifier)
                            .selectable(entry.selected, onClick = entry.onClick, role = Role.Tab,
                                interactionSource = interaction, indication = null),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Icon(entry.icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
                            Text(entry.label, color = tint, fontSize = 10.sp, lineHeight = 14.sp,
                                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        GlassSurface(
            tuningGroup = GlassSurfaceGroup.NAVIGATION,            backdrop = lensBackdrop, shape = CircleShape,
            tint = if (profile.isDark) Color.White.copy(alpha = 0.145f * (1f - lift * 0.62f))
                else Color.Black.copy(alpha = 0.10f * (1f - lift * 0.70f)),
            fallback = if (profile.isDark) Color.White.copy(alpha = 0.145f) else Color.Black.copy(alpha = 0.10f),
            rim = Color.White.copy(alpha = if (profile.isDark) 0.18f else 0.45f),
            blurRadius = 0.1.dp, refraction = 0.55f * lift, elevation = lift.dp * if (profile.isDark) 0 else 7,
            modifier = Modifier.align(AbsoluteAlignment.TopLeft)
                .width(cell + 24.dp * lift)
                .height((GlassBarHeight - 8.dp) * (1f + lift * (16f / 52f)))
                .absoluteOffset {
                    val extraHeight = (GlassBarHeight - 8.dp).toPx() * lift * (16f / 52f)
                    IntOffset((4.dp.toPx() + cell.toPx() * visual - 12.dp.toPx() * lift).roundToInt(),
                        (4.dp.toPx() - extraHeight / 2f).roundToInt())
                }
        ) {}
    }
}
