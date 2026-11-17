package com.qtcreatormobile.app.ide

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Paleta da IDE: fundo escuro esverdeado com destaque verde-limao. */
object IdeColors {
    val Accent = Color(0xFFCDF04E)
    val OnAccent = Color(0xFF1C2600)
    val Background = Color(0xFF12140F)
    val Surface = Color(0xFF1A1D16)
    val SurfaceHigh = Color(0xFF262A20)
    val SurfaceChip = Color(0xFF30380F)
    val Outline = Color(0xFF3A3F30)
    val Text = Color(0xFFE6E9DC)
    val TextDim = Color(0xFF9AA08C)
    val Success = Color(0xFF3DDC5F)
    val Error = Color(0xFFFF8A75)
}

private val IdeDarkScheme = darkColorScheme(
    primary = IdeColors.Accent,
    onPrimary = IdeColors.OnAccent,
    primaryContainer = IdeColors.SurfaceChip,
    onPrimaryContainer = IdeColors.Accent,
    secondary = IdeColors.TextDim,
    onSecondary = IdeColors.Background,
    secondaryContainer = IdeColors.SurfaceHigh,
    onSecondaryContainer = IdeColors.Text,
    background = IdeColors.Background,
    onBackground = IdeColors.Text,
    surface = IdeColors.Surface,
    onSurface = IdeColors.Text,
    surfaceVariant = IdeColors.SurfaceHigh,
    onSurfaceVariant = IdeColors.TextDim,
    outline = IdeColors.Outline,
    error = IdeColors.Error,
)

@Composable
fun IdeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = IdeDarkScheme, content = content)
}
