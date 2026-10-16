package com.codexbar.android.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codexbar.android.R
import com.codexbar.android.core.presentation.QuotaPresentationSnapshot
import com.codexbar.android.core.presentation.ServiceQuotaPresentation
import com.codexbar.android.ui.components.ProviderIcon
import com.codexbar.android.ui.theme.providerVisualStyle
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency

enum class UsageAnalyticsPage { History, Spend }

@Composable
fun UsageAnalyticsScreen(page: UsageAnalyticsPage, onConnections: () -> Unit, preview: QuotaPresentationSnapshot? = null) {
    if (preview != null) { AnalyticsContent(page, preview, false, {}, onConnections); return }
    val model: DashboardViewModel = hiltViewModel()
    val state by model.uiState.collectAsStateWithLifecycle()
    val refreshing by model.isRefreshing.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { model.refresh() }
    AnalyticsContent(page, (state as? DashboardUiState.Content)?.snapshot, refreshing, model::refresh, onConnections)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalyticsContent(page: UsageAnalyticsPage, snapshot: QuotaPresentationSnapshot?, refreshing: Boolean, onRefresh: () -> Unit, onConnections: () -> Unit) {
    val title = stringResource(if (page == UsageAnalyticsPage.History) R.string.navigation_history else R.string.navigation_spend)
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = {
        TopAppBar(title = { Text(title) }, actions = { IconButton(onClick = onRefresh, enabled = !refreshing) { Icon(Icons.Rounded.Refresh, stringResource(R.string.action_refresh)) } })
    }) { padding ->
        val services = snapshot?.services.orEmpty()
        val rows = services.filter { if (page == UsageAnalyticsPage.Spend) it.desktopSpend != null else it.metrics.isNotEmpty() }
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
        val columns = minOf(rows.size.coerceAtLeast(1), (maxWidth / 340.dp).toInt().coerceAtLeast(1))
        LazyVerticalGrid(columns = GridCells.Fixed(columns), modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(if (page == UsageAnalyticsPage.History) R.string.analytics_history_help else R.string.analytics_spend_help),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (refreshing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    if (page == UsageAnalyticsPage.Spend) SpendOverview(rows)
                    if (rows.isEmpty()) {
                        Text(stringResource(if (page == UsageAnalyticsPage.Spend) R.string.analytics_spend_empty else R.string.analytics_history_empty))
                        OutlinedButton(onClick = onConnections) { Text(stringResource(R.string.action_open_connections)) }
                    }
                }
            }
            items(rows, key = { it.service.name }) { service ->
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
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

@Composable
private fun ProviderHistory(service: ServiceQuotaPresentation) {
    var selected by rememberSaveable(service.service.name) { mutableStateOf(service.metrics.firstOrNull()?.id) }
    val metric = service.metrics.find { it.id == selected } ?: service.metrics.firstOrNull() ?: return
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        service.metrics.forEach { entry -> FilterChip(selected = entry.id == metric.id, onClick = { selected = entry.id }, label = { Text(entry.label) }) }
    }
    QuotaGaugeBar(metric, showExtendedDetails = true, goodColor = providerVisualStyle(service.service).accent)
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
