package com.codexbar.android.feature.github

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codexbar.android.R
import com.codexbar.android.core.network.github.GitHubContributionCalendar
import com.codexbar.android.core.network.github.GitHubContributionDay
import com.codexbar.android.core.network.github.GitHubContributions
import com.codexbar.android.ui.components.CodexBarCard
import com.codexbar.android.ui.theme.LocalCodexBarThemeProfile
import java.time.LocalDate

@Composable
internal fun GitHubContributionCards(data: GitHubContributions?, unavailable: Boolean, loading: Boolean) {
    if (data == null) {
        if (unavailable && !loading) CodexBarCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.github_profile_unavailable), modifier = Modifier.padding(18.dp),
                style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    val viewer = data.viewer
    CodexBarCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.github_profile_period_title), style = MaterialTheme.typography.titleMedium,
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
            Text(stringResource(R.string.github_profile_contribution_note), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (data.partial) Text(stringResource(R.string.github_profile_partial), style = MaterialTheme.typography.bodySmall)
        }
    }
    viewer.year?.contributionCalendar?.let { ContributionCalendarCard(it) }
    viewer.year?.let { year ->
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
private fun ContributionCalendarCard(calendar: GitHubContributionCalendar) {
    val dark = LocalCodexBarThemeProfile.current.isDark
    var selected by remember(calendar) { mutableStateOf<GitHubContributionDay?>(null) }
    val density = LocalDensity.current
    val step = with(density) { 14.dp.toPx() }
    val cell = with(density) { 11.dp.toPx() }
    val days = remember(calendar) {
        calendar.weeks.map { week -> week.contributionDays.mapNotNull { day ->
            runCatching { LocalDate.parse(day.date).dayOfWeek.value % 7 to day }.getOrNull()
        } }
    }
    val empty = if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.12f)
    val colors = listOf(empty, Color(0xFF176C3A), Color(0xFF239A4F), Color(0xFF36C867), Color(0xFF54E887))
    val description = stringResource(R.string.github_profile_calendar_total, calendar.totalContributions)
    CodexBarCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.github_profile_calendar), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodyMedium)
            if (days.isNotEmpty()) {
                val scroll = rememberScrollState(initial = Int.MAX_VALUE)
                Box(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
                    Canvas(Modifier.width((calendar.weeks.size * 14).dp).height((7 * 14).dp)
                        .semantics { contentDescription = description }
                        .pointerInput(days, step) {
                            detectTapGestures { offset ->
                                val week = (offset.x / step).toInt()
                                val day = (offset.y / step).toInt()
                                selected = days.getOrNull(week)?.firstOrNull { it.first == day }?.second
                            }
                        }) {
                        days.forEachIndexed { week, entries -> entries.forEach { (dayIndex, day) ->
                            val level = when (day.contributionLevel) {
                                "FIRST_QUARTILE" -> 1; "SECOND_QUARTILE" -> 2
                                "THIRD_QUARTILE" -> 3; "FOURTH_QUARTILE" -> 4; else -> 0
                            }
                            drawRoundRect(colors[level], topLeft = Offset(week * step, dayIndex * step),
                                size = Size(cell, cell), cornerRadius = CornerRadius(3.dp.toPx()))
                        } }
                    }
                }
            }
            val selectedDay = selected
            Text(if (selectedDay == null) stringResource(R.string.github_profile_calendar_hint)
                else stringResource(R.string.github_profile_calendar_selected, selectedDay.date, selectedDay.contributionCount),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
