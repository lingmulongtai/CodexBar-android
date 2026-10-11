package com.codexbar.android.core.widget

import com.codexbar.android.core.domain.model.AiService

internal object WidgetSelectionPolicy {
    fun choices(connected: List<AiService>, configured: List<AiService>): List<AiService> =
        AiService.entries.filter { it in connected || it in configured }

    fun initialSelection(connected: List<AiService>, configured: List<AiService>, hasConfiguration: Boolean): List<AiService> =
        if (hasConfiguration) configured else connected

    fun canSave(selected: List<AiService>, hasConfiguration: Boolean): Boolean =
        hasConfiguration || selected.isNotEmpty()
}
