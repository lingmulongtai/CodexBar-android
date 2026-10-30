package com.codexbar.android.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.codexbar.android.ui.components.CodexBarTextButton as TextButton
import com.codexbar.android.ui.components.CodexBarIconButton as IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.AppThemeStyle
import com.codexbar.android.ui.components.BrandGlyph
import com.codexbar.android.ui.components.GlassPageHeader
import com.codexbar.android.ui.theme.LocalCodexBarThemeProfile
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
    onStartReorder: (() -> Unit)? = null,
    onFinishReorder: (() -> Unit)? = null
) {
    val profile = LocalCodexBarThemeProfile.current
    if (profile.style == AppThemeStyle.LIQUID_GLASS) {
        val status = refreshStatusText(refresh, updatedAt)
        val colors = MaterialTheme.colorScheme
        GlassPageHeader(title = title, modifier = modifier, subtitle = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (refresh.active) SmallSpinner(14.dp, colors.onSurfaceVariant)
                Text(if (refresh.active || updatedAt == null) status else stringResource(R.string.dashboard_updated_status, status),
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }, actions = { DashboardOrderAction(onStartReorder, onFinishReorder) })
        return
    }
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
            DashboardOrderAction(onStartReorder, null)
            RefreshStatusButton(refresh, updatedAt, onRefresh)
        }
    }
}

@Composable
private fun DashboardOrderAction(onStart: (() -> Unit)?, onFinish: (() -> Unit)?) {
    if (onFinish != null) {
        TextButton(onClick = onFinish) { Text(stringResource(R.string.provider_order_done)) }
    } else if (onStart != null) {
        IconButton(onClick = onStart) {
            Icon(Icons.Rounded.SwapVert, contentDescription = stringResource(R.string.provider_order_action),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
