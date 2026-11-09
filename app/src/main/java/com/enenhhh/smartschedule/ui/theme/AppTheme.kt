package com.enenhhh.smartschedule.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.enenhhh.smartschedule.ui.glass.GlassSpec
import com.enenhhh.smartschedule.ui.glass.LocalGlass
import com.enenhhh.smartschedule.util.FontManager
import com.enenhhh.smartschedule.util.Prefs
import com.materialkolor.Contrast
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme

/** 预置主题色（种子色）。key 落盘保存，旧版的 indigo/teal/orange/purple 继续有效 */
data class AccentPreset(val key: String, val label: String, val seed: Color)

val AccentPresets: List<AccentPreset> = listOf(
    AccentPreset("indigo", "湛蓝", Color(0xFF006BDF)),
    AccentPreset("sky", "天空", Color(0xFF0288D1)),
    AccentPreset("teal", "青碧", Color(0xFF00897B)),
    AccentPreset("mint", "薄荷", Color(0xFF26A69A)),
    AccentPreset("green", "翠绿", Color(0xFF43A047)),
    AccentPreset("lime", "青柠", Color(0xFF9E9D24)),
    AccentPreset("amber", "琥珀", Color(0xFFFFB300)),
    AccentPreset("orange", "暖橙", Color(0xFFF57C00)),
    AccentPreset("coral", "珊瑚", Color(0xFFFF7043)),
    AccentPreset("rose", "玫瑰", Color(0xFFE91E63)),
    AccentPreset("purple", "紫罗兰", Color(0xFF8E24AA)),
    AccentPreset("grape", "葡萄", Color(0xFF5E35B1)),
    AccentPreset("graphite", "石墨", Color(0xFF546E7A)),
    AccentPreset("sand", "暖沙", Color(0xFF8D6E63)),
)

data class PaletteOption(val key: String, val label: String, val desc: String, val style: PaletteStyle)

val PaletteOptions: List<PaletteOption> = listOf(
    PaletteOption("tonal_spot", "标准", "Material You 默认，柔和克制", PaletteStyle.TonalSpot),
    PaletteOption("expressive", "表现力", "会旋转主色相；想保留所选蓝色请用「忠实」", PaletteStyle.Expressive),
    PaletteOption("vibrant", "鲜艳", "高饱和，适合浅色模式", PaletteStyle.Vibrant),
    PaletteOption("fidelity", "忠实", "尽量贴近种子色本身", PaletteStyle.Fidelity),
    PaletteOption("rainbow", "彩虹", "中性底色 + 彩色点缀", PaletteStyle.Rainbow),
    PaletteOption("fruit_salad", "果盘", "活泼的多色搭配", PaletteStyle.FruitSalad),
    PaletteOption("neutral", "素雅", "接近灰度，只保留一点色相", PaletteStyle.Neutral),
    PaletteOption("monochrome", "单色", "纯黑白灰", PaletteStyle.Monochrome),
)

/**
 * 外观相关的全部状态。Application.onCreate 里 load 一次，之后设置页改动直接驱动
 * 所有界面重组，不需要重启。
 */
object ThemeState {
    var seedKey by mutableStateOf("indigo"); private set
    var customSeed by mutableIntStateOf(0xFF006BDF.toInt()); private set
    var paletteKey by mutableStateOf("fidelity"); private set
    var darkMode by mutableIntStateOf(Prefs.DARK_MODE_SYSTEM); private set
    var dynamicColor by mutableStateOf(false); private set
    var amoled by mutableStateOf(false); private set
    var contrast by mutableIntStateOf(0); private set
    var glassEnabled by mutableStateOf(true); private set
    var glassIntensity by mutableFloatStateOf(0.6f); private set
    var liquidGlass by mutableStateOf(true); private set
    var animatedBackground by mutableStateOf(true); private set

    var reduceMotion by mutableStateOf(false); private set

    fun load(context: Context) {
        val c = context.applicationContext
        seedKey = Prefs.getThemeColor(c)
        customSeed = Prefs.getCustomSeed(c)
        paletteKey = Prefs.getPaletteStyle(c)
        darkMode = Prefs.getDarkMode(c)
        dynamicColor = Prefs.isDynamicColorEnabled(c)
        amoled = Prefs.isAmoled(c)
        contrast = Prefs.getContrast(c)
        glassEnabled = Prefs.isGlassEnabled(c)
        glassIntensity = Prefs.getGlassIntensity(c)
        liquidGlass = Prefs.isLiquidGlass(c)
        animatedBackground = Prefs.isAnimatedBackground(c)
        reduceMotion = Prefs.isReduceMotion(c)
    }

