package com.codexbar.android.core.presentation

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.codexbar.android.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

interface QuotaPresentationText {
    val locale: Locale

    fun window(number: Int): String
    fun usedHidden(): String
    fun percentUsed(percent: Int): String
    fun remainingHidden(): String
    fun percentRemaining(percent: Int): String
    fun collectingPaceHistory(): String
    fun credits(): String
    fun currencyUsed(currency: String, amount: String): String
    fun limitHidden(): String
    fun currencyLimit(currency: String, amount: String): String
    fun currencyRemaining(currency: String, amount: String): String
    fun noFreshData(): String
    fun reauthenticationRequired(): String
    fun authenticationFailed(): String
    fun notConnected(): String
    fun networkUnavailable(): String
    fun rateLimitedUntil(retryAt: Instant): String
    fun rateLimited(): String
    fun providerResponseInvalid(): String
    fun providerUnavailable(): String
    fun justNow(): String
    fun minutesAgo(minutes: Long): String
    fun hoursAgo(hours: Long): String
    fun daysAgo(days: Long): String
    fun resetsInDays(days: Long, hours: Long): String
    fun resetsInHours(hours: Long, minutes: Long): String
    fun resetsInMinutes(minutes: Long): String
    fun noResetTime(): String
    fun waitingForNextWindow(): String
    fun paceStable(): String
    fun roomToUseMore(): String
    fun reserve(percent: Int): String
    fun noRecentIncrease(): String
    fun mayRunOutBeforeReset(): String
    fun paceTooFast(): String
    fun paceAtRisk(): String
    fun paceOnTrack(): String
    fun paceUnavailable(): String
    fun cycleProgress(usedPercent: Int, elapsedPercent: String): String
    fun usageRate(percentPerHour: String): String
    fun paceMultiplier(multiplier: String): String
    fun forecastAtReset(projectedPercent: Int): String
    fun limitNotProvidedTitle(hours: Long): String
    fun limitNotProvidedMessage(): String
}

object EnglishQuotaPresentationText : QuotaPresentationText {
    override val locale: Locale
        get() = Locale.ENGLISH

    override fun window(number: Int): String = "Window $number"
    override fun usedHidden(): String = "Used hidden"
    override fun percentUsed(percent: Int): String = "$percent% used"
    override fun remainingHidden(): String = "Remaining hidden"
    override fun percentRemaining(percent: Int): String = "$percent% left"
    override fun collectingPaceHistory(): String = "Collecting pace history"
    override fun credits(): String = "Credits"
    override fun currencyUsed(currency: String, amount: String): String = "$currency $amount used"
    override fun limitHidden(): String = "Limit hidden"
    override fun currencyLimit(currency: String, amount: String): String = "$currency $amount limit"
    override fun currencyRemaining(currency: String, amount: String): String = "$currency $amount left"
    override fun noFreshData(): String = "No fresh data"
    override fun reauthenticationRequired(): String = "Reauthentication required"
    override fun authenticationFailed(): String = "Authentication failed"
    override fun notConnected(): String = "Not connected"
    override fun networkUnavailable(): String = "Network unavailable"
    override fun rateLimitedUntil(retryAt: Instant): String = "Rate limited until $retryAt"
    override fun rateLimited(): String = "Rate limited"
    override fun providerResponseInvalid(): String = "Provider response could not be parsed"
    override fun providerUnavailable(): String = "Provider unavailable"
    override fun justNow(): String = "just now"
    override fun minutesAgo(minutes: Long): String = "${minutes}m ago"
    override fun hoursAgo(hours: Long): String = "${hours}h ago"
    override fun daysAgo(days: Long): String = "${days}d ago"
    override fun resetsInDays(days: Long, hours: Long): String = "Resets in ${days}d ${hours}h"
    override fun resetsInHours(hours: Long, minutes: Long): String = "Resets in ${hours}h ${minutes}m"
    override fun resetsInMinutes(minutes: Long): String = "Resets in ${minutes}m"
    override fun noResetTime(): String = "No reset time"
    override fun waitingForNextWindow(): String = "Waiting for next window"
    override fun paceStable(): String = "Pace stable"
    override fun roomToUseMore(): String = "You can use more"
    override fun reserve(percent: Int): String = "$percent% reserve"
    override fun noRecentIncrease(): String = "No recent increase"
    override fun mayRunOutBeforeReset(): String = "May run out before reset"
    override fun paceTooFast(): String = "Usage pace is too fast"
    override fun paceAtRisk(): String = "A little faster than ideal"
    override fun paceOnTrack(): String = "You're on a good pace"
    override fun paceUnavailable(): String = "Pace unavailable"
    override fun cycleProgress(usedPercent: Int, elapsedPercent: String): String =
        "Used $usedPercent% / $elapsedPercent% of window elapsed"
    override fun usageRate(percentPerHour: String): String = "Avg $percentPerHour%/h"
    override fun paceMultiplier(multiplier: String): String = "$multiplier× target pace"
    override fun forecastAtReset(projectedPercent: Int): String =
        "At this pace: $projectedPercent% by reset"
    override fun limitNotProvidedTitle(hours: Long): String =
        "$hours-hour limit isn't active right now!"
    override fun limitNotProvidedMessage(): String =
        "No short-term window was returned, so you can focus on the longer window and keep building."
}

