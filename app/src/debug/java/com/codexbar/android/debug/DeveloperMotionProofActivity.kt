package com.codexbar.android.debug

import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import com.codexbar.android.core.domain.model.AppAnimationSpeed
import com.codexbar.android.core.domain.model.DeveloperSettings
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.ui.theme.CodexBarTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Opt-in proof surface for a disposable emulator. Never included in the release APK. */
@AndroidEntryPoint
class DeveloperMotionProofActivity : AppCompatActivity() {
    @Inject lateinit var prefs: EncryptedPrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(intent.getBooleanExtra("seed_demo", false)) { "Use only on a disposable demo emulator" }
        lifecycleScope.launch {
            prefs.warmCache()
            var settings = if (intent.getBooleanExtra("reset_dev", false)) DeveloperSettings() else prefs.developerSettings.value
            if (intent.hasExtra("app_speed")) settings = settings.copy(animationSpeed = intent.getFloatExtra("app_speed", 1f))
            if (intent.hasExtra("frost")) settings = settings.copy(containers = settings.containers.copy(frost = intent.getFloatExtra("frost", 16f)))
            if (intent.hasExtra("opacity")) settings = settings.copy(containers = settings.containers.copy(tint = intent.getFloatExtra("opacity", .74f)))
            prefs.setDeveloperSettings(settings)
            val saved = prefs.developerSettings.value
            setContent {
                CodexBarTheme(darkTheme = true) {
                    var result by remember { mutableStateOf("Measuring app animation…") }
                    var showDialog by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        repeat(2) { withFrameNanos {} }
                        val scale = coroutineContext[MotionDurationScale]?.scaleFactor
                        check(scale != null && kotlin.math.abs(scale - AppAnimationSpeed.durationScale(saved.animationSpeed)) < .0001f)
                        val value = Animatable(0f)
                        val start = SystemClock.elapsedRealtime()
                        value.animateTo(1f, tween(400, easing = LinearEasing))
                        val elapsed = SystemClock.elapsedRealtime() - start
                        val expected = AppAnimationSpeed.durationMillis(400, saved.animationSpeed)
                        check(elapsed in (expected - 100)..(expected + 250)) { "Unexpected animation duration $elapsed, expected $expected" }
                        val system = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
                        result = "PASS system=$system app_speed=${saved.animationSpeed} elapsed_ms=$elapsed scale=$scale frost=${saved.containers.frost}"
                        Log.i("CodexBarDevProof", result)
                        showDialog = true
                    }
                    Column(Modifier.padding(28.dp)) { Text(result) }
                    if (showDialog) Dialog(onDismissRequest = { showDialog = false }) {
                        LaunchedEffect(Unit) {
                            val scale = coroutineContext[MotionDurationScale]?.scaleFactor
                            check(scale == AppAnimationSpeed.durationScale(saved.animationSpeed))
                            Log.i("CodexBarDevProof", "PASS dialog_scale=$scale")
                        }
                        Text(result)
                    }
                }
            }
        }
    }
}
