package com.codexbar.android.core.monitoring

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class MonitoringSession(
    val startedAtMillis: Long,
    val endsAtMillis: Long,
    val cadenceMinutes: Long
) {
    fun isActive(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return nowMillis in startedAtMillis until endsAtMillis
    }

    fun remainingMinutes(nowMillis: Long = System.currentTimeMillis()): Long {
        return ((endsAtMillis - nowMillis).coerceAtLeast(0L) + 59_999L) / 60_000L
    }
}

@Singleton
class MonitoringSessionStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun start(
        durationMinutes: Long = DEFAULT_DURATION_MINUTES,
        cadenceMinutes: Long = DEFAULT_CADENCE_MINUTES,
        nowMillis: Long = System.currentTimeMillis()
    ): MonitoringSession {
        val boundedDuration = normalizeMonitoringDuration(durationMinutes)
        val boundedCadence = cadenceMinutes.coerceAtLeast(MIN_CADENCE_MINUTES)
        val session = MonitoringSession(
            startedAtMillis = nowMillis,
            endsAtMillis = nowMillis + boundedDuration * 60_000L,
            cadenceMinutes = boundedCadence
        )
        prefs.edit()
            .putLong(KEY_STARTED_AT, session.startedAtMillis)
            .putLong(KEY_ENDS_AT, session.endsAtMillis)
            .putLong(KEY_CADENCE_MINUTES, session.cadenceMinutes)
            .apply()
        return session
    }

    fun stop() {
        prefs.edit()
            .remove(KEY_STARTED_AT)
            .remove(KEY_ENDS_AT)
            .remove(KEY_CADENCE_MINUTES)
            .apply()
    }

    fun preferredDurationMinutes(): Long {
        return normalizeMonitoringDuration(
            prefs.getLong(KEY_PREFERRED_DURATION_MINUTES, DEFAULT_DURATION_MINUTES)
        )
    }

    fun setPreferredDurationMinutes(durationMinutes: Long) {
        prefs.edit()
            .putLong(KEY_PREFERRED_DURATION_MINUTES, normalizeMonitoringDuration(durationMinutes))
            .apply()
    }

    fun activeSession(nowMillis: Long = System.currentTimeMillis()): MonitoringSession? {
        val startedAt = prefs.getLong(KEY_STARTED_AT, 0L).takeIf { it > 0L } ?: return null
        val endsAt = prefs.getLong(KEY_ENDS_AT, 0L).takeIf { it > startedAt } ?: return null
        val cadence = prefs.getLong(KEY_CADENCE_MINUTES, DEFAULT_CADENCE_MINUTES)
            .coerceAtLeast(MIN_CADENCE_MINUTES)
        val session = MonitoringSession(startedAt, endsAt, cadence)
        if (!session.isActive(nowMillis)) {
            stop()
            return null
        }
        return session
    }

    companion object {
        const val PREFS_NAME = "codexbar_monitoring_session"
        const val BACKUP_PATH = "$PREFS_NAME.xml"

        const val DEFAULT_DURATION_MINUTES = 60L
        const val DEFAULT_CADENCE_MINUTES = 15L
        const val MIN_DURATION_MINUTES = 15L
        const val MAX_DURATION_MINUTES = 180L
        private const val MIN_CADENCE_MINUTES = 15L

        private const val KEY_STARTED_AT = "started_at_ms"
        private const val KEY_ENDS_AT = "ends_at_ms"
        private const val KEY_CADENCE_MINUTES = "cadence_minutes"
        private const val KEY_PREFERRED_DURATION_MINUTES = "preferred_duration_minutes"
    }
}

internal fun normalizeMonitoringDuration(durationMinutes: Long): Long {
    return durationMinutes.coerceIn(
        MonitoringSessionStore.MIN_DURATION_MINUTES,
        MonitoringSessionStore.MAX_DURATION_MINUTES
    )
}
