package com.codexbar.android.feature.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import com.codexbar.android.ui.components.CodexBarFilterChip as FilterChip
import com.codexbar.android.ui.components.CodexBarTextButton as TextButton
import com.codexbar.android.ui.components.CodexBarOutlinedButton as OutlinedButton
import com.codexbar.android.ui.components.CodexBarPanel
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.AppThemeStyle
import com.codexbar.android.core.presentation.QuotaPresentationSnapshot
import com.codexbar.android.core.presentation.ServiceQuotaPresentation
import com.codexbar.android.ui.components.WallpaperFooter
import com.codexbar.android.ui.components.glassTabPosition
import com.codexbar.android.ui.components.LocalBottomChromeInset
import com.codexbar.android.ui.components.GlassSurface
import com.codexbar.android.ui.components.LocalGlassCardBackdrop
import com.codexbar.android.ui.components.ProviderIcon
import com.codexbar.android.ui.components.ThemedTopBar
import com.codexbar.android.ui.theme.LocalCodexBarThemeProfile
import com.codexbar.android.ui.theme.PlexMonoFamily
import com.codexbar.android.ui.theme.providerVisualStyle
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import kotlin.math.roundToInt

enum class UsageAnalyticsPage { History, Spend }

@Composable
fun UsageAnalyticsScreen(
    page: UsageAnalyticsPage,
    onConnections: () -> Unit,
    preview: QuotaPresentationSnapshot? = null,
    model: DashboardViewModel? = null,
    onPageChange: (UsageAnalyticsPage) -> Unit = {}
) {
    if (preview != null || model == null) { AnalyticsContent(page, preview, DashboardRefreshState(), {}, onConnections, onPageChange); return }
    val state by model.uiState.collectAsStateWithLifecycle()
    val refresh by model.refreshState.collectAsStateWithLifecycle()
    val localeTag = LocalConfiguration.current.locales[0].toLanguageTag()
    LaunchedEffect(Unit) { model.refreshIfStale(localeTag) }
    AnalyticsContent(page, (state as? DashboardUiState.Content)?.snapshot, refresh, model::refresh, onConnections, onPageChange)
}

@Composable
private fun AnalyticsContent(
    page: UsageAnalyticsPage,
    snapshot: QuotaPresentationSnapshot?,
    refresh: DashboardRefreshState,
    onRefresh: () -> Unit,
    onConnections: () -> Unit,
    onPageChange: (UsageAnalyticsPage) -> Unit
) {
    val profile = LocalCodexBarThemeProfile.current
    Column(Modifier.fillMaxSize()) {
        // Liquid Glass refreshes from its floating button; the docked designs refresh here.
        ThemedTopBar(title = stringResource(R.string.navigation_trends), actions = {
            if (!profile.floatingNavigation) RefreshStatusButton(refresh, snapshot?.generatedAt, onRefresh, Modifier.padding(end = 8.dp))
        })
        TrendsTabs(page, onPageChange, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        val services = snapshot?.services.orEmpty()
        val rows = services.filter { if (page == UsageAnalyticsPage.Spend) it.desktopSpend != null else it.metrics.isNotEmpty() }
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = minOf(rows.size.coerceAtLeast(1), (maxWidth / 340.dp).toInt().coerceAtLeast(1))
        LazyVerticalGrid(columns = GridCells.Fixed(columns), modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp + LocalBottomChromeInset.current),
            verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.then(if (profile.style == AppThemeStyle.LIQUID_GLASS)
                    Modifier.background(profile.cardColor.copy(alpha = 0.86f), MaterialTheme.shapes.large).padding(16.dp) else Modifier),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(if (page == UsageAnalyticsPage.History) R.string.analytics_history_help else R.string.analytics_spend_help),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (page == UsageAnalyticsPage.Spend) SpendOverview(rows)
                    if (rows.isEmpty()) {
                        Text(stringResource(if (page == UsageAnalyticsPage.Spend) R.string.analytics_spend_empty else R.string.analytics_history_empty))
                        OutlinedButton(onClick = onConnections) { Text(stringResource(R.string.action_open_connections)) }
                    }
                }
            }
            items(rows, key = { it.service.name }) { service ->
                CodexBarPanel(shape = MaterialTheme.shapes.large, color = profile.cardColor, border = BorderStroke(1.dp, profile.cardBorderColor)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ProviderIcon(service.service, Modifier.size(28.dp))
                            Text(service.service.displayName, style = MaterialTheme.typography.titleLarge)
                        }
                        if (page == UsageAnalyticsPage.History) ProviderHistory(service) else ProviderSpend(service)
                    }
                }
            }
            item(key = "wallpaper-credit", span = { GridItemSpan(maxLineSpan) }) { WallpaperFooter() }
        }
        }
    }
}

