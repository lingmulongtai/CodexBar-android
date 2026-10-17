@file:OptIn(ExperimentalTextApi::class)

package com.codexbar.android.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.codexbar.android.R

// Bundled SIL OFL 1.1 fonts; provenance and licenses are listed in docs/fonts.md.
// Characters these fonts lack, such as Japanese, fall back to the system font.

private fun figtree(weight: Int) = Font(
    resId = R.font.figtree_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

val FigtreeFamily = FontFamily(
    figtree(400),
    figtree(500),
    figtree(600),
    figtree(700),
    figtree(800)
)

val PlexMonoFamily = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold)
)
