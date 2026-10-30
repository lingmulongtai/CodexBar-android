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
import com.codexbar.android.ui.components.CodexBarTextButton as TextButton
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
    showMark: Boolean = true,
    onFinishReorder: (() -> Unit)? = null
) {
    val profile = LocalCodexBarThemeProfile.current
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
        if (onFinishReorder != null) {
            TextButton(onClick = onFinishReorder) { Text(stringResource(R.string.provider_order_done)) }
        } else {
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
    Surface(onClick = onRefresh, modifier = modifier.height(40.dp)
        .semantics { contentDescription = "$refreshLabel, $status" },
        shape = CircleShape, color = colors.surfaceContainerHigh) {
        Row(Modifier.padding(start = 10.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (refresh.active) SmallSpinner(16.dp)
            else Icon(Icons.Rounded.Refresh, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
            Text(status, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
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
    modifier: Modifier = Modifier,
    onFinishReorder: (() -> Unit)? = null
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
            Box(Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 56.dp).padding(vertical = 8.dp)) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = if (onFinishReorder != null) 88.dp else 0.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (refresh.active) SmallSpinner(11.dp, colors.onSurfaceVariant)
                        Text(status, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (onFinishReorder != null) {
                    TextButton(onClick = onFinishReorder, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)) {
                        Text(stringResource(R.string.provider_order_done))
                    }
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = if (profile.isDark) colors.outlineVariant else colors.outlineVariant.copy(alpha = 0.6f))
        }
    }
}
