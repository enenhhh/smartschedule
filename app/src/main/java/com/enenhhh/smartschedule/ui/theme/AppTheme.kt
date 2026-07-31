package com.enenhhh.smartschedule.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.enenhhh.smartschedule.util.FontManager
import com.enenhhh.smartschedule.util.Prefs

/**
 * 全局外观状态。
 *
 * 原先深色模式 / 主题色只在 Activity 创建时读一次 SharedPreferences，
 * 在设置里改完必须重启才生效。改成 Compose 状态之后，设置项一改
 * 所有正在展示的界面会立即重组。
 */
object ThemeState {
    /** 0=跟随系统 1=深色 2=浅色 */
    var darkMode by mutableIntStateOf(Prefs.DARK_MODE_SYSTEM)
        private set
    var dynamicColor by mutableStateOf(false)
        private set
    var themeColor by mutableStateOf(Prefs.THEME_INDIGO)
        private set

    /** 在 [com.enenhhh.smartschedule.App.onCreate] 里调用一次即可 */
    fun load(context: Context) {
        darkMode = Prefs.getDarkMode(context)
        dynamicColor = Prefs.isDynamicColorEnabled(context)
        themeColor = Prefs.getThemeColor(context)
    }

    fun setDarkMode(context: Context, mode: Int) {
        darkMode = mode
        Prefs.setDarkMode(context, mode)
    }

    fun setDynamicColor(context: Context, enabled: Boolean) {
        dynamicColor = enabled
        Prefs.setDynamicColorEnabled(context, enabled)
    }

    fun setThemeColor(context: Context, color: String) {
        themeColor = color
        Prefs.setThemeColor(context, color)
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current

    val dark = when (ThemeState.darkMode) {
        Prefs.DARK_MODE_ON -> true
        Prefs.DARK_MODE_OFF -> false
        else -> isSystemInDarkTheme()
    }

    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme: ColorScheme = when {
        ThemeState.dynamicColor && supportsDynamic ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        else -> when (ThemeState.themeColor) {
            Prefs.THEME_TEAL -> if (dark) TealDark else TealLight
            Prefs.THEME_ORANGE -> if (dark) OrangeDark else OrangeLight
            Prefs.THEME_PURPLE -> if (dark) PurpleDark else PurpleLight
            else -> if (dark) IndigoDark else IndigoLight
        }
    }

    // 状态栏 / 导航栏图标跟随应用内的深浅色，而不是系统深色设置
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity() ?: return@SideEffect
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FontManager.currentTypography(),
        shapes = AppShapes,
        content = content
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
