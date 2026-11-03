package com.codexbar.android.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.codexbar.android.core.domain.model.DeveloperSettings

/** Saved I/O may finish while a newer slider draft is being previewed. Keep those states separate. */
internal class DeveloperSettingsSession(initial: DeveloperSettings = DeveloperSettings()) {
    var settings by mutableStateOf(initial.normalized())
        private set
    var saveFailed by mutableStateOf(false)
        private set
    private var saved = settings
    private var owner: Any? = null
    private var dirty = false
    private var generation = 0L
    private var pending: Pair<Long, DeveloperSettings>? = null

    fun receiveSaved(value: DeveloperSettings) {
        saved = value.normalized()
        if ((owner == null || !dirty) && pending == null) settings = saved
    }
    fun begin(token: Any) { if (owner == null) dirty = false; owner = token }
    fun preview(token: Any, value: DeveloperSettings) {
        if (owner === token) { dirty = true; settings = value.normalized(); saveFailed = false }
    }
    fun requestSave(token: Any, value: DeveloperSettings): Long? {
        if (owner !== token) return null
        preview(token, value)
        generation++
        pending = generation to settings
        return generation
    }
    fun completeSave(id: Long, value: DeveloperSettings, failed: Boolean) {
        if (pending?.first != id) return
        saved = value.normalized()
        pending = null
        saveFailed = failed
        if (owner == null) settings = saved
    }
    fun finish(token: Any) {
        if (owner !== token) return
        owner = null
        dirty = false
        settings = pending?.second ?: saved
    }
}
