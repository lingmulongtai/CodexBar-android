package com.codexbar.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.*
import com.codexbar.android.ui.components.*

@Composable
internal fun DeveloperSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = LocalDeveloperSettings.current
    val latestSettings by rememberUpdatedState(settings)
    val owner = remember { Any() }
    var group by remember { mutableStateOf(GlassSurfaceGroup.CONTAINERS) }
    val preview: (DeveloperSettings) -> Unit = { viewModel.previewDeveloperSettings(owner, it) }
    val save: (DeveloperSettings) -> Unit = { viewModel.saveDeveloperSettings(owner, it) }
    DisposableEffect(viewModel, owner) {
        viewModel.beginDeveloperSettingsPreview(owner)
        onDispose { viewModel.finishDeveloperSettingsPreview(owner) }
    }
    GlassHeaderOverlay(header = {
        ThemedTopBar(stringResource(R.string.developer_settings_title), navigationIcon = {
            CodexBarIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back)) }
        })
    }) { header ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(top = header + 8.dp, bottom = LocalBottomChromeInset.current + 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CodexBarCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.developer_animation_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.developer_animation_description), style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.developer_animation_speed))
                        Text(stringResource(R.string.developer_speed_value, settings.animationSpeed), fontWeight = FontWeight.SemiBold)
                    }
                    CodexBarSlider(settings.animationSpeed, { preview(settings.copy(animationSpeed = it)) }, Modifier.fillMaxWidth(),
                        enabled = state.preferencesLoaded, valueRange = AppAnimationSpeed.MIN..AppAnimationSpeed.MAX, steps = 10,
                        onValueChangeFinished = { save(latestSettings) })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(.5f, 1f, 2f).forEach { value -> CodexBarFilterChip(settings.animationSpeed == value,
                            { save(settings.copy(animationSpeed = value)) }, label = { Text(stringResource(R.string.developer_speed_value, value)) }) }
                    }
                }
            }
            Text(stringResource(R.string.developer_glass_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassSurfaceGroup.entries.forEach { target ->
                    CodexBarFilterChip(group == target, { group = target }, label = { Text(stringResource(target.label())) })
                }
            }
            GlassSurface(LocalGlassCardBackdrop.current, RoundedCornerShape(24.dp), Color.White.copy(alpha = .12f),
                Color(0x77383848), Color.White.copy(alpha = .3f), Modifier.fillMaxWidth().height(150.dp),
                blurRadius = 16.dp, refraction = .45f, elevation = 0.dp, tuningGroup = group) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Aa", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.developer_glass_preview), style = MaterialTheme.typography.labelLarge)
                }
            }
            CodexBarCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(stringResource(R.string.developer_glass_description), style = MaterialTheme.typography.bodyMedium)
                    GlassParameter.entries.forEach { parameter ->
                        val override = settings.tuning(group).value(parameter)
                        val value = override ?: defaultGlassValue(group, parameter)
                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(parameter.label()), style = MaterialTheme.typography.titleSmall)
                                Text(if (override == null) stringResource(R.string.developer_glass_default)
                                    else stringResource(if (parameter == GlassParameter.FROST) R.string.developer_blur_value else R.string.developer_parameter_value, value),
                                    style = MaterialTheme.typography.labelLarge)
                            }
                            CodexBarSlider(value, { next -> preview(settings.withTuning(group, settings.tuning(group).withValue(parameter, next))) },
                                Modifier.fillMaxWidth(), enabled = state.preferencesLoaded, valueRange = parameter.min..parameter.max,
                                onValueChangeFinished = { save(latestSettings) })
                        }
                    }
                    CodexBarOutlinedButton(onClick = { save(settings.withTuning(group, GlassTuning())) }) {
                        Text(stringResource(R.string.developer_glass_reset_group))
                    }
                }
            }
            if (viewModel.developerSettingsSaveFailed) Text(stringResource(R.string.developer_settings_save_failed), color = MaterialTheme.colorScheme.error)
            CodexBarOutlinedButton(onClick = { save(DeveloperSettings()) }) { Text(stringResource(R.string.developer_settings_reset_all)) }
            WallpaperFooter()
        }
    }
}

private fun GlassSurfaceGroup.label(): Int = when (this) {
    GlassSurfaceGroup.CONTAINERS -> R.string.developer_glass_containers
    GlassSurfaceGroup.HEADER -> R.string.developer_glass_header
    GlassSurfaceGroup.NAVIGATION -> R.string.developer_glass_navigation
    GlassSurfaceGroup.CONTROLS -> R.string.developer_glass_controls
}

private fun GlassParameter.label(): Int = when (this) {
    GlassParameter.FROST -> R.string.developer_glass_frost
    GlassParameter.BEND -> R.string.developer_glass_bend
    GlassParameter.DISPERSION -> R.string.developer_glass_dispersion
    GlassParameter.RIM -> R.string.developer_glass_rim
    GlassParameter.SHINE -> R.string.developer_glass_shine
    GlassParameter.SATURATION -> R.string.developer_glass_saturation
    GlassParameter.TINT -> R.string.developer_glass_tint
    GlassParameter.REACH -> R.string.developer_glass_reach
}

private fun defaultGlassValue(group: GlassSurfaceGroup, parameter: GlassParameter): Float = when (parameter) {
    GlassParameter.FROST -> when (group) { GlassSurfaceGroup.HEADER -> 0f; GlassSurfaceGroup.NAVIGATION -> 3f; GlassSurfaceGroup.CONTROLS -> 5f; else -> 16f }
    GlassParameter.BEND, GlassParameter.REACH -> 1f
    GlassParameter.DISPERSION -> .4f
    GlassParameter.RIM -> if (group == GlassSurfaceGroup.HEADER) .13f else .18f
    GlassParameter.SHINE -> if (group == GlassSurfaceGroup.HEADER) 0f else .14f
    GlassParameter.SATURATION -> if (group == GlassSurfaceGroup.HEADER) 1f else 1.12f
    GlassParameter.TINT -> when (group) { GlassSurfaceGroup.HEADER -> 0f; GlassSurfaceGroup.CONTAINERS -> .74f; else -> .15f }
}
