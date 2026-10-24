package com.kumquat.ledger.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val KmColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Ink,
    secondary = Exchange,
    onSecondary = Ink,
    tertiary = Income,
    background = BgGradStart,
    onBackground = Ink,
    surface = GlassFillStrong,
    onSurface = Ink,
    surfaceVariant = GlassFill,
    onSurfaceVariant = InkBody,
    outline = GlassBorderLight,
    error = Accent
)

/**
 * Y2K 亮色主题（唯一主题，深色模式不做：系统深色下也保持亮色）。
 */
@Composable
fun KumquatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KmColorScheme,
        typography = KmTypography,
        shapes = KmShapes,
        content = content
    )
}
