package com.codexbar.android.feature.dashboard

import com.codexbar.android.core.domain.model.DesktopSpend
import com.codexbar.android.core.domain.model.DesktopSpendDay
import java.time.Instant
import java.time.ZoneId

/** Preserve calendar gaps and the PC's day boundary, independent of the phone's timezone. */
internal fun DesktopSpend.calendarDays(count: Int): List<DesktopSpendDay> {
    val end = Instant.ofEpochSecond(updatedAtEpochSeconds).atZone(ZoneId.of(timeZone)).toLocalDate()
    val entries = daily.associateBy { it.day }
    return (count - 1 downTo 0).map { ago ->
        val day = end.minusDays(ago.toLong()).toString()
        entries[day] ?: DesktopSpendDay(day, null, null, false)
    }
}
