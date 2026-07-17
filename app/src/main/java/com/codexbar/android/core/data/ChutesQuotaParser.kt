package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.UsageWindow
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal object ChutesQuotaParser {
    data class ParsedQuota(
        val windows: List<UsageWindow>,
        val tier: String?
    )

    fun parse(root: JsonElement): ParsedQuota {
        val candidates = mutableListOf<UsageWindow>()
        collectWindows(root, emptyList(), candidates)
        val windows = candidates
            .mapIndexed { index, window ->
                if (window.label.isBlank()) window.copy(label = "Quota ${index + 1}") else window
            }
            .distinctBy { "${it.label}|${it.windowDurationSeconds}|${it.resetsAt}" }
            .take(MAX_WINDOWS)
        return ParsedQuota(
            windows = windows,
            tier = findTier(root)?.takeIf { it.length <= MAX_LABEL_LENGTH }
        )
    }

    private fun collectWindows(
        element: JsonElement,
        path: List<String>,
        output: MutableList<UsageWindow>
    ) {
        if (output.size >= MAX_WINDOWS) return
        when (element) {
            is JsonObject -> {
                parseWindow(element, path)?.let {
                    output += it
                    return
                }
                element.entries.forEach { (key, child) ->
                    collectWindows(child, path + key, output)
                }
            }
            is JsonArray -> element.forEachIndexed { index, child ->
                collectWindows(child, path + index.toString(), output)
            }
            else -> Unit
        }
    }

    private fun parseWindow(payload: JsonObject, path: List<String>): UsageWindow? {
        val values = payload.entries.associate { normalizeKey(it.key) to it.value }
        val limit = values.firstNumber(LIMIT_KEYS)
        val used = values.firstNumber(USED_KEYS)
        val remaining = values.firstNumber(REMAINING_KEYS)
        val percentUsed = values.firstNumber(PERCENT_USED_KEYS)?.normalizedPercent()
        val percentRemaining = values.firstNumber(PERCENT_REMAINING_KEYS)?.normalizedPercent()

        val utilization = when {
            percentUsed != null -> percentUsed / 100.0
            percentRemaining != null -> 1.0 - (percentRemaining / 100.0)
            limit != null && limit.isFinite() && limit > 0.0 -> {
                val resolvedUsed = used ?: remaining?.let { limit - it }
                resolvedUsed?.takeIf(Double::isFinite)?.div(limit)
            }
            used != null && remaining != null && used + remaining > 0.0 -> used / (used + remaining)
            else -> null
        }?.takeIf(Double::isFinite)?.coerceIn(0.0, 1.0) ?: return null

        val pathText = path.joinToString(" ").lowercase()
        val explicitLabel = values.firstString(LABEL_KEYS)
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it.length <= MAX_LABEL_LENGTH && it.none(Char::isISOControl) }
        val labelSource = listOfNotNull(explicitLabel, pathText).joinToString(" ").lowercase()
        val label = when {
            labelSource.contains("rolling") || labelSource.contains("4h") ||
                labelSource.contains("four hour") -> "4-Hour"
            labelSource.contains("month") || labelSource.contains("subscription") -> "Monthly"
            explicitLabel != null -> explicitLabel
            else -> ""
        }
        val duration = when (label) {
            "4-Hour" -> FOUR_HOURS_SECONDS
            else -> values.windowDurationSeconds()
        }

        return UsageWindow(
            label = label,
            utilization = utilization,
            resetsAt = values.firstElement(RESET_KEYS)?.toInstantOrNull(),
            windowDurationSeconds = duration
        )
    }

    private fun Map<String, JsonElement>.windowDurationSeconds(): Long? {
        val minutes = firstNumber(WINDOW_MINUTE_KEYS)
        val hours = firstNumber(WINDOW_HOUR_KEYS)
        val days = firstNumber(WINDOW_DAY_KEYS)
        val seconds = when {
            minutes != null -> minutes * 60.0
            hours != null -> hours * 60.0 * 60.0
            days != null -> days * 24.0 * 60.0 * 60.0
            else -> return null
        }
        return seconds.takeIf { it.isFinite() && it > 0.0 && it <= Long.MAX_VALUE.toDouble() }
            ?.toLong()
    }

    private fun findTier(element: JsonElement): String? = when (element) {
        is JsonObject -> {
            val values = element.entries.associate { normalizeKey(it.key) to it.value }
            values.firstString(TIER_KEYS) ?: element.values.firstNotNullOfOrNull(::findTier)
        }
        is JsonArray -> element.firstNotNullOfOrNull(::findTier)
        else -> null
    }

    private fun Map<String, JsonElement>.firstElement(keys: Set<String>): JsonElement? =
        keys.firstNotNullOfOrNull { this[it] }

    private fun Map<String, JsonElement>.firstNumber(keys: Set<String>): Double? =
        firstElement(keys).numberOrNull()

    private fun Map<String, JsonElement>.firstString(keys: Set<String>): String? =
        (firstElement(keys) as? JsonPrimitive)
            ?.takeUnless { it is JsonNull }
            ?.content

    private fun JsonElement?.numberOrNull(): Double? {
        val primitive = this as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        return primitive.content
            .trim()
            .replace(",", "")
            .replace("$", "")
            .replace("%", "")
            .toDoubleOrNull()
            ?.takeIf(Double::isFinite)
    }

    private fun JsonElement.toInstantOrNull(): Instant? {
        val primitive = this as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        val value = primitive.content.trim()
        value.toDoubleOrNull()?.takeIf(Double::isFinite)?.let { epoch ->
            val seconds = if (epoch > EPOCH_MILLIS_THRESHOLD) epoch / 1000.0 else epoch
            return runCatching { Instant.ofEpochSecond(seconds.toLong()) }.getOrNull()
        }
        return runCatching { Instant.parse(value) }.getOrNull()
    }

    private fun Double.normalizedPercent(): Double? {
        if (!isFinite()) return null
        val percent = if (kotlin.math.abs(this) < 1.0) this * 100.0 else this
        return percent.coerceIn(0.0, 100.0)
    }

    private fun normalizeKey(value: String): String =
        value.lowercase().filter(Char::isLetterOrDigit)

    private val LIMIT_KEYS = setOf(
        "limit", "cap", "max", "maximum", "quota", "quotalimit", "monthlycap",
        "monthlylimit", "requestlimit", "tokenlimit", "hardlimit", "total"
    )
    private val USED_KEYS = setOf(
        "used", "usage", "usedamount", "consumed", "consumedamount", "current",
        "currentusage", "requests", "requestcount", "tokens", "tokenusage", "monthlyusage"
    )
    private val REMAINING_KEYS = setOf(
        "remaining", "available", "balance", "left", "remainingamount", "availableamount"
    )
    private val PERCENT_USED_KEYS = setOf(
        "percentused", "usagepercent", "usedpercent", "utilization", "utilizationpercent"
    )
    private val PERCENT_REMAINING_KEYS = setOf("percentremaining", "remainingpercent")
    private val LABEL_KEYS = setOf(
        "label", "name", "title", "type", "quotatype", "period", "window", "windowname"
    )
    private val RESET_KEYS = setOf(
        "resetat", "resetsat", "resettime", "nextresetat", "renewsat", "renewalat",
        "periodend", "currentperiodend", "expiresat", "windowend", "endtime"
    )
    private val TIER_KEYS = setOf("planname", "plan", "tier", "subscriptionplan", "subscriptiontier")
    private val WINDOW_MINUTE_KEYS = setOf("windowminutes", "periodminutes", "durationminutes")
    private val WINDOW_HOUR_KEYS = setOf("windowhours", "periodhours", "durationhours")
    private val WINDOW_DAY_KEYS = setOf("windowdays", "perioddays", "durationdays")

    private const val FOUR_HOURS_SECONDS = 4L * 60L * 60L
    private const val EPOCH_MILLIS_THRESHOLD = 100_000_000_000.0
    private const val MAX_WINDOWS = 8
    private const val MAX_LABEL_LENGTH = 64
}