    fun seedColor(): Color =
        if (seedKey == "custom") Color(customSeed)
        else (AccentPresets.firstOrNull { it.key == seedKey } ?: AccentPresets.first()).seed

    fun paletteStyle(): PaletteStyle =
        (PaletteOptions.firstOrNull { it.key == paletteKey } ?: PaletteOptions[3]).style

    fun setSeed(context: Context, key: String) { seedKey = key; Prefs.setThemeColor(context, key) }
    fun setCustomSeed(context: Context, argb: Int) {
        customSeed = argb; Prefs.setCustomSeed(context, argb)
        setSeed(context, "custom")
    }
    fun setPalette(context: Context, key: String) { paletteKey = key; Prefs.setPaletteStyle(context, key) }
    fun setDarkMode(context: Context, mode: Int) { darkMode = mode; Prefs.setDarkMode(context, mode) }
    fun setDynamicColor(context: Context, v: Boolean) { dynamicColor = v; Prefs.setDynamicColorEnabled(context, v) }
    fun setAmoled(context: Context, v: Boolean) { amoled = v; Prefs.setAmoled(context, v) }
    fun setContrast(context: Context, v: Int) { contrast = v; Prefs.setContrast(context, v) }
    fun setGlassEnabled(context: Context, v: Boolean) { glassEnabled = v; Prefs.setGlassEnabled(context, v) }
    fun setGlassIntensity(context: Context, v: Float) { glassIntensity = v.coerceIn(0f, 1f); Prefs.setGlassIntensity(context, v) }
    fun setLiquidGlass(context: Context, v: Boolean) { liquidGlass = v; Prefs.setLiquidGlass(context, v) }
    fun setAnimatedBackground(context: Context, v: Boolean) { animatedBackground = v; Prefs.setAnimatedBackground(context, v) }

    fun setReduceMotion(context: Context, v: Boolean) { reduceMotion = v; Prefs.setReduceMotion(context, v) }

    @Composable
    fun isDark(): Boolean = when (darkMode) {
        Prefs.DARK_MODE_ON -> true
        Prefs.DARK_MODE_OFF -> false
        else -> isSystemInDarkTheme()
    }
}

/** 给任意种子色生成当前设置下的配色（设置页预览色块也用它） */
@Composable
fun rememberSchemeFor(seed: Color, dark: Boolean, style: PaletteStyle = ThemeState.paletteStyle()): ColorScheme =
    rememberDynamicColorScheme(
        seedColor = seed,
        isDark = dark,
        isAmoled = dark && ThemeState.amoled,
        style = style,
        contrastLevel = when (ThemeState.contrast) {
            1 -> Contrast.Medium.value
            2 -> Contrast.High.value
            else -> Contrast.Default.value
        }
    )

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = ThemeState.isDark()
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val useDynamic = ThemeState.dynamicColor && supportsDynamic

    val generated = rememberSchemeFor(ThemeState.seedColor(), dark)
    val colorScheme: ColorScheme = remember(useDynamic, dark, generated, ThemeState.amoled) {
        when {
            useDynamic && dark -> dynamicDarkColorScheme(context).let {
                if (ThemeState.amoled) it.copy(background = Color.Black, surface = Color.Black) else it
            }
            useDynamic -> dynamicLightColorScheme(context)
            else -> generated
        }
    }

    val animations = com.enenhhh.smartschedule.ui.motion.motionEnabled()
    val glass = remember(
        ThemeState.glassEnabled, ThemeState.glassIntensity, ThemeState.liquidGlass,
        ThemeState.animatedBackground, ThemeState.reduceMotion, ThemeState.amoled, animations, dark
    ) {
        GlassSpec(
            enabled = ThemeState.glassEnabled,
            intensity = ThemeState.glassIntensity,
            liquid = ThemeState.liquidGlass,
            animated = ThemeState.animatedBackground && animations,
            dark = dark,
            amoled = dark && ThemeState.amoled
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity() ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = FontManager.currentTypography(),
        shapes = AppShapes
    ) {
        // A color scheme alone does NOT set LocalContentColor. The old custom Box /
        // Column glass surfaces inherited black in dark mode. Surface establishes
        // the semantic onSurface foreground for every route, top bar and dialog.
        Surface(modifier = Modifier.fillMaxSize(), color = colorScheme.background, contentColor = colorScheme.onSurface) {
            CompositionLocalProvider(LocalGlass provides glass, LocalContentColor provides colorScheme.onSurface) {
                content()
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
