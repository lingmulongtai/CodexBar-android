package com.codexbar.android.feature.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import com.codexbar.android.R
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

/** The current time, ticking every half minute so relative labels stay current on screen. */
@Composable
internal fun rememberNow(): Instant {
    val now by produceState(Instant.now()) {
        while (true) {
            delay(30_000L)
            value = Instant.now()
        }
    }
    return now
}

/** How long ago [instant] was, such as "3 min ago"; null when there is no reading yet. */
@Composable
internal fun ageLabel(instant: Instant?, now: Instant = rememberNow()): String? {
    if (instant == null) return null
    val minutes = Duration.between(instant, now).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> stringResource(R.string.dashboard_updated_just_now)
        minutes < 60 -> stringResource(R.string.presentation_minutes_ago, minutes.toInt())
        minutes < 24 * 60 -> stringResource(R.string.presentation_hours_ago, (minutes / 60).toInt())
        else -> stringResource(R.string.presentation_days_ago, (minutes / (24 * 60)).toInt())
    }
}

/** A short countdown to [instant], such as "3d 4h" or "2h 18m"; null once it has passed. */
@Composable
internal fun compactCountdown(instant: Instant?, now: Instant = rememberNow()): String? {
    if (instant == null) return null
    val minutes = Duration.between(now, instant).toMinutes()
    if (minutes < 0) return null
    val days = minutes / (24 * 60)
    val hours = (minutes / 60) % 24
    return when {
        days > 0 -> stringResource(R.string.dashboard_compact_days_hours, days.toInt(), hours.toInt())
        minutes >= 60 -> stringResource(R.string.dashboard_compact_hours_minutes, (minutes / 60).toInt(), (minutes % 60).toInt())
        else -> stringResource(R.string.dashboard_compact_minutes, minutes.toInt())
    }
}
