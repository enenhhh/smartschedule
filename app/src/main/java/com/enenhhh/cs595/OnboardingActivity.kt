package com.enenhhh.smartschedule

import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.enenhhh.smartschedule.data.ScheduleRepository
import com.enenhhh.smartschedule.databinding.ActivityOnboardingBinding
import com.enenhhh.smartschedule.notifications.NotificationHelper
import com.enenhhh.smartschedule.services.KeepAliveService
import com.enenhhh.smartschedule.util.Prefs
import com.enenhhh.smartschedule.util.RecentsHelper

class OnboardingActivity : AppCompatActivity() {

    private lateinit var b: ActivityOnboardingBinding
    private lateinit var repo: ScheduleRepository

    private var step = 1

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        updatePermStates()
        if (Build.VERSION.SDK_INT >= 33 && !NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            openAppNotificationSettings()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(b.root)

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        repo = ScheduleRepository(this)

        // Step1
        b.btnWelcomeContinue.setOnClickListener { goStep(2) }
        b.btnWelcomeImportConfig.setOnClickListener { showImportConfigAndJumpStep2() }

        // Step2 权限
        b.btnAskNotif.setOnClickListener { requestOrOpenNotificationPermission() }
        b.btnOpenAutoStart.setOnClickListener { openAutoStartManager() }
        b.btnAskBattery.setOnClickListener { requestIgnoreBattery() }
        b.btnRefreshPerm.setOnClickListener { updatePermStates() }
        updatePermStates()

        // Step3 课表与简写
        b.btnSetCourseCount.setOnClickListener { changeCourseCount() }
        b.btnEditSchedule.setOnClickListener { startActivity(Intent(this, EditScheduleActivity::class.java)) }
        b.btnPasteScheduleOnly.setOnClickListener { showImportScheduleOnly() }
        b.btnSaveShortNames.setOnClickListener { saveShortNamesFromEditor() }

        // Step4 分割线
        b.btnSaveSplit.setOnClickListener { saveSplitConfig() }

        // Step5 功能设置
        b.switchKeepAlive.setOnCheckedChangeListener { _, checked ->
            try {
                Prefs.setKeepAlive(this, checked)
                if (checked) {
                    NotificationHelper.createChannels(this)
                    KeepAliveService.start(this)
                } else if (!Prefs.isPersistentEnabled(this)) {
                    KeepAliveService.stop(this)
                }
            } catch (_: Throwable) {}
        }
        b.switchPersistent.setOnCheckedChangeListener { _, checked ->
            try {
                Prefs.setPersistentEnabled(this, checked)
                if (checked) {
                    NotificationHelper.createChannels(this)
                    KeepAliveService.start(this)
                } else if (!Prefs.isKeepAlive(this)) {
                    KeepAliveService.stop(this)
                }
            } catch (_: Throwable) {}
        }

        b.switchReminder.setOnCheckedChangeListener { _, checked ->
            Prefs.setTomorrowReminderEnabled(this, checked)
            if (checked) NotificationHelper.scheduleTomorrowReminder(this) else NotificationHelper.cancelTomorrowReminder(this)
        }
        b.rowReminderTime.setOnClickListener {
            val h = Prefs.getReminderHour(this)
            val m = Prefs.getReminderMinute(this)
            TimePickerDialog(this, { _, hh, mm ->
                Prefs.setReminderTime(this, hh, mm)
                b.tvReminderTime.text = "%02d:%02d".format(hh, mm)
                NotificationHelper.scheduleTomorrowReminder(this)
            }, h, m, true).show()
        }

        b.switchWeather.setOnCheckedChangeListener { _, checked ->
            Prefs.setWeatherReminderEnabled(this, checked)
            if (checked) NotificationHelper.scheduleWeatherReminder(this) else NotificationHelper.cancelWeatherReminder(this)
        }
        b.rowWeatherTime.setOnClickListener {
            val h = Prefs.getWeatherHour(this)
            val m = Prefs.getWeatherMinute(this)
            TimePickerDialog(this, { _, hh, mm ->
                Prefs.setWeatherTime(this, hh, mm)
                b.tvWeatherTime.text = "%02d:%02d".format(hh, mm)
                NotificationHelper.scheduleWeatherReminder(this)
            }, h, m, true).show()
        }
        b.rowCity.setOnClickListener {
            val et = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_TEXT
                setText(Prefs.getWeatherCity(this@OnboardingActivity))
                hint = "输入城市（如：北京、上海、广州…）"
            }
            AlertDialog.Builder(this)
                .setTitle("设置城市")
                .setView(et)
                .setPositiveButton("确定") { _, _ ->
                    val city = et.text?.toString()?.trim().orEmpty().ifBlank { "北京" }
                    Prefs.setWeatherCity(this, city)
                    b.tvCity.text = city
                }
                .setNegativeButton("取消", null)
                .show()
        }

        // 导航
        b.btnPrev.setOnClickListener { if (step > 1) goStep(step - 1) }
        b.btnNext.setOnClickListener {
            if (step == 3 && repo.getShortNames().isEmpty()) {
                android.widget.Toast.makeText(this, "请先设置至少一个课程简写后再继续", android.widget.Toast.LENGTH_LONG).show()
            } else {
                goStep(step + 1)
            }
        }
        b.btnStart.setOnClickListener {
            try {
                NotificationHelper.createChannels(this)
                if (Prefs.isPersistentEnabled(this) || Prefs.isKeepAlive(this)) {
                    KeepAliveService.start(this)
                }
            } catch (_: Throwable) {}
            Prefs.setFirstRunDone(this, true)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        // 初始化UI值
        b.tvReminderTime.text = "%02d:%02d".format(Prefs.getReminderHour(this), Prefs.getReminderMinute(this))
        b.tvWeatherTime.text = "%02d:%02d".format(Prefs.getWeatherHour(this), Prefs.getWeatherMinute(this))
        b.tvCity.text = Prefs.getWeatherCity(this)
        b.etSplitPositions.setText(Prefs.getSplitPositions(this).joinToString(","))
        b.etSplitSeparator.setText("｜")

        goStep(1)
    }

