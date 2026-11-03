package com.codexbar.android.ui.theme

import com.codexbar.android.core.domain.model.DeveloperSettings
import org.junit.Assert.*
import org.junit.Test

class DeveloperSettingsSessionTest {
    private val a = DeveloperSettings(animationSpeed = .5f)
    private val b = DeveloperSettings(animationSpeed = 2f)
    @Test fun `older save never replaces a newer active preview`() {
        val session = DeveloperSettingsSession(); val owner = Any(); session.begin(owner)
        val id = session.requestSave(owner, a)!!
        session.preview(owner, b); session.receiveSaved(a); session.completeSave(id, a, false)
        assertEquals(b, session.settings)
        session.finish(owner); assertEquals(a, session.settings)
    }
    @Test fun `leaving during a write discards only the unsaved drag`() {
        val session = DeveloperSettingsSession(); val owner = Any(); session.begin(owner)
        val id = session.requestSave(owner, a)!!; session.preview(owner, b); session.finish(owner)
        assertEquals(a, session.settings)
        session.receiveSaved(a); session.completeSave(id, a, false)
        assertEquals(a, session.settings)
    }
    @Test fun `stale completions and stale screen owners cannot overwrite the latest edit`() {
        val session = DeveloperSettingsSession(); val old = Any(); val current = Any(); session.begin(old)
        val first = session.requestSave(old, a)!!; session.begin(current)
        val second = session.requestSave(current, b)!!; session.finish(old)
        session.receiveSaved(a); session.completeSave(first, a, false); assertEquals(b, session.settings)
        session.finish(current); assertEquals(b, session.settings)
        session.receiveSaved(b); session.completeSave(second, b, false); assertEquals(b, session.settings)
    }
}