/** History and Spend share the Trends tab; this switches between them in the design's style. */
@Composable
private fun TrendsTabs(page: UsageAnalyticsPage, onPageChange: (UsageAnalyticsPage) -> Unit, modifier: Modifier = Modifier) {
    val profile = LocalCodexBarThemeProfile.current
    val colors = MaterialTheme.colorScheme
    val pages = listOf(UsageAnalyticsPage.History to R.string.navigation_history, UsageAnalyticsPage.Spend to R.string.navigation_spend)
    when (profile.style) {
        AppThemeStyle.MATERIAL_3 -> SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
            pages.forEachIndexed { index, (entry, label) ->
                SegmentedButton(selected = page == entry, onClick = { onPageChange(entry) },
                    shape = SegmentedButtonDefaults.itemShape(index, pages.size)) { Text(stringResource(label)) }
            }
        }
        AppThemeStyle.LIQUID_GLASS -> {
            val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            val haptic = LocalHapticFeedback.current
            val latestChange by rememberUpdatedState(onPageChange)
            val latestPage by rememberUpdatedState(page)
            val selectedIndex = pages.indexOfFirst { it.first == page }.coerceAtLeast(0)
            var dragPosition by remember { mutableStateOf<Float?>(null) }
            val settled by animateFloatAsState(dragPosition ?: selectedIndex.toFloat(),
                if (dragPosition != null) snap() else spring(stiffness = 500f), label = "trends_tab_position")
            val lift by animateFloatAsState(if (dragPosition != null) 1f else 0f,
                spring(dampingRatio = 0.65f, stiffness = 500f), label = "trends_tab_lift")
            GlassSurface(
                backdrop = LocalGlassCardBackdrop.current,
                shape = CircleShape,
                tint = colors.onSurface.copy(alpha = 0.06f),
                fallback = colors.surfaceContainerHigh,
                rim = Color.White.copy(alpha = if (profile.isDark) 0.16f else 0.50f),
                modifier = modifier.fillMaxWidth(), blurRadius = 4.dp, elevation = 0.dp
            ) {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(3.dp).height(36.dp).selectableGroup()
                    .pointerInput(pages.size, rtl) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { point ->
                                dragPosition = glassTabPosition(point.x, size.width.toFloat(), pages.size, rtl)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                dragPosition = glassTabPosition(change.position.x, size.width.toFloat(), pages.size, rtl)
                            },
                            onDragEnd = {
                                val target = dragPosition?.roundToInt()?.let { pages.getOrNull(it)?.first }
                                dragPosition = null
                                if (target != null && target != latestPage) latestChange(target)
                            },
                            onDragCancel = { dragPosition = null }
                        )
                    }) {
                    val cell = maxWidth / pages.size
                    val logical = dragPosition ?: settled
                    val visual = if (rtl) pages.lastIndex - logical else logical
                    GlassSurface(
                        backdrop = LocalGlassCardBackdrop.current, shape = CircleShape,
                        tint = profile.cardColor.copy(alpha = 0.80f), fallback = profile.cardColor,
                        rim = Color.White.copy(alpha = if (profile.isDark) 0.30f else 0.65f),
                        blurRadius = 2.dp, refraction = 0.5f + lift * 0.3f, elevation = lift.dp * 4,
                        modifier = Modifier.align(AbsoluteAlignment.TopLeft).width(cell).fillMaxHeight()
                            .absoluteOffset { IntOffset((cell.toPx() * visual).roundToInt(), 0) }
                            .graphicsLayer { scaleX = 1f + lift * 0.04f; scaleY = 1f + lift * 0.10f }
                    ) {}
                    Row(Modifier.fillMaxSize()) {
                        pages.forEachIndexed { index, (entry, label) ->
                            val highlighted = index == (dragPosition?.roundToInt() ?: selectedIndex)
                            val interaction = remember { MutableInteractionSource() }
                            val focused by interaction.collectIsFocusedAsState()
                            Box(Modifier.weight(1f).fillMaxHeight()
                                .then(if (focused) Modifier.border(2.dp, colors.primary, CircleShape) else Modifier)
                                .clip(CircleShape)
                                .selectable(selected = page == entry, onClick = { onPageChange(entry) }, role = Role.Tab,
                                    interactionSource = interaction, indication = null), contentAlignment = Alignment.Center) {
                                Text(stringResource(label), style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }

    }
}

@Composable
private fun ProviderHistory(service: ServiceQuotaPresentation) {
    var selected by rememberSaveable(service.service.name) { mutableStateOf(service.metrics.firstOrNull()?.id) }
    val metric = service.metrics.find { it.id == selected } ?: service.metrics.firstOrNull() ?: return
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        service.metrics.forEach { entry -> FilterChip(selected = entry.id == metric.id, onClick = { selected = entry.id }, label = { Text(entry.label) }) }
    }
    QuotaGaugeBar(metric, showGuideLegend = true, goodColor = providerVisualStyle(service.service).accent)
    QuotaHistoryChart(metric.history, providerVisualStyle(service.service).accent)
    Text(stringResource(if (service.historyFromDesktop) R.string.analytics_pc_history else R.string.analytics_device_history),
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SpendOverview(rows: List<ServiceQuotaPresentation>) {
    val groups = rows.mapNotNull { it.desktopSpend }.groupBy { it.currency }
    groups.forEach { (currency, values) ->
        val known = values.mapNotNull { it.thirtyDay }
        if (known.isNotEmpty()) {
            Text(money(known.sum(), currency), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(if (known.size == values.size) R.string.analytics_30_day_total else R.string.analytics_30_day_partial),
                style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ProviderSpend(service: ServiceQuotaPresentation) {
    val spend = service.desktopSpend ?: return
    var tokens by rememberSaveable(service.service.name) { mutableStateOf(false) }
    var days by rememberSaveable(service.service.name) { mutableIntStateOf(30) }
    var selectedDay by rememberSaveable(service.service.name) { mutableStateOf<String?>(null) }
    var chartWidth by remember { mutableIntStateOf(1) }
    val numbers = remember { NumberFormat.getIntegerInstance() }
    val unknown = stringResource(R.string.analytics_unknown)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column { Text(stringResource(R.string.analytics_7_days), style = MaterialTheme.typography.labelMedium); Text(spend.sevenDay?.let { money(it, spend.currency) } ?: unknown, style = MaterialTheme.typography.titleLarge) }
        Column { Text(stringResource(R.string.analytics_30_days), style = MaterialTheme.typography.labelMedium); Text(spend.thirtyDay?.let { money(it, spend.currency) } ?: unknown, style = MaterialTheme.typography.titleLarge) }
    }
    val periodLabel = when (spend.reportingPeriod) {
        "all" -> stringResource(R.string.local_usage_all)
        "month-to-date" -> stringResource(R.string.local_usage_month_to_date)
        else -> stringResource(R.string.local_usage_rolling_days, spend.reportingPeriod.removePrefix("rolling:").toIntOrNull() ?: 30)
    }
    Text(stringResource(R.string.analytics_period_total, periodLabel, spend.periodCost?.let { money(it, spend.currency) } ?: unknown), style = MaterialTheme.typography.bodySmall)
    Text((if (spend.tokensLowerBound) "≥ " else "") + (spend.thirtyDayTokens?.let(numbers::format) ?: unknown) + " " + stringResource(R.string.analytics_tokens), style = MaterialTheme.typography.titleMedium)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = !tokens, onClick = { tokens = false }, label = { Text(stringResource(R.string.navigation_spend)) })
        FilterChip(selected = tokens, onClick = { tokens = true }, label = { Text(stringResource(R.string.analytics_tokens)) })
        FilterChip(selected = days == 7, onClick = { days = 7 }, label = { Text(stringResource(R.string.analytics_7_days)) })
        FilterChip(selected = days == 30, onClick = { days = 30 }, label = { Text(stringResource(R.string.analytics_30_days)) })
    }
    val series = spend.calendarDays(days)
    val values = series.map { if (tokens) it.tokens?.toDouble() else it.costUsd }
    val selected = series.indexOfFirst { it.day == selectedDay }.takeIf { it >= 0 } ?: series.lastIndex
    val accent = providerVisualStyle(service.service).accent
    val glassChart = LocalCodexBarThemeProfile.current.style == AppThemeStyle.LIQUID_GLASS
    val description = stringResource(R.string.analytics_chart_description, values.count { it != null }, values.size)
    Canvas(Modifier.fillMaxWidth().height(144.dp).onSizeChanged { chartWidth = it.width.coerceAtLeast(1) }
        .pointerInput(series, tokens) { detectTapGestures { offset ->
            if (series.isNotEmpty()) selectedDay = series[(offset.x / chartWidth * series.size).toInt().coerceIn(series.indices)].day
        } }.semantics { contentDescription = description }) {
        val max = values.filterNotNull().maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val width = size.width / values.size.coerceAtLeast(1)
        values.forEachIndexed { index, value ->
            if (value != null) {
                val x = (index + 0.5f) * width
                val y = (size.height * (1 - value / max)).toFloat()
                if (glassChart) {
                    val barWidth = (width * 0.62f).coerceAtLeast(2f)
                    val barHeight = (size.height - y).coerceAtLeast(1f)
                    drawRoundRect(Brush.verticalGradient(listOf(accent.copy(alpha = if (index == selected) 1f else 0.75f), accent.copy(alpha = 0.20f))),
                        topLeft = Offset(x - barWidth / 2, y), size = Size(barWidth, barHeight), cornerRadius = CornerRadius(3.dp.toPx()))
                } else {
                drawLine(accent.copy(alpha = if (index == selected) 1f else 0.55f), Offset(x, size.height - 1), Offset(x, y.coerceAtMost(size.height - 1)), strokeWidth = (width * 0.65f).coerceAtLeast(2f))
                }
                if (value == 0.0) drawCircle(accent, 2f, Offset(x, size.height - 2f))
            }
        }
    }
    series.getOrNull(selected)?.let { day ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = selected > 0, onClick = { selectedDay = series[selected - 1].day }) { Text(stringResource(R.string.analytics_previous_day)) }
            val value = if (tokens) day.tokens?.let(numbers::format) else day.costUsd?.let { money(it, "USD") }
            Text("${day.day}\n${value ?: unknown}", style = MaterialTheme.typography.labelMedium)
            TextButton(enabled = selected < series.lastIndex, onClick = { selectedDay = series[selected + 1].day }) { Text(stringResource(R.string.analytics_next_day)) }
        }
    }
    if (series.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(series.first().day, style = MaterialTheme.typography.labelSmall); Text(series.last().day, style = MaterialTheme.typography.labelSmall)
    }
    Text(stringResource(R.string.analytics_missing_unknown), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val updated = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(spend.updatedAtEpochSeconds))
    Text(stringResource(R.string.analytics_pc_updated, updated, spend.timeZone), style = MaterialTheme.typography.labelSmall)
    if (spend.refreshing || Instant.now().epochSecond - spend.updatedAtEpochSeconds > 35 * 60) Text(stringResource(R.string.local_usage_stale), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
}

private fun money(value: Double, currency: String): String = runCatching {
    NumberFormat.getCurrencyInstance().apply { this.currency = Currency.getInstance(currency) }.format(value)
}.getOrElse { "$currency ${NumberFormat.getNumberInstance().format(value)}" }
