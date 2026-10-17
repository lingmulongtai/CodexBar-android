package com.codexbar.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codexbar.android.core.domain.model.AppThemeStyle
import com.codexbar.android.ui.theme.LocalCodexBarThemeProfile
import com.codexbar.android.ui.theme.PlexMonoFamily

/**
 * A screen title in the design's voice: Expressive's top app bar, Liquid Glass's large title and
 * Instrument's monospaced heading.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemedTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    val profile = LocalCodexBarThemeProfile.current
    when (profile.style) {
        AppThemeStyle.MATERIAL_3 -> TopAppBar(
            title = {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = profile.topBarContainerColor,
                scrolledContainerColor = profile.topBarContainerColor
            )
        )
        AppThemeStyle.LIQUID_GLASS -> Row(
            modifier = modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            navigationIcon()
            Text(
                title,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.4).sp,
                fontFamily = MaterialTheme.typography.displaySmall.fontFamily,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            actions()
        }
        AppThemeStyle.INSTRUMENT -> Row(
            modifier = modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .heightIn(min = 60.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            navigationIcon()
            Text(
                title,
                fontFamily = PlexMonoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            actions()
        }
    }
}
