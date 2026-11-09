package com.enenhhh.smartschedule

import android.os.Bundle
import com.enenhhh.smartschedule.legal.LegalConsent
import com.enenhhh.smartschedule.ui.legal.LegalConsentScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.enenhhh.smartschedule.data.TempSwaps
import com.enenhhh.smartschedule.notifications.NotificationHelper
import com.enenhhh.smartschedule.ui.app.SmartScheduleApp
import com.enenhhh.smartschedule.ui.settings.UpdateDialog
import com.enenhhh.smartschedule.ui.theme.AppTheme
import com.enenhhh.smartschedule.util.Prefs
import com.enenhhh.smartschedule.util.UpdateChecker

/**
 * 唯一的 Activity。所有页面都是 Compose，由 [SmartScheduleApp] 内部导航。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        runCatching { TempSwaps.clearExpired(this) }

        setContent {
            AppTheme {
                var consent by remember { mutableStateOf(LegalConsent.isAccepted(this@MainActivity)) }
                if (!consent) {
                    LegalConsentScreen(onAccepted = { consent = true })
                    return@AppTheme
                }
                var update by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
                LaunchedEffect(Unit) {
                    val ctx = this@MainActivity
                    if (Prefs.isFirstRunDone(ctx) && Prefs.isAutoCheckUpdate(ctx) &&
                        System.currentTimeMillis() - Prefs.getLastUpdateCheck(ctx) > UpdateChecker.AUTO_CHECK_INTERVAL_MS
                    ) {
                        val r = runCatching { UpdateChecker.check(ctx) }.getOrNull()
                        if (r is UpdateChecker.CheckResult.UpdateAvailable && r.info.latest != Prefs.getSkippedVersion(ctx)) {
                            update = r.info
                        }
                    }
                }
                SmartScheduleApp(
                    pendingUpdateDialog = {
                        update?.let { info -> UpdateDialog(info = info, onDismiss = { update = null }) }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 回到前台立刻对一次表：进程可能已经被保活了好几天
        if (com.enenhhh.smartschedule.util.AppClock.refresh()) {
            runCatching { TempSwaps.clearExpired(this) }
        }
        // 回到前台时刷新常驻通知（课表可能刚改过）
        runCatching {
            if (Prefs.isPersistentEnabled(this) || Prefs.isKeepAlive(this)) NotificationHelper.updatePersistentNotification(this)
        }
    }
}
