package com.codexbar.android.feature.github

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import com.codexbar.android.ui.components.keepHorizontalScrollInPage
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.nativeCanvas
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.GitHubWidgetKind
import com.codexbar.android.core.network.github.GitHubContributionCalendar
import com.codexbar.android.core.network.github.GitHubContributionDay
import com.codexbar.android.core.network.github.GitHubContributions
import com.codexbar.android.ui.components.CodexBarCard
import com.codexbar.android.ui.theme.LocalCodexBarThemeProfile
import java.time.LocalDate

@Composable
internal fun GitHubContributionCards(data: GitHubContributions?, unavailable: Boolean, loading: Boolean,
    selectedDay: GitHubContributionDay?, onSelectDay: (GitHubContributionDay?) -> Unit,
    kind: GitHubWidgetKind? = null, calendarScale: Float = 1f, privateContributionsConnected: Boolean = false) {
    if (data == null) {
        if ((kind == null || kind == GitHubWidgetKind.COMMITS) && unavailable && !loading) CodexBarCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.github_profile_unavailable), modifier = Modifier.padding(18.dp),
                style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    val viewer = data.viewer
    if (kind == null || kind == GitHubWidgetKind.COMMITS) CodexBarCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (privateContributionsConnected && !data.partial) R.string.github_profile_period_title_all
                else R.string.github_profile_period_title), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            val labels = listOf(R.string.github_profile_today, R.string.github_profile_week,
                R.string.github_profile_month, R.string.github_profile_year)
            val values = listOf(viewer.today?.totalCommitContributions, viewer.week?.totalCommitContributions,
                viewer.month?.totalCommitContributions, viewer.year?.totalCommitContributions)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                labels.forEachIndexed { index, label ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(values[index]?.toString() ?: "—", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
            if (data.partial) Text(stringResource(R.string.github_profile_partial), style = MaterialTheme.typography.bodySmall)
        }
    }
    if (kind == null || kind == GitHubWidgetKind.CALENDAR) (viewer.rolling?.contributionCalendar ?: viewer.year?.contributionCalendar)?.let { ContributionCalendarCard(it, selectedDay, onSelectDay, calendarScale) }
    if (kind == null || kind == GitHubWidgetKind.ACTIVITY) viewer.year?.let { year ->
        val values = listOf(year.totalCommitContributions, year.totalIssueContributions,
            year.totalPullRequestContributions, year.totalPullRequestReviewContributions)
        if (values.any { it != null }) CodexBarCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.github_profile_activity), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                val labels = listOf(R.string.github_profile_commits, R.string.github_profile_issues,
                    R.string.github_profile_pulls, R.string.github_profile_reviews)
                val colors = listOf(Color(0xFF32B66B), Color(0xFF58A6FF), Color(0xFFBB86FC), Color(0xFFFFB65C))
                val total = values.filterNotNull().sum().coerceAtLeast(1)
                Row(Modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    values.forEachIndexed { index, value ->
                        if (value != null && value > 0) androidx.compose.foundation.layout.Box(
                            Modifier.weight(value.toFloat() / total).fillMaxHeight()
                                .background(colors[index], RoundedCornerShape(50)))
                    }
                }
                labels.chunked(2).forEachIndexed { row, pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEachIndexed { column, label ->
                            val index = row * 2 + column
                            Column(Modifier.weight(1f)) {
                                Text(values[index]?.toString() ?: "—", style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContributionCalendarCard(calendar: GitHubContributionCalendar, selected: GitHubContributionDay?,
    onSelect: (GitHubContributionDay?) -> Unit, scale: Float) {
    val dark = LocalCodexBarThemeProfile.current.isDark
    val density = LocalDensity.current
    val stepDp = 12.dp * scale.coerceIn(0.42f, 1.6f)
    val step = with(density) { stepDp.toPx() }
    val cell = step * .75f
    val labelsHeight = with(density) { 18.dp.toPx() }
    val days = remember(calendar) {
        calendar.weeks.map { week -> week.contributionDays.mapNotNull { day ->
            runCatching { LocalDate.parse(day.date).dayOfWeek.value % 7 to day }.getOrNull()
        } }
    }
    val identity = calendar.weeks.firstOrNull()?.contributionDays?.firstOrNull()?.date
    var entered by rememberSaveable(identity) { mutableStateOf(false) }
    val appearance = remember(identity) { Animatable(if (entered) 1f else 0f) }
    LaunchedEffect(identity) {
        if (!entered) { entered = true; appearance.animateTo(1f, tween(2200, easing = LinearEasing)) }
    }
    val empty = if (dark) Color.White.copy(alpha = .10f) else Color.Black.copy(alpha = .1f)
    val colors = listOf(empty, Color(0xFF345AA3), Color(0xFF347DF0), Color(0xFF59B7FC), Color(0xFF9EE7FF))
    val description = stringResource(R.string.github_calendar_rolling_total, calendar.totalContributions)
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val monthPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    monthPaint.color = textColor.toArgb()
    monthPaint.textSize = with(density) { 10.sp.toPx() }
    CodexBarCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.github_profile_calendar), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(description, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                if (selected != null) Text(stringResource(R.string.github_profile_calendar_selected, selected.date.drop(5).replace('-', '/'), selected.contributionCount),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            }
            if (days.isNotEmpty()) {
                val scroll = rememberScrollState(initial = Int.MAX_VALUE)
                Box(Modifier.fillMaxWidth().keepHorizontalScrollInPage().horizontalScroll(scroll)) {
                    Canvas(Modifier.width(stepDp * calendar.weeks.size).height(stepDp * 7 + 18.dp)
                        .semantics { contentDescription = description }
                        .pointerInput(days, step) {
                            detectTapGestures { offset ->
                                val week = (offset.x / step).toInt()
                                val day = ((offset.y - labelsHeight) / step).toInt()
                                onSelect(if (offset.y < labelsHeight) null else days.getOrNull(week)?.firstOrNull { it.first == day }?.second)
                            }
                        }) {
                        var lastMonth = -1
                        var lastLabelX = -1000f
                        days.forEachIndexed { week, entries ->
                            val month = entries.firstOrNull()?.second?.date?.let { runCatching { LocalDate.parse(it).month }.getOrNull() }
                            if (month != null && month.value != lastMonth && week * step - lastLabelX > 26.dp.toPx()) {
                                drawContext.canvas.nativeCanvas.drawText(month.getDisplayName(TextStyle.SHORT, Locale.getDefault()), week * step, labelsHeight * .7f, monthPaint)
                                lastMonth = month.value; lastLabelX = week * step
                            }
                            entries.forEach { (dayIndex, day) ->
                                val level = when (day.contributionLevel) {
                                    "FIRST_QUARTILE" -> 1; "SECOND_QUARTILE" -> 2
                                    "THIRD_QUARTILE" -> 3; "FOURTH_QUARTILE" -> 4; else -> 0
                                }
                                val delay = week.toFloat() / days.size * .72f
                                val p = ((appearance.value - delay) / .28f).coerceIn(0f, 1f)
                                val bounce = if (p < .7f) .2f + p / .7f * .95f else 1.15f - (p-.7f)/.3f * .15f
                                val side = cell * bounce
                                val topLeft = Offset(week * step + (cell-side)/2, labelsHeight + dayIndex * step + (cell-side)/2)
                                val radius = CornerRadius(minOf(side/3, 2.dp.toPx()))
                                drawRoundRect(colors[level].copy(alpha = colors[level].alpha * p), topLeft = topLeft,
                                    size = Size(side, side), cornerRadius = radius)
                                if (selected?.date == day.date) drawRoundRect(Color.White, topLeft = topLeft,
                                    size = Size(side, side), cornerRadius = radius, style = Stroke(width = 1.5.dp.toPx()))
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(stringResource(R.string.github_calendar_less), style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(6.dp))
                    colors.forEach { color -> Box(Modifier.padding(end = 3.dp).size(8.dp).background(color, RoundedCornerShape(2.dp))) }
                    Spacer(Modifier.width(3.dp))
                    Text(stringResource(R.string.github_calendar_more), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
