package com.lulu786.alternate.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.lulu786.alternate.R

/*
 * Google Sans Flex (OFL, licenses/GoogleSansFlex-OFL.txt), bundled as one variable font: wght 1-1000, ROND 0-100,
 * wdth 100-150; opsz pinned to 18 and slnt to 0 to cut it from 3.9 MB to ~435 KB.
 * shortcut: Android 7 (API 24-25) ignores variation settings and draws every weight at the default 400; bundle static instances if that matters.
 */
private fun flex(weight: Int, vararg extra: FontVariation.Setting) =
    Font(R.font.google_sans_flex, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight), *extra))

private val weights = listOf(400, 500, 600, 700, 800)
private val family = FontFamily(weights.map { flex(it) })
// Emphasized headings get the same full roundness as the avatar letters.
val RoundedFontFamily = FontFamily(weights.map { flex(it, FontVariation.Setting("ROND", 100f)) })

/** Fast-scroller letters: rounded and a little wide, regular and highlighted weights. */
val ScrubberFontFamily = FontFamily(listOf(500, 700).map { flex(it, FontVariation.width(125f), FontVariation.Setting("ROND", 100f)) })

/** Avatar initial, as in the M3 list sample: heaviest, widest, fully rounded. */
val AvatarLetterFont = FontFamily(flex(900, FontVariation.width(150f), FontVariation.Setting("ROND", 100f)))

private val base = Typography()
val AlternateTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = family),
    displayMedium = base.displayMedium.copy(fontFamily = family),
    displaySmall = base.displaySmall.copy(fontFamily = family),
    headlineLarge = base.headlineLarge.copy(fontFamily = family),
    headlineMedium = base.headlineMedium.copy(fontFamily = family),
    headlineSmall = base.headlineSmall.copy(fontFamily = family),
    titleLarge = base.titleLarge.copy(fontFamily = family),
    titleMedium = base.titleMedium.copy(fontFamily = family),
    titleSmall = base.titleSmall.copy(fontFamily = family),
    bodyLarge = base.bodyLarge.copy(fontFamily = family),
    bodyMedium = base.bodyMedium.copy(fontFamily = family),
    bodySmall = base.bodySmall.copy(fontFamily = family),
    labelLarge = base.labelLarge.copy(fontFamily = family),
    labelMedium = base.labelMedium.copy(fontFamily = family),
    labelSmall = base.labelSmall.copy(fontFamily = family),
    displayLargeEmphasized = base.displayLargeEmphasized.copy(fontFamily = RoundedFontFamily),
    displayMediumEmphasized = base.displayMediumEmphasized.copy(fontFamily = RoundedFontFamily),
    displaySmallEmphasized = base.displaySmallEmphasized.copy(fontFamily = RoundedFontFamily),
    headlineLargeEmphasized = base.headlineLargeEmphasized.copy(fontFamily = RoundedFontFamily),
    headlineMediumEmphasized = base.headlineMediumEmphasized.copy(fontFamily = RoundedFontFamily),
    headlineSmallEmphasized = base.headlineSmallEmphasized.copy(fontFamily = RoundedFontFamily),
    titleLargeEmphasized = base.titleLargeEmphasized.copy(fontFamily = RoundedFontFamily),
    titleMediumEmphasized = base.titleMediumEmphasized.copy(fontFamily = RoundedFontFamily),
    titleSmallEmphasized = base.titleSmallEmphasized.copy(fontFamily = RoundedFontFamily),
    bodyLargeEmphasized = base.bodyLargeEmphasized.copy(fontFamily = family),
    bodyMediumEmphasized = base.bodyMediumEmphasized.copy(fontFamily = family),
    bodySmallEmphasized = base.bodySmallEmphasized.copy(fontFamily = family),
    labelLargeEmphasized = base.labelLargeEmphasized.copy(fontFamily = family),
    labelMediumEmphasized = base.labelMediumEmphasized.copy(fontFamily = family),
    labelSmallEmphasized = base.labelSmallEmphasized.copy(fontFamily = family),
)
