package com.codexbar.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import com.codexbar.android.feature.dashboard.DashboardRefreshState
import com.codexbar.android.ui.components.BrandGlyph
import com.codexbar.android.ui.components.GlassBackdrop
import com.codexbar.android.ui.components.GlassSurface
import com.codexbar.android.ui.components.glassTabPosition
import com.codexbar.android.ui.theme.LocalCodexBarThemeProfile
import kotlin.math.roundToInt

internal data class NavigationEntry(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit
)

internal val GlassBarHeight = 64.dp
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
    refresh: DashboardRefreshState?,
    onRefresh: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val profile = LocalCodexBarThemeProfile.current
    val colors = MaterialTheme.colorScheme
    val glassTint = if (profile.isDark) Color(0xFF202430).copy(alpha = 0.62f) else Color.White.copy(alpha = 0.60f)
    val glassFallback = if (profile.isDark) Color(0xFF1C1C1E).copy(alpha = 0.97f) else Color.White.copy(alpha = 0.97f)
    val rim = if (profile.isDark) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.95f)
    Row(
        modifier = modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = GlassBarMargin),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassSurface(
            backdrop = backdrop,
            shape = CircleShape,
            tint = glassTint,
            fallback = glassFallback,
            rim = rim,
            modifier = Modifier
                .weight(1f)
                .height(GlassBarHeight)
        ) {
            GlassTabRow(entries, backdrop)
        }
        if (onRefresh != null) {
            val refreshLabel = stringResource(R.string.action_refresh)
            val active = refresh?.active == true
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val focused by interaction.collectIsFocusedAsState()
            val scale by animateFloatAsState(
                if (pressed) 1.10f else 1f,
                animationSpec = spring(dampingRatio = 0.65f, stiffness = 500f),
                label = "glass_refresh_lift"
            )
            GlassSurface(
                backdrop = backdrop,
                shape = CircleShape,
                tint = glassTint,
                fallback = glassFallback,
                rim = rim,
                modifier = Modifier
                    .size(GlassBarHeight)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .then(if (focused) Modifier.border(2.dp, colors.primary, CircleShape) else Modifier)
                    .clip(CircleShape)
                    .clickable(interactionSource = interaction, indication = null, onClick = onRefresh)
                    .semantics {
                        role = Role.Button
                        contentDescription = refreshLabel
                    }
            ) {
                if (active) {
                    val total = refresh?.total ?: 0
                    val fraction = if (total > 0) {
                        (refresh?.completed ?: 0).toFloat() / total
                    } else {
                        0f
                    }.coerceAtLeast(0.08f)
                    val ring = colors.primary
                    Canvas(Modifier.fillMaxSize().padding(3.dp)) {
                        val stroke = 3.dp.toPx()
                        val inset = stroke / 2
                        drawArc(
                            color = ring.copy(alpha = 0.16f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - stroke, size.height - stroke),
                            style = Stroke(stroke)
                        )
                        drawArc(
                            color = ring,
                            startAngle = -90f,
                            sweepAngle = 360f * fraction,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - stroke, size.height - stroke),
                            style = Stroke(stroke, cap = StrokeCap.Round)
                        )
                    }
                }
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = if (active) colors.primary else colors.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}


/** One moving lens, rather than separate pressed backgrounds, follows a held finger. */
@Composable
private fun GlassTabRow(entries: List<NavigationEntry>, backdrop: GlassBackdrop) {
    if (entries.isEmpty()) return
    val profile = LocalCodexBarThemeProfile.current
    val colors = MaterialTheme.colorScheme
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val haptic = LocalHapticFeedback.current
    val latestEntries by rememberUpdatedState(entries)
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    val selectedIndex = entries.indexOfFirst { it.selected }.coerceAtLeast(0)
    val settledPosition by animateFloatAsState(selectedIndex.toFloat(), spring(stiffness = 500f), label = "glass_tab_position")
    val lift by animateFloatAsState(if (dragPosition != null) 1f else 0f, spring(dampingRatio = 0.65f, stiffness = 500f), label = "glass_tab_lift")
    BoxWithConstraints(
        Modifier.fillMaxSize().padding(4.dp).selectableGroup()
            .pointerInput(entries.size, rtl) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { point ->
                        dragPosition = glassTabPosition(point.x, size.width.toFloat(), latestEntries.size, rtl)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        dragPosition = glassTabPosition(change.position.x, size.width.toFloat(), latestEntries.size, rtl)
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
        val cell = maxWidth / entries.size
        val logical = dragPosition ?: settledPosition
        val visual = if (rtl) entries.lastIndex - logical else logical
        GlassSurface(
            backdrop = backdrop,
            shape = CircleShape,
            tint = if (profile.isDark) Color.White.copy(alpha = 0.14f) else colors.primary.copy(alpha = 0.09f),
            fallback = colors.surfaceContainerHigh,
            rim = Color.White.copy(alpha = if (profile.isDark) 0.30f else 0.65f),
            blurRadius = 2.dp,
            refraction = 0.5f + lift * 0.3f,
            elevation = lift.dp * 4,
            modifier = Modifier.align(AbsoluteAlignment.TopLeft).width(cell).fillMaxHeight()
                .absoluteOffset { IntOffset((cell.toPx() * visual).roundToInt(), 0) }
                .graphicsLayer {
                    scaleX = 1f + lift * 0.06f
                    scaleY = 1f + lift * 0.12f
                    translationY = -2.dp.toPx() * lift
                }
        ) {}
        Row(Modifier.fillMaxSize()) {
            entries.forEachIndexed { index, entry ->
                val highlighted = index == (dragPosition?.roundToInt() ?: selectedIndex)
                val tint = if (highlighted) colors.primary else colors.onSurface
                val interaction = remember { MutableInteractionSource() }
                val focused by interaction.collectIsFocusedAsState()
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .then(if (focused) Modifier.border(2.dp, colors.primary, CircleShape) else Modifier)
                        .selectable(entry.selected, onClick = entry.onClick, role = Role.Tab,
                            interactionSource = interaction, indication = null),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(entry.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                        Text(entry.label, color = tint, fontSize = 10.sp,
                            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
