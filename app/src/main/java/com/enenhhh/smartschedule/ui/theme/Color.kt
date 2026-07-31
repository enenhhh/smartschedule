package com.enenhhh.smartschedule.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Material 3 配色。
 *
 * 与上一版的区别：中性色改成了标准的 M3 色阶（背景接近白、容器逐级加深），
 * 这样默认的 Card / Dialog / Chip 不用逐个指定颜色就有正确的层次；
 * secondary / tertiary 也不再简单等于 primary，
 * 否则 FilterChip、selected 态、tonal button 全是一个颜色，看起来很平。
 */

// ---------------------------------------------------------------- 中性色

private val LightBackground = Color(0xFFFBFBFD)
private val LightSurfaceDim = Color(0xFFDCDCE0)
private val LightSurfaceBright = Color(0xFFFBFBFD)
private val LightContainerLowest = Color(0xFFFFFFFF)
private val LightContainerLow = Color(0xFFF5F5F8)
private val LightContainer = Color(0xFFEFEFF3)
private val LightContainerHigh = Color(0xFFEAEAEF)
private val LightContainerHighest = Color(0xFFE4E4EA)
private val LightOnSurface = Color(0xFF1A1B1E)
private val LightSurfaceVariant = Color(0xFFE1E2E8)
private val LightOnSurfaceVariant = Color(0xFF45474D)
private val LightOutline = Color(0xFF757780)
private val LightOutlineVariant = Color(0xFFC6C7CD)
private val LightError = Color(0xFFBA1A1A)
private val LightErrorContainer = Color(0xFFFFDAD6)
private val LightOnErrorContainer = Color(0xFF410002)
private val LightInverseSurface = Color(0xFF2F3033)
private val LightInverseOnSurface = Color(0xFFF1F0F4)

private val DarkBackground = Color(0xFF111318)
private val DarkSurfaceDim = Color(0xFF111318)
private val DarkSurfaceBright = Color(0xFF37393E)
private val DarkContainerLowest = Color(0xFF0C0E13)
private val DarkContainerLow = Color(0xFF191C20)
private val DarkContainer = Color(0xFF1D2024)
private val DarkContainerHigh = Color(0xFF282A2F)
private val DarkContainerHighest = Color(0xFF33353A)
private val DarkOnSurface = Color(0xFFE2E2E6)
private val DarkSurfaceVariant = Color(0xFF44464E)
private val DarkOnSurfaceVariant = Color(0xFFC4C6CF)
private val DarkOutline = Color(0xFF8E9099)
private val DarkOutlineVariant = Color(0xFF44464E)
private val DarkError = Color(0xFFFFB4AB)
private val DarkErrorContainer = Color(0xFF93000A)
private val DarkOnErrorContainer = Color(0xFFFFDAD6)
private val DarkInverseSurface = Color(0xFFE2E2E6)
private val DarkInverseOnSurface = Color(0xFF2F3033)

/** 品牌渐变色，用于「智能课表」标题 */
val BrandGradientStart = Color(0xFF2196F3)
val BrandGradientEnd = Color(0xFF00BFA5)

// ---------------------------------------------------------------- 构造器

private class Accent(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val inversePrimary: Color
)

private fun Accent.light(): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    inversePrimary = inversePrimary,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightBackground,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = primary,
    surfaceDim = LightSurfaceDim,
    surfaceBright = LightSurfaceBright,
    surfaceContainerLowest = LightContainerLowest,
    surfaceContainerLow = LightContainerLow,
    surfaceContainer = LightContainer,
    surfaceContainerHigh = LightContainerHigh,
    surfaceContainerHighest = LightContainerHighest,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = LightError,
    onError = Color.White,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    scrim = Color.Black
)

private fun Accent.dark(): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    inversePrimary = inversePrimary,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkBackground,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = primary,
    surfaceDim = DarkSurfaceDim,
    surfaceBright = DarkSurfaceBright,
    surfaceContainerLowest = DarkContainerLowest,
    surfaceContainerLow = DarkContainerLow,
    surfaceContainer = DarkContainer,
    surfaceContainerHigh = DarkContainerHigh,
    surfaceContainerHighest = DarkContainerHighest,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DarkError,
    onError = Color(0xFF690005),
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    scrim = Color.Black
)

// ---------------------------------------------------------------- 靛蓝（默认）

