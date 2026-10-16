package com.codexbar.android.core.domain.model

import java.time.Instant

data class DesktopQuotaSample(val at: Instant, val utilization: Double, val resetsAt: Instant?)
data class DesktopQuotaSeries(val label: String, val samples: List<DesktopQuotaSample>)
data class DesktopSpendDay(val day: String, val tokens: Long?, val costUsd: Double?, val isScanned: Boolean)
/** Provider-wide PC log aggregates. Monetary figures are reference usage costs, not subscription bills. */
data class DesktopSpend(
    val updatedAtEpochSeconds: Long, val reportingPeriod: String, val timeZone: String,
    val currency: String, val refreshing: Boolean, val sevenDay: Double?, val thirtyDay: Double?, val periodCost: Double?,
    val sevenDayTokens: Long?, val thirtyDayTokens: Long?, val periodTokens: Long?,
    val tokensLowerBound: Boolean, val daily: List<DesktopSpendDay>
)
