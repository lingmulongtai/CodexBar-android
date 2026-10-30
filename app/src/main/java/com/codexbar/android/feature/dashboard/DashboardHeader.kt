package com.codexbar.android.feature.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.AppThemeStyle
import com.codexbar.android.ui.components.BrandGlyph
import com.codexbar.android.ui.components.GlassBackdrop
import com.codexbar.android.ui.components.GlassSurface
import com.codexbar.android.ui.theme.LocalCodexBarThemeProfile
import com.codexbar.android.ui.theme.PlexMonoFamily
import java.time.Instant

/** "Updating 2/4" while a refresh runs, otherwise how long ago the readings were taken. */
@Composable
internal fun refreshStatusText(refresh: DashboardRefreshState, updatedAt: Instant?): String {
    val age = ageLabel(updatedAt)
    return when {
        refresh.active && refresh.total > 0 ->
            stringResource(R.string.dashboard_refreshing_progress, refresh.completed, refresh.total)
        refresh.active -> stringResource(R.string.dashboard_refreshing)
        else -> age ?: stringResource(R.string.action_refresh)
    }
}

/**
 * The pinned header of the docked designs: the design's mark, the app name and a refresh control
 * that reports progress in place. Refreshing never disables it or covers the content.
 */
@Composable
internal fun DashboardHeader(
    refresh: DashboardRefreshState,
    updatedAt: Instant?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.app_name),
    showMark: Boolean = true
) {
    val profile = LocalCodexBarThemeProfile.current
    if (profile.style == AppThemeStyle.INSTRUMENT) {
        Column(modifier.fillMaxWidth().statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (showMark) BrandGlyph(profile.style, Modifier.size(32.dp))
                Text(
                    title,
                    fontFamily = PlexMonoFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                RefreshStatusButton(refresh, updatedAt, onRefresh)
            }
            RefreshLine(refresh, Modifier.padding(horizontal = 16.dp))
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (showMark) BrandGlyph(profile.style, Modifier.size(40.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            RefreshStatusButton(refresh, updatedAt, onRefresh)
        }
    }
}

@Composable
internal fun RefreshStatusButton(
    refresh: DashboardRefreshState,
    updatedAt: Instant?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile = LocalCodexBarThemeProfile.current
    val colors = MaterialTheme.colorScheme
    val status = refreshStatusText(refresh, updatedAt)
    val refreshLabel = stringResource(R.string.action_refresh)
    val instrument = profile.style == AppThemeStyle.INSTRUMENT
    Surface(
        onClick = onRefresh,
        modifier = modifier
            .height(40.dp)
            .semantics { contentDescription = "$refreshLabel, $status" },
        shape = if (instrument) RoundedCornerShape(10.dp) else CircleShape,
        color = if (instrument) colors.surfaceContainerLow else colors.surfaceContainerHigh,
        border = if (instrument) BorderStroke(1.dp, colors.outlineVariant) else null
    ) {
        Row(
            modifier = Modifier.padding(start = if (instrument) 12.dp else 10.dp, end = if (instrument) 12.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (instrument) 8.dp else 6.dp)
        ) {
            when {
                refresh.active -> SmallSpinner(if (instrument) 14.dp else 16.dp)
                instrument -> Box(Modifier.size(7.dp).background(colors.primary, CircleShape))
                else -> Icon(Icons.Rounded.Refresh, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
            }
            Text(
                status,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = if (instrument) PlexMonoFamily else MaterialTheme.typography.labelLarge.fontFamily,
                fontWeight = if (instrument) FontWeight.Medium else FontWeight.SemiBold,
                fontSize = if (instrument) 12.sp else MaterialTheme.typography.labelLarge.fontSize,
                maxLines = 1
            )
            if (instrument && !refresh.active) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
internal fun SmallSpinner(size: Dp, color: Color = MaterialTheme.colorScheme.primary) {
    CircularProgressIndicator(
        modifier = Modifier.size(size),
        color = color,
        trackColor = color.copy(alpha = 0.2f),
        strokeWidth = (size.value / 7f).coerceAtLeast(1.5f).dp
    )
}

/** Instrument's 2 dp progress line; the space is always reserved so nothing moves. */
@Composable
private fun RefreshLine(refresh: DashboardRefreshState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val target = when {
        !refresh.active -> 0f
        refresh.total > 0 -> (refresh.completed.toFloat() / refresh.total).coerceAtLeast(0.04f)
        else -> 0.04f
    }
    val progress by animateFloatAsState(target, label = "refresh_line")
    Box(
        modifier
            .fillMaxWidth()
            .height(2.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(if (refresh.active) colors.outlineVariant else Color.Transparent)
    ) {
        if (refresh.active) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(colors.primary))
        }
    }
}

/** Liquid Glass's large title, which scrolls away under the collapsed glass bar. */
@Composable
internal fun GlassLargeTitle(
    title: String,
    refresh: DashboardRefreshState,
    updatedAt: Instant?,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val status = refreshStatusText(refresh, updatedAt)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 4.dp, top = 20.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            title,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.4).sp,
            fontFamily = MaterialTheme.typography.displaySmall.fontFamily
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (refresh.active) SmallSpinner(14.dp, colors.onSurfaceVariant)
            Text(
                if (refresh.active || updatedAt == null) status
                else stringResource(R.string.dashboard_updated_status, status),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
        }
    }
}

/** The inline title that appears in glass once the large title has scrolled away. */
@Composable
internal fun GlassCollapsedHeader(
    title: String,
    refresh: DashboardRefreshState,
    updatedAt: Instant?,
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier
) {
    val profile = LocalCodexBarThemeProfile.current
    val colors = MaterialTheme.colorScheme
    val status = refreshStatusText(refresh, updatedAt)
    GlassSurface(
        backdrop = backdrop,
        shape = RectangleShape,
        tint = colors.background.copy(alpha = 0.72f),
        fallback = colors.background.copy(alpha = 0.97f),
        rim = Color.Transparent,
        blurRadius = 18.dp,
        refraction = 0f,
        elevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (refresh.active) SmallSpinner(11.dp, colors.onSurfaceVariant)
                    Text(status, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = if (profile.isDark) colors.outlineVariant else colors.outlineVariant.copy(alpha = 0.6f))
        }
    }
}
