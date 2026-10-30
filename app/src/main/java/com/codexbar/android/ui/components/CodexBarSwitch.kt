package com.codexbar.android.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.codexbar.android.core.domain.model.AppThemeStyle
import com.codexbar.android.ui.theme.LocalCodexBarThemeProfile

/** Keeps the native switch's input, state announcements and motion with Glisse's green track. */
@Composable
fun CodexBarSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val profile = LocalCodexBarThemeProfile.current
    if (profile.style != AppThemeStyle.LIQUID_GLASS) {
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier)
        return
    }
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedTrackColor = if (profile.isDark) Color(0xFF30D158) else Color(0xFF34C759),
            uncheckedTrackColor = if (profile.isDark) Color(0xFF555563) else Color(0xFFB8BBC6),
            checkedBorderColor = Color.Transparent,
            uncheckedBorderColor = Color.Transparent,
            checkedThumbColor = Color.White.copy(alpha = 0.90f),
            uncheckedThumbColor = Color.White.copy(alpha = 0.90f)
        ),
        thumbContent = {
            GlassSurface(
                backdrop = LocalGlassCardBackdrop.current,
                shape = CircleShape,
                tint = Color.White.copy(alpha = 0.60f),
                fallback = Color.White,
                rim = Color.White.copy(alpha = 0.90f),
                blurRadius = 1.dp,
                refraction = 0.6f,
                elevation = 1.dp,
                modifier = Modifier.size(22.dp)
            ) {}
        }
    )
}
