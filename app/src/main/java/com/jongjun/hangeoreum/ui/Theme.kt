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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.jongjun.hangeoreum.R
import com.jongjun.hangeoreum.data.Goal
import com.jongjun.hangeoreum.data.ThemePrefs

val DefaultItemColors = listOf(
    Color(0xFF3182F6), // 파랑
    Color(0xFF00A6A6), // 청록
    Color(0xFF8B5CF6), // 보라
    Color(0xFFFF8A3D), // 주황
    Color(0xFF03B26C), // 초록
    Color(0xFFF25C8A), // 분홍
    Color(0xFFF04452), // 빨강
    Color(0xFFFFC342), // 노랑
    Color(0xFF1B64DA), // 남색
    Color(0xFF7AC74F), // 연두
    Color(0xFFA0704C), // 갈색
    Color(0xFF6B7684), // 회색
)

val DefaultYearColors = listOf(
    Color(0xFF3182F6), Color(0xFF00A6A6), Color(0xFF03B26C), Color(0xFF8B5CF6), Color(0xFFFF8A3D),
)

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
    val itemColors: List<Color> = DefaultItemColors,
    val yearColors: List<Color> = DefaultYearColors,
    val levelColors: List<Color> = List(4) { blue },
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

fun isDarkFor(prefs: ThemePrefs, system: Boolean): Boolean = when (prefs.mode) {
    1 -> false
    2 -> true
    else -> system
}

fun buildPalette(dark: Boolean, t: ThemePrefs): Palette {
    val base = if (dark) DarkPalette else LightPalette
    val accent = t.accent?.let { Color(it) } ?: base.blue
    val bgO = (if (dark) t.bgDark else t.bgLight)?.let { Color(it) }
    val cardO = (if (dark) t.cardDark else t.cardLight)?.let { Color(it) }
    val textO = (if (dark) t.textDark else t.textLight)?.let { Color(it) }
    val bg = bgO ?: base.bg
    val card = cardO ?: base.card
    val text = textO ?: base.text
    val derive = textO != null || cardO != null || bgO != null
    return base.copy(
        bg = bg,
        card = card,
        text = text,
        sub = if (derive) text.copy(alpha = 0.62f).compositeOver(card) else base.sub,
        tertiary = if (derive) text.copy(alpha = 0.36f).compositeOver(card) else base.tertiary,
        line = if (derive) text.copy(alpha = 0.12f).compositeOver(card) else base.line,
        fill = if (derive) text.copy(alpha = 0.06f).compositeOver(card) else base.fill,
        blue = accent,
        blueSoft = if (t.accent != null || derive) accent.copy(alpha = if (dark) 0.20f else 0.11f).compositeOver(card) else base.blueSoft,
        itemColors = DefaultItemColors.mapIndexed { i, c -> t.itemColors.getOrNull(i)?.let { Color(it) } ?: c },
        yearColors = DefaultYearColors.mapIndexed { i, c -> t.yearColors.getOrNull(i)?.let { Color(it) } ?: c },
        levelColors = List(4) { i -> t.levelColors.getOrNull(i)?.let { Color(it) } ?: accent },
    )
}

val LocalPalette = staticCompositionLocalOf { LightPalette }

@Composable
fun itemColor(i: Int): Color {
    val list = LocalPalette.current.itemColors
    return list[Math.floorMod(i, list.size)]
}

@Composable
fun goalColor(g: Goal): Color = when {
    g.customColor != null -> Color(g.customColor)
    g.color >= 0 -> itemColor(g.color)
    else -> LocalPalette.current.levelColors[g.level.ordinal]
}

val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

private fun TextStyle.pre() = copy(fontFamily = Pretendard)

@Composable
fun AppTheme(prefs: ThemePrefs, content: @Composable () -> Unit) {
    val dark = isDarkFor(prefs, isSystemInDarkTheme())
    val p = buildPalette(dark, prefs)
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