    override fun onResume() {
        super.onResume()
        RecentsHelper.apply(this)
        updatePermStates()
    }

    private fun goStep(target: Int) {
        val t = target.coerceIn(1, 6)
        step = t
        b.groupStep1Root.visibility = if (t == 1) View.VISIBLE else View.GONE
        b.groupStep2Root.visibility = if (t == 2) View.VISIBLE else View.GONE
        b.groupStep3Root.visibility = if (t == 3) View.VISIBLE else View.GONE
        b.groupStep4Root.visibility = if (t == 4) View.VISIBLE else View.GONE
        b.groupStep5Root.visibility = if (t == 5) View.VISIBLE else View.GONE
        b.groupStep6Root.visibility = if (t == 6) View.VISIBLE else View.GONE

        b.btnPrev.visibility = if (t == 1) View.INVISIBLE else View.VISIBLE
        b.btnNext.visibility = if (t == 6) View.INVISIBLE else View.VISIBLE
        b.btnStart.visibility = if (t == 6) View.VISIBLE else View.INVISIBLE
    }

    private fun updatePermStates() {
        val notifEnabled = if (Build.VERSION.SDK_INT >= 33) {
            NotificationManagerCompat.from(this).areNotificationsEnabled()
        } else true
        b.tvNotifState.text = if (notifEnabled) "已开启" else "未开启"

        val pm = getSystemService(PowerManager::class.java)
        val batteryOk = pm?.isIgnoringBatteryOptimizations(packageName) == true
        b.tvBatteryState.text = if (batteryOk) "已排除" else "未排除"
    }

    private fun requestOrOpenNotificationPermission() {
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                val enabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
                if (!enabled) {
                    notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openAppNotificationSettings()
                }
            } else {
                openAppNotificationSettings()
            }
        } catch (_: Throwable) {
            openAppNotificationSettings()
        }
    }

    private fun openAppNotificationSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
                startActivity(intent)
            } else {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Throwable) {}
        }
    }

    private fun openAutoStartManager() {
        try {
            val intent = Intent().apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Throwable) { }
        }
    }

    private fun requestIgnoreBattery() {
        try {
            val pm = getSystemService(PowerManager::class.java)
            val already = pm?.isIgnoringBatteryOptimizations(packageName) == true
            if (!already) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                try {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (_: Throwable) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
        } catch (_: Throwable) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Throwable) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }

    private fun changeCourseCount() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(Prefs.getCourseCount(this@OnboardingActivity).toString())
        }
        AlertDialog.Builder(this)
            .setTitle("设置课程数量")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val count = input.text.toString().toIntOrNull() ?: Prefs.getCourseCount(this)
                Prefs.setCourseCount(this, count)
                android.widget.Toast.makeText(this, "已设置为${count}节", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showImportScheduleOnly() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 10
            hint = "粘贴仅包含 odd/even 的课表JSON"
        }
        AlertDialog.Builder(this)
            .setTitle("导入课表（仅odd/even）")
            .setView(input)
            .setPositiveButton("导入") { _, _ ->
                val text = input.text.toString().trim()
                try {
                    repo.importFromJson(text)
                    android.widget.Toast.makeText(this, "导入成功", android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    android.util.Log.e("Onboarding", "导入失败", e)
                    android.widget.Toast.makeText(this, "导入失败：JSON格式不正确", android.widget.Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun saveShortNamesFromEditor() {
        val text = b.etShortNames.text?.toString()?.trim().orEmpty()
        val map = mutableMapOf<String, String>()
        if (text.isNotBlank()) {
            text.split("\n").forEach { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) map[parts[0].trim()] = parts[1].trim()
            }
        }
        repo.setShortNames(map)
        android.widget.Toast.makeText(this, "已保存 ${map.size} 条简写", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun saveSplitConfig() {
        val posStr = b.etSplitPositions.text?.toString()?.trim().orEmpty()
        val positions = if (posStr.isNotBlank()) {
            posStr.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }.sorted()
        } else emptyList()
        repo.setSplitPositions(positions)
        val sep = b.etSplitSeparator.text?.toString()?.trim().orEmpty().ifBlank { "｜" }
        repo.setSplitSeparator(sep)
        android.widget.Toast.makeText(this, "已保存分割线设置", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun showImportConfigAndJumpStep2() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 10
            hint = "粘贴完整配置JSON（包含 odd/even/config）"
        }
        AlertDialog.Builder(this)
            .setTitle("导入完整配置")
            .setView(input)
            .setPositiveButton("导入") { _, _ ->
                val text = input.text.toString().trim()
                try {
                    repo.importFromJson(text)
                    android.widget.Toast.makeText(this, "导入成功", android.widget.Toast.LENGTH_SHORT).show()
                    goStep(2)
                } catch (e: Exception) {
                    android.util.Log.e("Onboarding", "导入失败", e)
                    android.widget.Toast.makeText(this, "导入失败：JSON格式不正确", android.widget.Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}