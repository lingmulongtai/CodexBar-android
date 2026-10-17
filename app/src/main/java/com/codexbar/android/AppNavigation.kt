package com.codexbar.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codexbar.android.core.domain.model.AppThemeStyle
import com.codexbar.android.feature.dashboard.DashboardRefreshState
import com.codexbar.android.ui.components.BrandGlyph
import com.codexbar.android.ui.components.GlassBackdrop
import com.codexbar.android.ui.components.GlassSurface
import com.codexbar.android.ui.theme.LocalCodexBarThemeProfile

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
    if (profile.style == AppThemeStyle.INSTRUMENT) {
        InstrumentBottomBar(entries, modifier)
        return
    }
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

@Composable
private fun InstrumentBottomBar(entries: List<NavigationEntry>, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier = modifier.fillMaxWidth(), color = LocalCodexBarThemeProfile.current.navigationContainerColor) {
        Column {
            HorizontalDivider(color = colors.outlineVariant)
            Row(
                modifier = Modifier
                    .navigationBarsPadding()
                    .height(64.dp)
                    .selectableGroup()
            ) {
                entries.forEach { entry ->
                    val tint = if (entry.selected) colors.primary else colors.onSurfaceVariant
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(selected = entry.selected, onClick = entry.onClick, role = Role.Tab),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        if (entry.selected) {
                            Box(
                                Modifier
                                    .width(22.dp)
                                    .height(2.dp)
                                    .background(colors.primary, RoundedCornerShape(1.dp))
                            )
                        }
                        Column(
                            modifier = Modifier.padding(top = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(entry.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                            Text(
                                entry.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (entry.selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = tint,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
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
                        indicatorColor = if (profile.style == AppThemeStyle.INSTRUMENT) {
                            colors.primaryContainer
                        } else {
                            colors.secondaryContainer
                        },
                        selectedIconColor = if (profile.style == AppThemeStyle.INSTRUMENT) {
                            colors.primary
                        } else {
                            colors.onSecondaryContainer
                        }
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
    val glassTint = if (profile.isDark) Color(0xFF1C1C1E).copy(alpha = 0.58f) else Color.White.copy(alpha = 0.58f)
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
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .selectableGroup()
            ) {
                entries.forEach { entry ->
                    val tint = if (entry.selected) colors.primary else colors.onSurface
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(
                                if (entry.selected) colors.onSurface.copy(alpha = 0.08f) else Color.Transparent
                            )
                            .selectable(selected = entry.selected, onClick = entry.onClick, role = Role.Tab),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(entry.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                            Text(
                                entry.label,
                                color = tint,
                                fontSize = 10.sp,
                                fontWeight = if (entry.selected) FontWeight.Bold else FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        if (onRefresh != null) {
            val refreshLabel = stringResource(R.string.action_refresh)
            val active = refresh?.active == true
            GlassSurface(
                backdrop = backdrop,
                shape = CircleShape,
                tint = glassTint,
                fallback = glassFallback,
                rim = rim,
                modifier = Modifier
                    .size(GlassBarHeight)
                    .clip(CircleShape)
                    .clickable(onClick = onRefresh)
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
