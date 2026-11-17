package com.lulu786.alternate.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.lulu786.alternate.ui.components.AvatarSpec

/** container / on-container colour per palette hue, computed once per theme. */
class AvatarColors(val container: List<Color>, val onContainer: List<Color>)

val LocalAvatarColors = compositionLocalOf<AvatarColors> { error("AlternateTheme missing") }

// shortcut: fallback uses Compose's baseline M3 scheme rather than one generated from seed #663399; generate from the seed if a branded static theme is wanted.
@Composable
fun AlternateTheme(
    dark: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    val scheme = if (amoled && dark) colors.copy(
        background = Color.Black, surface = Color.Black, surfaceDim = Color.Black, surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF080808), surfaceContainer = Color(0xFF111111),
        surfaceContainerHigh = Color(0xFF1A1A1A), surfaceContainerHighest = Color(0xFF222222),
    ) else colors
    val avatar = remember(colors.primary, dark) {
        val roles = List(AvatarSpec.COLORS) { AvatarSpec.roles(it, colors.primary.toArgb(), !dark) }
        AvatarColors(roles.map { Color(it.first) }, roles.map { Color(it.second) })
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        typography = AlternateTypography,
    ) {
        CompositionLocalProvider(LocalAvatarColors provides avatar, content = content)
    }
}

