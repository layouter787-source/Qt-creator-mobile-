package com.qtcreatormobile.app.ide

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Cores fixas (estado) que nao dependem da cor de destaque. */
object IdeColors {
    val Accent = Color(0xFFCDF04E)
    val TextDim = Color(0xFF9AA08C)
    val Text = Color(0xFFE6E9DC)
    val Success = Color(0xFF3DDC5F)
    val Error = Color(0xFFFF8A75)
    val Warning = Color(0xFFFFC857)
}

val DefaultAccentArgb: Int = 0xFFCDF04E.toInt()

data class AppearanceSettings(
    val accent: Int = DefaultAccentArgb,
    val amoled: Boolean = false,
)

data class AccentPreset(val name: String, val argb: Int)

val AccentPresets: List<AccentPreset> = listOf(
    AccentPreset("Lima", 0xFFCDF04E.toInt()),
    AccentPreset("Azul", 0xFF6CB4FF.toInt()),
    AccentPreset("Roxo", 0xFFB794F6.toInt()),
    AccentPreset("Rosa", 0xFFFF8FB8.toInt()),
    AccentPreset("Laranja", 0xFFFFB454.toInt()),
    AccentPreset("Verde", 0xFF5EEA8F.toInt()),
    AccentPreset("Turquesa", 0xFF4FD8D0.toInt()),
    AccentPreset("Vermelho", 0xFFFF7A7A.toInt()),
)

/** Esquema escuro; fundo e superficies ganham um leve tom da cor de destaque. */
fun ideColorScheme(accentArgb: Int, amoled: Boolean): ColorScheme {
    val accent = Color(accentArgb)
    val onAccent = if (accent.luminance() > 0.45f) Color(0xFF151A05) else Color.White
    val background = if (amoled) Color.Black else lerp(Color(0xFF12140F), accent, 0.015f)
    val surface = lerp(if (amoled) Color(0xFF0B0B0B) else Color(0xFF1A1D16), accent, 0.04f)
    val surfaceHigh = lerp(if (amoled) Color(0xFF171717) else Color(0xFF262A20), accent, 0.05f)
    val container = lerp(background, accent, 0.24f)
    val outline = lerp(Color(0xFF3A3F30), accent, 0.10f)
    return darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = container,
        onPrimaryContainer = accent,
        secondary = IdeColors.TextDim,
        onSecondary = background,
        secondaryContainer = surfaceHigh,
        onSecondaryContainer = IdeColors.Text,
        background = background,
        onBackground = IdeColors.Text,
        surface = surface,
        onSurface = IdeColors.Text,
        surfaceVariant = surfaceHigh,
        onSurfaceVariant = IdeColors.TextDim,
        outline = outline,
        error = IdeColors.Error,
    )
}

@Composable
fun IdeTheme(accentArgb: Int = DefaultAccentArgb, amoled: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ideColorScheme(accentArgb, amoled), content = content)
}
