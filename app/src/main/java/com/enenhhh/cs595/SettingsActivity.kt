package com.enenhhh.smartschedule

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.enenhhh.smartschedule.data.ScheduleRepository
import com.enenhhh.smartschedule.databinding.ActivitySettingsBinding
import com.enenhhh.smartschedule.notifications.NotificationHelper
import com.enenhhh.smartschedule.services.KeepAliveService
import com.enenhhh.smartschedule.util.Prefs
import com.enenhhh.smartschedule.util.RecentsHelper
import com.enenhhh.smartschedule.util.UpdateChecker
import java.util.Calendar

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var repo: ScheduleRepository

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            NotificationHelper.createChannels(this)
            if (binding.switchReminder.isChecked) {
                NotificationHelper.scheduleTomorrowReminder(this)
            }
            if (binding.switchWeather.isChecked) {
                NotificationHelper.scheduleWeatherReminder(this)
            }
        } else {
            binding.switchReminder.isChecked = false
            Prefs.setTomorrowReminderEnabled(this, false)
            binding.switchWeather.isChecked = false
            Prefs.setWeatherReminderEnabled(this, false)
        }
    }

    private val exactAlarmPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (binding.switchReminder.isChecked) {
                NotificationHelper.scheduleTomorrowReminder(this)
            }
            if (binding.switchWeather.isChecked) {
                NotificationHelper.scheduleWeatherReminder(this)
            }
            if (binding.switchPersistent.isChecked || binding.switchKeepAlive.isChecked) {
                NotificationHelper.scheduleDailyPersistentUpdate(this)
            }
        } else {
            android.widget.Toast.makeText(
                this,
                "精确闹钟权限被拒绝，定时提醒可能不准确",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        repo = ScheduleRepository(this)

        binding.tvDataPath.text = "数据保存在：/Android/data/${packageName}/files"
        binding.tvStartDate.text = Prefs.getStartDate(this).replace('-', '/')

        val pInfo = packageManager.getPackageInfo(packageName, 0)
        binding.tvVersion.text = "版本号：${pInfo.versionName ?: "1.0"}"
        binding.tvAuthor.text = "作者：恩恩hhh"
        binding.tvFeedback.text = "意见反馈：https://www.wjx.cn/vm/rm8uWLo.aspx"
        binding.tvFeedback.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.wjx.cn/vm/rm8uWLo.aspx"))
            startActivity(intent)
        }

        // 初始化显示值
        binding.tvCourseCount.text = "当前：${Prefs.getCourseCount(this)}节课"
        binding.tvSplitPositions.text = "当前：${repo.getSplitPositions().joinToString(",")}"
        binding.tvSplitSeparator.text = "当前：\"${repo.getSplitSeparator()}\""
        binding.tvReminderTime.text = "%02d:%02d".format(Prefs.getReminderHour(this), Prefs.getReminderMinute(this))
        binding.tvWeatherTime.text = "%02d:%02d".format(Prefs.getWeatherHour(this), Prefs.getWeatherMinute(this))
        binding.tvCity.text = Prefs.getWeatherCity(this)

        // 课表/导入
        binding.btnEditSchedule.setOnClickListener {
            startActivity(Intent(this, EditScheduleActivity::class.java))
        }
        binding.btnImportConfig.setOnClickListener {
            showImportJsonDialog()
        }
        binding.btnImportScheduleOnly.setOnClickListener {
            showImportScheduleOnlyDialog()
        }

        // 隐藏最近任务
        binding.switchHideRecents.isChecked = Prefs.isHideRecents(this)
        binding.switchHideRecents.setOnCheckedChangeListener { _, checked ->
            Prefs.setHideRecents(this, checked)
            RecentsHelper.apply(this)
        }

        // 保持后台
        binding.switchKeepAlive.isChecked = Prefs.isKeepAlive(this)
        binding.switchKeepAlive.setOnCheckedChangeListener { _, isChecked ->
            try {
                Prefs.setKeepAlive(this, isChecked)
                if (isChecked) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val am = getSystemService(android.app.AlarmManager::class.java)
                        if (!am.canScheduleExactAlarms()) {
                            exactAlarmPermissionLauncher.launch(android.Manifest.permission.SCHEDULE_EXACT_ALARM)
                            binding.switchKeepAlive.isChecked = false
                            Prefs.setKeepAlive(this, false)
                            return@setOnCheckedChangeListener
                        }
                    }
                    KeepAliveService.start(this)
                } else {
                    if (!Prefs.isPersistentEnabled(this)) {
                        KeepAliveService.stop(this)
                    } else {
                        KeepAliveService.requestUpdate(this)
                    }
                }
            } catch (e: SecurityException) {
                android.util.Log.e("SettingsActivity", "设置后台运行失败", e)
                binding.switchKeepAlive.isChecked = !isChecked
                Prefs.setKeepAlive(this, !isChecked)
                android.widget.Toast.makeText(
                    this,
                    "开启后台运行失败：缺少必要权限",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                android.util.Log.e("SettingsActivity", "设置后台运行失败", e)
                binding.switchKeepAlive.isChecked = !isChecked
                Prefs.setKeepAlive(this, !isChecked)
                android.widget.Toast.makeText(this, "操作失败", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // 常驻
        binding.switchPersistent.isChecked = Prefs.isPersistentEnabled(this)
        binding.switchPersistent.setOnCheckedChangeListener { _, isChecked ->
            try {
                Prefs.setPersistentEnabled(this, isChecked)
                if (isChecked) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val am = getSystemService(android.app.AlarmManager::class.java)
                        if (!am.canScheduleExactAlarms()) {
                            exactAlarmPermissionLauncher.launch(android.Manifest.permission.SCHEDULE_EXACT_ALARM)
                            binding.switchPersistent.isChecked = false
                            Prefs.setPersistentEnabled(this, false)
                            return@setOnCheckedChangeListener
                        }
                    }
                    KeepAliveService.start(this)
                } else {
                    if (Prefs.isKeepAlive(this)) {
                        KeepAliveService.requestUpdate(this)
                    } else {
                        KeepAliveService.stop(this)
                    }
                }
            } catch (e: SecurityException) {
                android.util.Log.e("SettingsActivity", "设置常驻通知失败", e)
                binding.switchPersistent.isChecked = !isChecked
                Prefs.setPersistentEnabled(this, !isChecked)
                android.widget.Toast.makeText(
                    this,
                    "开启常驻通知失败：缺少必要权限",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                android.util.Log.e("SettingsActivity", "设置常驻通知失败", e)
                binding.switchPersistent.isChecked = !isChecked
                Prefs.setPersistentEnabled(this, !isChecked)
                android.widget.Toast.makeText(this, "操作失败", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // 课表提醒时间
        val openReminderTimePicker = {
            val h = Prefs.getReminderHour(this)
            val m = Prefs.getReminderMinute(this)
            TimePickerDialog(this, { _, hourOfDay, minute ->
                Prefs.setReminderTime(this, hourOfDay, minute)
                binding.tvReminderTime.text = "%02d:%02d".format(hourOfDay, minute)
                NotificationHelper.scheduleTomorrowReminder(this)
            }, h, m, true).show()
        }
        binding.rowReminderTime.setOnClickListener { openReminderTimePicker() }
        binding.tvReminderTime.setOnClickListener { openReminderTimePicker() }

        // 课表提醒开关
        binding.switchReminder.isChecked = Prefs.isTomorrowReminderEnabled(this)
        binding.switchReminder.setOnCheckedChangeListener { _, checked ->
            try {
                Prefs.setTomorrowReminderEnabled(this, checked)
                if (checked) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val am = getSystemService(android.app.AlarmManager::class.java)
                        if (!am.canScheduleExactAlarms()) {
                            exactAlarmPermissionLauncher.launch(android.Manifest.permission.SCHEDULE_EXACT_ALARM)
                            binding.switchReminder.isChecked = false
                            Prefs.setTomorrowReminderEnabled(this, false)
                            return@setOnCheckedChangeListener
                        }
                    }
                    NotificationHelper.maybeRequestPostNotifications(this, notifPermissionLauncher)
                    NotificationHelper.scheduleTomorrowReminder(this)
                } else {
                    NotificationHelper.cancelTomorrowReminder(this)
                }
            } catch (e: SecurityException) {
                android.util.Log.e("SettingsActivity", "设置课表提醒失败", e)
                binding.switchReminder.isChecked = !checked
                Prefs.setTomorrowReminderEnabled(this, !checked)
                android.widget.Toast.makeText(
                    this,
                    "开启课表提醒失败：缺少必要权限",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                android.util.Log.e("SettingsActivity", "设置课表提醒失败", e)
                binding.switchReminder.isChecked = !checked
                Prefs.setTomorrowReminderEnabled(this, !checked)
                android.widget.Toast.makeText(this, "操作失败", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // 天气时间
        val openWeatherTimePicker = {
            val h = Prefs.getWeatherHour(this)
            val m = Prefs.getWeatherMinute(this)
            TimePickerDialog(this, { _, hourOfDay, minute ->
                Prefs.setWeatherTime(this, hourOfDay, minute)
                binding.tvWeatherTime.text = "%02d:%02d".format(hourOfDay, minute)
                NotificationHelper.scheduleWeatherReminder(this)
            }, h, m, true).show()
        }
        binding.rowWeatherTime.setOnClickListener { openWeatherTimePicker() }
        binding.tvWeatherTime.setOnClickListener { openWeatherTimePicker() }

        // 天气开关
        binding.switchWeather.isChecked = Prefs.isWeatherReminderEnabled(this)
        binding.switchWeather.setOnCheckedChangeListener { _, checked ->
            try {
                Prefs.setWeatherReminderEnabled(this, checked)
                if (checked) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val am = getSystemService(android.app.AlarmManager::class.java)
                        if (!am.canScheduleExactAlarms()) {
                            exactAlarmPermissionLauncher.launch(android.Manifest.permission.SCHEDULE_EXACT_ALARM)
                            binding.switchWeather.isChecked = false
                            Prefs.setWeatherReminderEnabled(this, false)
                            return@setOnCheckedChangeListener
                        }
                    }
                    NotificationHelper.maybeRequestPostNotifications(this, notifPermissionLauncher)
                    NotificationHelper.scheduleWeatherReminder(this)
                } else {
                    NotificationHelper.cancelWeatherReminder(this)
                }
            } catch (e: SecurityException) {
                android.util.Log.e("SettingsActivity", "设置天气提醒失败", e)
                binding.switchWeather.isChecked = !checked
                Prefs.setWeatherReminderEnabled(this, !checked)
                android.widget.Toast.makeText(
                    this,
                    "开启天气提醒失败：缺少必要权限",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                android.util.Log.e("SettingsActivity", "设置天气提醒失败", e)
                binding.switchWeather.isChecked = !checked
                Prefs.setWeatherReminderEnabled(this, !checked)
                android.widget.Toast.makeText(this, "操作失败", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // 城市
        binding.rowCity.setOnClickListener {
            val et = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_TEXT
                setText(Prefs.getWeatherCity(this@SettingsActivity))
                hint = "输入城市（如：北京、上海、广州…）"
            }
            AlertDialog.Builder(this)
                .setTitle("设置城市")
                .setView(et)
                .setPositiveButton("确定") { _, _ ->
                    val city = et.text?.toString()?.trim().orEmpty().ifBlank { "北京" }
                    Prefs.setWeatherCity(this, city)
                    binding.tvCity.text = city
                }
                .setNegativeButton("取消", null)
                .show()
        }

        // 学期开始日期
        binding.rowStartDate.setOnClickListener {
            val cur = Prefs.getStartDateCalendar(this)
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    val dateStr = "%04d-%02d-%02d".format(y, m + 1, d)
                    Prefs.setStartDate(this, dateStr)
                    binding.tvStartDate.text = dateStr.replace('-', '/')
                    KeepAliveService.requestUpdate(this)
                },
                cur.get(Calendar.YEAR),
                cur.get(Calendar.MONTH),
                cur.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // 立即发送
        binding.btnSendScheduleNow.setOnClickListener {
            NotificationHelper.maybeRequestPostNotifications(this, notifPermissionLauncher)
            NotificationHelper.triggerScheduleNow(this)
        }
        binding.btnSendWeatherNow.setOnClickListener {
            NotificationHelper.maybeRequestPostNotifications(this, notifPermissionLauncher)
            NotificationHelper.triggerWeatherNow(this)
        }

        // 刷新全部
        binding.btnRefreshAll.setOnClickListener {
            NotificationHelper.triggerPersistentUpdateNow(this)
            NotificationHelper.scheduleDailyPersistentUpdate(this)
            if (Prefs.isTomorrowReminderEnabled(this)) {
                NotificationHelper.scheduleTomorrowReminder(this)
            }
            if (Prefs.isWeatherReminderEnabled(this)) {
                NotificationHelper.scheduleWeatherReminder(this)
            }
        }

        // 课程数
        binding.rowCourseCount.setOnClickListener {
            val input = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(Prefs.getCourseCount(this@SettingsActivity).toString())
            }
            AlertDialog.Builder(this)
                .setTitle("设置课程数量")
                .setView(input)
                .setPositiveButton("确定") { _, _ ->
                    val count = input.text.toString().toIntOrNull() ?: Prefs.getCourseCount(this)
                    Prefs.setCourseCount(this, count)
                    binding.tvCourseCount.text = "当前：${count}节课"
                }
                .setNegativeButton("取消", null)
                .show()
        }

        // 简写编辑
        binding.rowCustomShortNames.setOnClickListener {
            val currentNames = repo.getShortNames()
            val input = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                setText(currentNames.entries.joinToString("\n") { "${it.key}=${it.value}" })
                hint = "格式：课程全称=简写\n每行一个"
                minLines = 6
            }
            AlertDialog.Builder(this)
                .setTitle("自定义课程简写（保存到JSON）")
                .setView(input)
                .setPositiveButton("确定") { _, _ ->
                    val text = input.text.toString().trim()
                    val map = mutableMapOf<String, String>()
                    if (text.isNotBlank()) {
                        text.split("\n").forEach { line ->
                            val parts = line.split("=", limit = 2)
                            if (parts.size == 2) {
                                map[parts[0].trim()] = parts[1].trim()
                            }
                        }
                    }
                    repo.setShortNames(map)
                    KeepAliveService.requestUpdate(this)
                }
                .setNegativeButton("取消", null)
                .show()
        }

        // 分割线位置
        binding.rowSplitPositions.setOnClickListener {
            val currentPositions = repo.getSplitPositions()
            val input = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                setText(currentPositions.joinToString(","))
                hint = "输入分割位置（逗号分隔）\n例如：1,5"
                minLines = 2
            }
            AlertDialog.Builder(this)
                .setTitle("设置分割线位置")
                .setView(input)
                .setPositiveButton("确定") { _, _ ->
                    val text = input.text.toString().trim()
                    val positions = if (text.isNotBlank()) {
                        text.split(",")
                            .mapNotNull { it.trim().toIntOrNull() }
                            .filter { it > 0 }
                            .sorted()
                    } else emptyList()
                    repo.setSplitPositions(positions)
                    binding.tvSplitPositions.text = "当前：${repo.getSplitPositions().joinToString(",")}"
                    KeepAliveService.requestUpdate(this)
                }
                .setNegativeButton("取消", null)
                .show()
        }

        // 分割线符号
        binding.rowSplitSeparator.setOnClickListener {
            val current = repo.getSplitSeparator()
            val input = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_TEXT
                setText(current)
                hint = "输入分割线符号"
            }
            AlertDialog.Builder(this)
                .setTitle("设置分割线符号")
                .setView(input)
                .setPositiveButton("确定") { _, _ ->
                    val separator = input.text.toString().trim()
                    if (separator.isNotBlank()) {
                        repo.setSplitSeparator(separator)
                        binding.tvSplitSeparator.text = "当前：\"$separator\""
                        KeepAliveService.requestUpdate(this)
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        // 检查更新
        binding.btnCheckUpdate.setOnClickListener {
            UpdateChecker.checkAndPrompt(this, manual = true)
        }
    }

    override fun onResume() {
        super.onResume()
        RecentsHelper.apply(this)
    }

    private fun showImportJsonDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 10
            hint = "粘贴完整配置JSON（包含 odd/even/config）"
        }
        AlertDialog.Builder(this)
            .setTitle("导入配置（完整）")
            .setView(input)
            .setPositiveButton("导入") { _, _ ->
                val text = input.text.toString().trim()
                try {
                    repo.importFromJson(text)
                    binding.tvCourseCount.text = "当前：${Prefs.getCourseCount(this)}节课"
                    binding.tvSplitPositions.text = "当前：${repo.getSplitPositions().joinToString(",")}"
                    binding.tvSplitSeparator.text = "当前：\"${repo.getSplitSeparator()}\""
                    KeepAliveService.requestUpdate(this)
                    android.widget.Toast.makeText(this, "导入成功", android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    android.util.Log.e("SettingsActivity", "导入失败", e)
                    android.widget.Toast.makeText(this, "导入失败：JSON格式不正确", android.widget.Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showImportScheduleOnlyDialog() {
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
                    KeepAliveService.requestUpdate(this)
                    android.widget.Toast.makeText(this, "导入成功", android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    android.util.Log.e("SettingsActivity", "导入失败", e)
                    android.widget.Toast.makeText(this, "导入失败：JSON格式不正确", android.widget.Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}