class AndroidQuotaPresentationText(
    private val context: Context
) : QuotaPresentationText {
    private val languageContext: Context
        get() = ContextCompat.getContextForLanguage(context)

    override val locale: Locale
        get() = languageContext.resources.configuration.locales[0]

    override fun window(number: Int): String = string(R.string.presentation_window, number)
    override fun usedHidden(): String = string(R.string.presentation_used_hidden)
    override fun percentUsed(percent: Int): String = string(R.string.presentation_percent_used, percent)
    override fun remainingHidden(): String = string(R.string.presentation_remaining_hidden)
    override fun percentRemaining(percent: Int): String = string(R.string.quota_remaining_percent, percent)
    override fun collectingPaceHistory(): String = string(R.string.pace_collecting_history)
    override fun credits(): String = string(R.string.presentation_credits)
    override fun currencyUsed(currency: String, amount: String): String =
        string(R.string.presentation_currency_used, currency, amount)
    override fun limitHidden(): String = string(R.string.presentation_limit_hidden)
    override fun currencyLimit(currency: String, amount: String): String =
        string(R.string.presentation_currency_limit, currency, amount)
    override fun currencyRemaining(currency: String, amount: String): String =
        string(R.string.presentation_currency_remaining, currency, amount)
    override fun noFreshData(): String = string(R.string.presentation_no_fresh_data)
    override fun reauthenticationRequired(): String =
        string(R.string.presentation_reauthentication_required)
    override fun authenticationFailed(): String = string(R.string.presentation_authentication_failed)
    override fun notConnected(): String = string(R.string.status_not_connected)
    override fun networkUnavailable(): String = string(R.string.presentation_network_unavailable)
    override fun rateLimitedUntil(retryAt: Instant): String = string(
        R.string.presentation_rate_limited_until,
        formatInstant(retryAt)
    )
    override fun rateLimited(): String = string(R.string.status_rate_limited)
    override fun providerResponseInvalid(): String = string(R.string.presentation_provider_response_invalid)
    override fun providerUnavailable(): String = string(R.string.presentation_provider_unavailable)
    override fun justNow(): String = string(R.string.presentation_just_now)
    override fun minutesAgo(minutes: Long): String = string(R.string.presentation_minutes_ago, minutes)
    override fun hoursAgo(hours: Long): String = string(R.string.presentation_hours_ago, hours)
    override fun daysAgo(days: Long): String = string(R.string.presentation_days_ago, days)
    override fun resetsInDays(days: Long, hours: Long): String =
        string(R.string.presentation_resets_days, days, hours)
    override fun resetsInHours(hours: Long, minutes: Long): String =
        string(R.string.presentation_resets_hours, hours, minutes)
    override fun resetsInMinutes(minutes: Long): String =
        string(R.string.presentation_resets_minutes, minutes)
    override fun noResetTime(): String = string(R.string.pace_no_reset_time)
    override fun waitingForNextWindow(): String = string(R.string.pace_waiting_next_window)
    override fun paceStable(): String = string(R.string.pace_stable)
    override fun roomToUseMore(): String = string(R.string.pace_room_to_use_more)
    override fun reserve(percent: Int): String = string(R.string.pace_reserve, percent)
    override fun noRecentIncrease(): String = string(R.string.pace_no_recent_increase)
    override fun mayRunOutBeforeReset(): String = string(R.string.pace_may_run_out)
    override fun paceTooFast(): String = string(R.string.pace_too_fast)
    override fun paceAtRisk(): String = string(R.string.pace_at_risk)
    override fun paceOnTrack(): String = string(R.string.pace_on_track)
    override fun paceUnavailable(): String = string(R.string.pace_unavailable)
    override fun cycleProgress(usedPercent: Int, elapsedPercent: String): String =
        string(R.string.pace_cycle_progress, usedPercent, elapsedPercent)
    override fun usageRate(percentPerHour: String): String =
        string(R.string.pace_usage_rate, percentPerHour)
    override fun paceMultiplier(multiplier: String): String =
        string(R.string.pace_multiplier, multiplier)
    override fun forecastAtReset(projectedPercent: Int): String =
        string(R.string.pace_forecast, projectedPercent)
    override fun limitNotProvidedTitle(hours: Long): String =
        string(R.string.insight_limit_not_provided_title, hours)
    override fun limitNotProvidedMessage(): String =
        string(R.string.insight_limit_not_provided_message)

    private fun string(@StringRes resourceId: Int, vararg formatArgs: Any): String {
        return languageContext.getString(resourceId, *formatArgs)
    }

    private fun formatInstant(instant: Instant): String {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }
}
