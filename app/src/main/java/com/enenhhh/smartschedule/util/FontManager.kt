package com.enenhhh.smartschedule.util

import android.content.Context
import android.net.Uri
import androidx.compose.material3.Typography
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.enenhhh.smartschedule.ui.theme.AppTypography
import com.enenhhh.smartschedule.ui.theme.withFontFamily
import java.io.File

/**
 * 字体管理：
 * 0 - 系统默认
 * 1 - 自定义本地 ttf / otf
 */
object FontManager {
    private const val PREF = "font_prefs"
    private const val KEY_MODE = "font_mode"
    const val MODE_SYSTEM = 0
    const val MODE_CUSTOM = 1
    private const val CUSTOM_FONT_FILE = "custom_font.ttf"

    private var mode by mutableIntStateOf(MODE_SYSTEM)
    private var appContext: Context? = null
    private var customFontFamily: FontFamily? = null

    fun init(context: Context) {
        if (appContext != null) return
        val ctx = context.applicationContext
        appContext = ctx
        mode = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getInt(KEY_MODE, MODE_SYSTEM)
            .coerceIn(MODE_SYSTEM, MODE_CUSTOM)
        loadCustomFontIfExists()
    }

    fun currentMode(): Int = mode

    fun currentFontFamily(): FontFamily? =
        if (mode == MODE_CUSTOM) customFontFamily else null

    /** 供 AppTheme 使用：已经带上用户选择的字体的整套排版 */
    fun currentTypography(): Typography {
        val family = currentFontFamily()
        return if (family != null) AppTypography.withFontFamily(family) else AppTypography
    }

    fun useSystemDefault() {
        mode = MODE_SYSTEM
        saveMode(MODE_SYSTEM)
    }

    fun useCustomFontFromUri(context: Context, uri: Uri): Boolean = try {
        val dest = File(context.applicationContext.filesDir, CUSTOM_FONT_FILE)
        val copied = context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
            true
        } ?: false

        if (!copied) {
            false
        } else {
            // 先试着解析一次，坏文件不要写进配置，否则整个应用会渲染不出文字
            val family = FontFamily(Font(dest))
            customFontFamily = family
            mode = MODE_CUSTOM
            saveMode(MODE_CUSTOM)
            true
        }
    } catch (e: Throwable) {
        // 解析失败就回滚到系统字体，并清掉坏文件
        runCatching { File(context.applicationContext.filesDir, CUSTOM_FONT_FILE).delete() }
        customFontFamily = null
        mode = MODE_SYSTEM
        saveMode(MODE_SYSTEM)
        false
    }

    private fun saveMode(value: Int) {
        appContext?.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            ?.edit()
            ?.putInt(KEY_MODE, value)
            ?.apply()
    }

    private fun loadCustomFontIfExists() {
        val ctx = appContext ?: return
        val file = File(ctx.filesDir, CUSTOM_FONT_FILE)
        if (file.exists()) {
            customFontFamily = runCatching { FontFamily(Font(file)) }.getOrNull()
        }
        if (customFontFamily == null && mode == MODE_CUSTOM) {
            mode = MODE_SYSTEM
            saveMode(MODE_SYSTEM)
        }
    }
}
