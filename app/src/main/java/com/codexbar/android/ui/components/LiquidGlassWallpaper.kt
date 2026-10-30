package com.codexbar.android.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.codexbar.android.R
import okhttp3.HttpUrl.Companion.toHttpUrl
import androidx.lifecycle.ViewModel
import com.codexbar.android.core.data.DailyWallpaperRepository
import com.codexbar.android.core.data.WallpaperState
import com.codexbar.android.ui.theme.LocalCodexBarThemeProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DailyWallpaperViewModel @Inject constructor(private val repository: DailyWallpaperRepository) : ViewModel() {
    val state = repository.state
    fun refresh(force: Boolean = false) = repository.refreshInBackground(force)
    suspend fun save(enabled: Boolean, replacementKey: String?) = repository.save(enabled, replacementKey)
}

@Composable
fun WallpaperAttribution(state: WallpaperState, modifier: Modifier = Modifier) {
    val photo = state.photo ?: return
    if (!state.preferences.enabled || state.bitmap == null) return
    val uri = LocalUriHandler.current
    val colors = MaterialTheme.colorScheme
    fun open(value: String) {
        uri.openUri(value.toHttpUrl().newBuilder().setQueryParameter("utm_source", "codexbar_android")
            .setQueryParameter("utm_medium", "referral").build().toString())
    }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(Modifier.background(colors.surface.copy(alpha = 0.90f), CircleShape).padding(horizontal = 10.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(photo.photographer, style = MaterialTheme.typography.labelSmall, color = colors.onSurface,
                modifier = Modifier.clickable { open(photo.profileUrl) })
            Text("·", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Text("Unsplash", style = MaterialTheme.typography.labelSmall, color = colors.onSurface,
                modifier = Modifier.clickable { open("https://unsplash.com") })
        }
    }
}

/** The same crop and scrim are used by the visible background and the card-only backdrop. */
@Composable
fun LiquidGlassWallpaper(state: WallpaperState, modifier: Modifier = Modifier) {
    val profile = LocalCodexBarThemeProfile.current
    Box(modifier.fillMaxSize().background(profile.backgroundBrush)) {
        if (state.preferences.enabled) state.bitmap?.let { bitmap ->
            Image(bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            val shade = if (profile.isDark) Color(0xFF07101F) else Color(0xFFF4F5FA)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                shade.copy(alpha = if (profile.isDark) 0.76f else 0.76f),
                shade.copy(alpha = if (profile.isDark) 0.34f else 0.26f),
                shade.copy(alpha = if (profile.isDark) 0.52f else 0.38f)
            ))))
        }
    }
}
