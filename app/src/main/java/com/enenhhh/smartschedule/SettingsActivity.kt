package com.enenhhh.smartschedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.enenhhh.smartschedule.ui.SettingsScreen
import com.enenhhh.smartschedule.ui.theme.AppTheme
import com.enenhhh.smartschedule.util.FontManager

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        FontManager.init(this)

        setContent {
            AppTheme {
                SettingsScreen(onBack = { finish() })
            }
        }
    }
}
