package com.jongjun.hangeoreum.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.jongjun.hangeoreum.R

data class Palette(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val text: Color,
    val sub: Color,
    val tertiary: Color,
    val line: Color,
    val fill: Color,
    val blue: Color,
    val blueSoft: Color,
    val red: Color,
    val green: Color,
)

val LightPalette = Palette(
    dark = false,
    bg = Color(0xFFF2F4F6),
    card = Color(0xFFFFFFFF),
    text = Color(0xFF191F28),
    sub = Color(0xFF6B7684),
    tertiary = Color(0xFFB0B8C1),
    line = Color(0xFFE5E8EB),
    fill = Color(0xFFF2F4F6),
    blue = Color(0xFF3182F6),
    blueSoft = Color(0xFFE8F3FF),
    red = Color(0xFFF04452),
    green = Color(0xFF03B26C),
)

val DarkPalette = Palette(
    dark = true,
    bg = Color(0xFF17171C),
    card = Color(0xFF202027),
    text = Color(0xFFF2F2F4),
    sub = Color(0xFF9E9EA6),
    tertiary = Color(0xFF5F5F68),
    line = Color(0xFF34343C),
    fill = Color(0xFF2C2C35),
    blue = Color(0xFF4593FC),
    blueSoft = Color(0xFF1C2B42),
    red = Color(0xFFF25B67),
    green = Color(0xFF26C281),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

private fun TextStyle.pre() = copy(fontFamily = Pretendard)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.blue, onPrimary = Color.White, background = p.bg, onBackground = p.text,
            surface = p.card, onSurface = p.text, surfaceVariant = p.fill, onSurfaceVariant = p.sub,
            surfaceContainerHigh = p.card, surfaceContainerHighest = p.fill, outline = p.line,
        )
    } else {
        lightColorScheme(
            primary = p.blue, onPrimary = Color.White, background = p.bg, onBackground = p.text,
            surface = p.card, onSurface = p.text, surfaceVariant = p.fill, onSurfaceVariant = p.sub,
            surfaceContainerHigh = p.card, surfaceContainerHighest = p.fill, outline = p.line,
        )
    }
    val t = Typography()
    val typo = Typography(
        displayLarge = t.displayLarge.pre(), displayMedium = t.displayMedium.pre(), displaySmall = t.displaySmall.pre(),
        headlineLarge = t.headlineLarge.pre(), headlineMedium = t.headlineMedium.pre(), headlineSmall = t.headlineSmall.pre(),
        titleLarge = t.titleLarge.pre(), titleMedium = t.titleMedium.pre(), titleSmall = t.titleSmall.pre(),
        bodyLarge = t.bodyLarge.pre(), bodyMedium = t.bodyMedium.pre(), bodySmall = t.bodySmall.pre(),
        labelLarge = t.labelLarge.pre(), labelMedium = t.labelMedium.pre(), labelSmall = t.labelSmall.pre(),
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = typo, content = content)
    }
}
