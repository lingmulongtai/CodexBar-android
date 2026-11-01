package com.codexbar.android.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import com.codexbar.android.R

@Composable
fun AppSettingsAction(onClick: () -> Unit) {
    CodexBarIconButton(onClick = onClick) {
        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings_title),
            tint = Color.White.copy(alpha = 0.85f))
    }
}
