package com.codexbar.android.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.codexbar.android.GlassNavigationBar
import com.codexbar.android.NavigationEntry
import com.codexbar.android.core.domain.model.AppThemeStyle
import com.codexbar.android.ui.components.*
import com.codexbar.android.ui.theme.CodexBarTheme
import com.codexbar.android.ui.theme.LocalCodexBarThemeProfile

/** Isolated real input controls, without accounts, permission requests or persistent settings. */
class GlassControlsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val rtl = intent.getBooleanExtra("rtl", false)
        setContent {
            CodexBarTheme(style = AppThemeStyle.LIQUID_GLASS, darkTheme = false, dynamicColor = false) {
                CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    val backdrop = rememberGlassBackdrop()
                    var tab by remember { mutableIntStateOf(0) }
                    var tabCommits by remember { mutableIntStateOf(0) }
                    var checked by remember { mutableStateOf(true) }
                    var switchCommits by remember { mutableIntStateOf(0) }
                    var slider by remember { mutableFloatStateOf(50f) }
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().glassBackdropSource(backdrop).background(LocalCodexBarThemeProfile.current.backgroundBrush))
                        CompositionLocalProvider(LocalGlassCardBackdrop provides backdrop) {
                            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                Text("Glass control verification")
                                Text("Tab commits: $tabCommits")
                                Text("Switch commits: $switchCommits")
                                CodexBarSwitch(checked, { checked = it; switchCommits++ }, Modifier.semantics { contentDescription = "Interactive switch" })
                                CodexBarSwitch(true, {}, Modifier.semantics { contentDescription = "Disabled switch" }, enabled = false)
                                Text("Slider value: ${slider.toInt()}")
                                CodexBarSlider(slider, { slider = it }, Modifier.semantics { contentDescription = "Test slider" }, valueRange = 0f..100f, steps = 3)
                            }
                            GlassNavigationBar(listOf("First", "Second", "Third").mapIndexed { index, label ->
                                NavigationEntry(label, listOf(Icons.Rounded.Home, Icons.Rounded.BarChart, Icons.Rounded.Settings)[index], tab == index) {
                                    tab = index; tabCommits++
                                }
                            }, backdrop, null, null, Modifier.align(Alignment.BottomCenter))
                        }
                    }
                }
            }
        }
    }
}