private val IndigoAccentLight = Accent(
    primary = Color(0xFF005FB8), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E3FA), onPrimaryContainer = Color(0xFF001C3A),
    secondary = Color(0xFF4A5B73), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE3F2), onSecondaryContainer = Color(0xFF05192C),
    tertiary = Color(0xFF00696A), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF9CF1F0), onTertiaryContainer = Color(0xFF002020),
    inversePrimary = Color(0xFFA5C8FF)
)
private val IndigoAccentDark = Accent(
    primary = Color(0xFF6CB6F0), onPrimary = Color(0xFF00325C),
    primaryContainer = Color(0xFF004881), onPrimaryContainer = Color(0xFFD3E3FA),
    secondary = Color(0xFFB4C8E4), onSecondary = Color(0xFF1D3149),
    secondaryContainer = Color(0xFF344761), onSecondaryContainer = Color(0xFFDCE3F2),
    tertiary = Color(0xFF80D5D4), onTertiary = Color(0xFF003736),
    tertiaryContainer = Color(0xFF004F4E), onTertiaryContainer = Color(0xFF9CF1F0),
    inversePrimary = Color(0xFF005FB8)
)
val IndigoLight = IndigoAccentLight.light()
val IndigoDark = IndigoAccentDark.dark()

// ---------------------------------------------------------------- 蓝绿

private val TealAccentLight = Accent(
    primary = Color(0xFF006A5C), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF7FF8DF), onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF4A635E), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E2), onSecondaryContainer = Color(0xFF06201C),
    tertiary = Color(0xFF42618A), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3E4FF), onTertiaryContainer = Color(0xFF001C38),
    inversePrimary = Color(0xFF5EDBC3)
)
private val TealAccentDark = Accent(
    primary = Color(0xFF34D1B8), onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005045), onPrimaryContainer = Color(0xFF7FF8DF),
    secondary = Color(0xFFB1CCC6), onSecondary = Color(0xFF1C3531),
    secondaryContainer = Color(0xFF334B47), onSecondaryContainer = Color(0xFFCCE8E2),
    tertiary = Color(0xFFA9C8F9), onTertiary = Color(0xFF0B305A),
    tertiaryContainer = Color(0xFF294978), onTertiaryContainer = Color(0xFFD3E4FF),
    inversePrimary = Color(0xFF006A5C)
)
val TealLight = TealAccentLight.light()
val TealDark = TealAccentDark.dark()

// ---------------------------------------------------------------- 橙红

private val OrangeAccentLight = Accent(
    primary = Color(0xFFB02D00), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBD1), onPrimaryContainer = Color(0xFF3B0900),
    secondary = Color(0xFF77574E), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBD1), onSecondaryContainer = Color(0xFF2C150F),
    tertiary = Color(0xFF6C5D2F), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF5E1A7), onTertiaryContainer = Color(0xFF231B00),
    inversePrimary = Color(0xFFFFB59D)
)
private val OrangeAccentDark = Accent(
    primary = Color(0xFFFFB59D), onPrimary = Color(0xFF5F1500),
    primaryContainer = Color(0xFF862000), onPrimaryContainer = Color(0xFFFFDBD1),
    secondary = Color(0xFFE7BDB1), onSecondary = Color(0xFF442A21),
    secondaryContainer = Color(0xFF5D4037), onSecondaryContainer = Color(0xFFFFDBD1),
    tertiary = Color(0xFFD8C58D), onTertiary = Color(0xFF3B2F05),
    tertiaryContainer = Color(0xFF534619), onTertiaryContainer = Color(0xFFF5E1A7),
    inversePrimary = Color(0xFFB02D00)
)
val OrangeLight = OrangeAccentLight.light()
val OrangeDark = OrangeAccentDark.dark()

// ---------------------------------------------------------------- 紫罗兰

private val PurpleAccentLight = Accent(
    primary = Color(0xFF6750A4), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF), onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B70), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8), onSecondaryContainer = Color(0xFF1E192B),
    tertiary = Color(0xFF7E5260), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E3), onTertiaryContainer = Color(0xFF31101D),
    inversePrimary = Color(0xFFD0BCFF)
)
private val PurpleAccentDark = Accent(
    primary = Color(0xFFD0BCFF), onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B), onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCBC2DB), onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458), onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8), onTertiary = Color(0xFF4A2532),
    tertiaryContainer = Color(0xFF633B48), onTertiaryContainer = Color(0xFFFFD9E3),
    inversePrimary = Color(0xFF6750A4)
)
val PurpleLight = PurpleAccentLight.light()
val PurpleDark = PurpleAccentDark.dark()
