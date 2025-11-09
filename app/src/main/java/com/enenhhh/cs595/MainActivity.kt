package com.enenhhh.smartschedule

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.enenhhh.smartschedule.data.ScheduleRepository
import com.enenhhh.smartschedule.databinding.ActivityMainBinding
import com.enenhhh.smartschedule.notifications.NotificationHelper
import com.enenhhh.smartschedule.util.DateUtils
import com.enenhhh.smartschedule.util.Prefs
import com.enenhhh.smartschedule.util.RecentsHelper
import com.enenhhh.smartschedule.util.UpdateChecker
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: ScheduleRepository
    private lateinit var adapter: DayScheduleAdapter

    private var weekOffset = 0 // 0 = 本周；-1 上周；+1 下周

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            NotificationHelper.createChannels(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 首次启动 -> 引导
        if (!Prefs.isFirstRunDone(this)) {
            startActivity(android.content.Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        try {
            NotificationHelper.createChannels(this)

            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            setSupportActionBar(binding.toolbar)

            repo = ScheduleRepository(this)
            adapter = DayScheduleAdapter()

            binding.recyclerView.layoutManager = LinearLayoutManager(this)
            binding.recyclerView.adapter = adapter

            binding.btnPrevWeek.setOnClickListener {
                weekOffset--
                refreshUI()
            }
            binding.btnNextWeek.setOnClickListener {
                weekOffset++
                refreshUI()
            }
            binding.btnThisWeek.setOnClickListener {
                weekOffset = 0
                refreshUI()
            }

            NotificationHelper.maybeRequestPostNotifications(this, requestNotifPermission)

            // 启动时检查更新
            UpdateChecker.checkAndPrompt(this, manual = false)

            refreshUI()
        } catch (e: Throwable) {
            showCrash(e)
        }
    }

    override fun onResume() {
        super.onResume()
        RecentsHelper.apply(this)
        if (this::binding.isInitialized) {
            runCatching { refreshUI() }
        }
    }

    private fun refreshUI() {
        val startDate = Prefs.getStartDate(this)
        val baseWeek = DateUtils.getWeekNumberFrom(startDate, System.currentTimeMillis())
        val targetWeek = (baseWeek + weekOffset).coerceAtLeast(1)
        val isOdd = targetWeek % 2 == 1

        val days = listOf("周一", "周二", "周三", "周四", "周五")
        val list = days.map { day ->
            val entry = repo.getDayEntry(day, isOdd)
            DayUi(
                dayName = day,
                morningRead = entry.morningRead,
                periods = entry.courses,
            )
        }

        adapter.weekOffset = weekOffset
        adapter.submit(list)

        val range = DateUtils.getWeekRangeLabel(startDate, targetWeek) // M/d-M/d
        val label = "${if (isOdd) "单" else "双"}$targetWeek：$range"
        binding.tvWeekLabel.text = label

        val showHighLight = weekOffset == 0
        adapter.todayDay = if (showHighLight) DateUtils.todayChinese() else null
        adapter.tomorrowDay = if (showHighLight) DateUtils.tomorrowChinese() else null
        adapter.notifyDataSetChanged()

        binding.tvStartDate.text = "学期开始日期：${startDate.replace('-', '/')}"
        binding.btnThisWeek.isVisible = weekOffset != 0
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(android.content.Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showCrash(e: Throwable) {
        val padding = (16 * resources.displayMetrics.density).toInt()
        val trace = android.util.Log.getStackTraceString(e)
        val content = buildString {
            appendLine("启动异常捕获：")
            appendLine(e.toString())
            appendLine()
            appendLine(trace)
        }
        runCatching {
            val f = File(getExternalFilesDir(null), "last_crash.txt")
            f.writeText(content)
        }
        val tv = TextView(this).apply {
            setPadding(padding, padding, padding, padding)
            text = content
            textSize = 12f
            setTextIsSelectable(true)
        }
        val sv = ScrollView(this).apply { addView(tv) }
        setContentView(sv)
        title = "错误报告（已保存到last_crash.txt）"
    }
}

data class DayUi(
    val dayName: String,
    val morningRead: String?,
    val periods: List<String>
)