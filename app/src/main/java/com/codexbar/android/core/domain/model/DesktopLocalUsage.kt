package com.codexbar.android.core.domain.model

/** Provider-wide Windows log totals, not account-scoped quota or billed charges. */
data class DesktopLocalUsage(
    val updatedAtEpochSeconds: Long, val todayDate: String,
    val todayTokens: Long?, val thirtyDayTokens: Long?, val periodTokens: Long?,
    val todayCostUsd: Double?, val thirtyDayCostUsd: Double?, val periodCostUsd: Double?,
    val reportingPeriod: String?, val topModel: String?, val incompleteRequestCount: Int?
)
