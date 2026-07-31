package com.enenhhh.smartschedule

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import com.enenhhh.smartschedule.notifications.NotificationHelper
import com.enenhhh.smartschedule.ui.app.SmartScheduleApp
import com.enenhhh.smartschedule.ui.theme.AppTheme
import com.enenhhh.smartschedule.util.FontManager
import com.enenhhh.smartschedule.util.Prefs
import com.enenhhh.smartschedule.util.UpdateChecker

class MainActivity : ComponentActivity() {

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            NotificationHelper.createChannels(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (!Prefs.isFirstRunDone(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        FontManager.init(this)
        NotificationHelper.createChannels(this)
        requestNotificationPermissionIfNeeded()

        if (Prefs.isAutoCheckUpdate(this)) {
            UpdateChecker.checkAndPrompt(this, manual = false)
        }

        setContent {
            AppTheme {
                SmartScheduleApp(
                    openSettings = {
                        startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从设置页回来时通知内容可能已经变了
        NotificationHelper.updatePersistentNotification(this)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        // 只有真正用到通知的功能被打开时才打扰用户
        val needsNotification = Prefs.isTomorrowReminderEnabled(this) ||
            Prefs.isWeatherReminderEnabled(this) ||
            Prefs.isPersistentEnabled(this) ||
            Prefs.isKeepAlive(this)
        if (needsNotification) {
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
