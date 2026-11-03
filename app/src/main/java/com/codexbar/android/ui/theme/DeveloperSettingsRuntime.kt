package com.codexbar.android.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.WindowRecomposerFactory
import androidx.compose.ui.platform.WindowRecomposerPolicy
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import com.codexbar.android.core.domain.model.AppAnimationSpeed
import com.codexbar.android.core.domain.model.DeveloperSettings
import com.codexbar.android.core.security.EncryptedPrefsManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal class AppMotionDurationScale(private val readSpeed: (() -> Float)? = null) : MotionDurationScale {
    var speed by mutableStateOf(AppAnimationSpeed.DEFAULT)
    override val scaleFactor: Float get() = AppAnimationSpeed.durationScale(readSpeed?.invoke() ?: speed)
}

/** One process-owned policy covers activities, dialogs and widget configuration compositions. */
@Singleton
class DeveloperSettingsRuntime @Inject constructor(private val prefs: EncryptedPrefsManager) {
    private val session = DeveloperSettingsSession()
    val settings: DeveloperSettings get() = session.settings
    val saveFailed: Boolean get() = session.saveFailed
    private val motion = AppMotionDurationScale { settings.animationSpeed }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @OptIn(InternalComposeUiApi::class)
    fun install() {
        // Keep Compose's lifecycle/frame-clock management; replace only the duration-scale element.
        WindowRecomposerPolicy.setFactory(WindowRecomposerFactory { view ->
            view.createLifecycleAwareWindowRecomposer(coroutineContext = motion)
        })
        scope.launch { prefs.developerSettings.collect(session::receiveSaved) }
    }

    fun beginPreview(owner: Any) = session.begin(owner)
    fun preview(owner: Any, value: DeveloperSettings) = session.preview(owner, value)
    fun finishPreview(owner: Any) = session.finish(owner)
    fun save(owner: Any, value: DeveloperSettings) {
        val id = session.requestSave(owner, value) ?: return
        // The process owns the write so leaving the settings screen cannot cancel a committed drag.
        scope.launch {
            try {
                prefs.setDeveloperSettings(value)
                session.completeSave(id, prefs.developerSettings.value, failed = false)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: java.io.IOException) {
                session.completeSave(id, prefs.developerSettings.value, failed = true)
            }
        }
    }
}
