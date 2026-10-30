package com.codexbar.android.feature.dashboard

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.AppThemeStyle
import com.codexbar.android.core.presentation.QuotaPresentationSnapshot
import com.codexbar.android.core.presentation.ServiceQuotaPresentation
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Surface(shape = MaterialTheme.shapes.large, color = profile.cardColor, border = BorderStroke(1.dp, profile.cardBorderColor)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ProviderIcon(service.service, Modifier.size(28.dp))
                            Text(service.service.displayName, style = MaterialTheme.typography.titleLarge)
                        }
                        if (page == UsageAnalyticsPage.History) ProviderHistory(service) else ProviderSpend(service)
                    }
                }
            }
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
        AppThemeStyle.LIQUID_GLASS -> GlassSurface(
            backdrop = LocalGlassCardBackdrop.current,
            shape = CircleShape,
            tint = colors.onSurface.copy(alpha = 0.06f),
            fallback = colors.surfaceContainerHigh,
            rim = Color.White.copy(alpha = if (profile.isDark) 0.16f else 0.50f),
            modifier = modifier.fillMaxWidth(),
            blurRadius = 4.dp,
            elevation = 0.dp
        ) {
            Row(Modifier.fillMaxWidth().padding(3.dp).selectableGroup()) {
                pages.forEach { (entry, label) ->
                    val selected = page == entry
                    Box(Modifier.weight(1f).height(36.dp).clip(CircleShape)
                        .background(if (selected) profile.cardColor.copy(alpha = 0.90f) else Color.Transparent)
                        .selectable(selected = selected, onClick = { onPageChange(entry) }, role = Role.Tab), contentAlignment = Alignment.Center) {
                        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
        }
        AppThemeStyle.INSTRUMENT -> Row(modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            pages.forEach { (entry, label) ->
                val selected = page == entry
                Column(Modifier.selectable(selected = selected, onClick = { onPageChange(entry) }, role = Role.Tab).padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(label), fontFamily = PlexMonoFamily, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) colors.onSurface else colors.onSurfaceVariant)
                    Box(Modifier.width(24.dp).height(2.dp).background(if (selected) colors.primary else Color.Transparent))
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
                drawLine(accent.copy(alpha = if (index == selected) 1f else 0.55f), Offset(x, size.height - 1), Offset(x, y.coerceAtMost(size.height - 1)), strokeWidth = (width * 0.65f).coerceAtLeast(2f))
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
