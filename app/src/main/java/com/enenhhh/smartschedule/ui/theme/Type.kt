package com.enenhhh.smartschedule.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * 排版。相对 M3 默认做了两处针对中文的调整：
 *  1. letterSpacing 归零 —— M3 默认字距是按拉丁字母调的，中文加了字距会显得散；
 *  2. 行高对齐方式设为居中，否则中文在固定高度的卡片/表格里会明显偏下。
 * 另外把标题类字重调重一档，中文小字号下 Medium 太轻，层次拉不开。
 */
private val chineseLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

private val base = Typography()

val AppTypography: Typography = base.tuneForChinese()

private fun Typography.tuneForChinese(): Typography = Typography(
    displayLarge = displayLarge.tuned(),
    displayMedium = displayMedium.tuned(),
    displaySmall = displaySmall.tuned(),
    headlineLarge = headlineLarge.tuned(FontWeight.SemiBold),
    headlineMedium = headlineMedium.tuned(FontWeight.SemiBold),
    headlineSmall = headlineSmall.tuned(FontWeight.SemiBold),
    titleLarge = titleLarge.tuned(FontWeight.SemiBold),
    titleMedium = titleMedium.tuned(FontWeight.SemiBold),
    titleSmall = titleSmall.tuned(FontWeight.Medium),
    bodyLarge = bodyLarge.tuned(),
    bodyMedium = bodyMedium.tuned(),
    bodySmall = bodySmall.tuned(),
    labelLarge = labelLarge.tuned(FontWeight.Medium),
    labelMedium = labelMedium.tuned(FontWeight.Medium),
    labelSmall = labelSmall.tuned(FontWeight.Medium)
)

private fun TextStyle.tuned(weight: FontWeight? = null): TextStyle = copy(
    fontWeight = weight ?: fontWeight,
    letterSpacing = 0.sp,
    lineHeightStyle = chineseLineHeightStyle
)

/** 把整套排版换成指定字体，用于「字体设置」里的自定义字体 */
fun Typography.withFontFamily(fontFamily: FontFamily): Typography = Typography(
    displayLarge = displayLarge.copy(fontFamily = fontFamily),
    displayMedium = displayMedium.copy(fontFamily = fontFamily),
    displaySmall = displaySmall.copy(fontFamily = fontFamily),
    headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
    headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
    headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
    titleLarge = titleLarge.copy(fontFamily = fontFamily),
    titleMedium = titleMedium.copy(fontFamily = fontFamily),
    titleSmall = titleSmall.copy(fontFamily = fontFamily),
    bodyLarge = bodyLarge.copy(fontFamily = fontFamily),
    bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
    bodySmall = bodySmall.copy(fontFamily = fontFamily),
    labelLarge = labelLarge.copy(fontFamily = fontFamily),
    labelMedium = labelMedium.copy(fontFamily = fontFamily),
    labelSmall = labelSmall.copy(fontFamily = fontFamily)
)
