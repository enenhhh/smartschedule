package com.enenhhh.smartschedule

import android.os.Bundle
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.enenhhh.smartschedule.data.ScheduleRepository
import com.enenhhh.smartschedule.databinding.ActivityEditScheduleBinding
import com.enenhhh.smartschedule.services.KeepAliveService
import com.enenhhh.smartschedule.util.Prefs
import com.enenhhh.smartschedule.util.RecentsHelper
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class EditScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditScheduleBinding
    private lateinit var repo: ScheduleRepository

    private var currentDay = "周一"
    private var currentOdd = true
    private val courseInputs = mutableListOf<TextInputEditText>()
    private var lastBuiltCount = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        repo = ScheduleRepository(this)

        val days = listOf("周一", "周二", "周三", "周四", "周五")
        binding.spinnerDay.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, days)
        binding.spinnerDay.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                currentDay = days[pos]
                loadFields()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        binding.toggleWeekType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentOdd = checkedId == binding.btnOdd.id
                loadFields()
            }
        }

        binding.btnSave.setOnClickListener {
            saveCurrentFields()
            repo.persist()
            KeepAliveService.requestUpdate(this)
            finish()
        }

        binding.spinnerDay.setSelection(0)
        binding.toggleWeekType.check(binding.btnOdd.id)
        loadFields()
    }

    override fun onResume() {
        super.onResume()
        RecentsHelper.apply(this)
    }

    private fun rebuildCourseInputsIfNeeded() {
        val count = Prefs.getCourseCount(this)
        if (count == lastBuiltCount && courseInputs.size == count) return

        val container = binding.llCourseList
        container.removeAllViews()
        courseInputs.clear()
        lastBuiltCount = count

        val dp8 = (8 * resources.displayMetrics.density).toInt()
        for (i in 1..count) {
            val til = TextInputLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = if (i == 1) dp8 else dp8 / 2
                }
                hint = "第${i}节"
            }
            val et = TextInputEditText(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            til.addView(et)
            container.addView(til)
            courseInputs.add(et)
        }
    }

    private fun loadFields() {
        rebuildCourseInputsIfNeeded()

        val entry = repo.getDayEntry(currentDay, currentOdd)

        binding.etMr.setText(entry.morningRead ?: "")

        val courseCount = Prefs.getCourseCount(this)
        binding.tipCourseCount.text = "当前设置：${courseCount}节课（可在设置中调整）"
        for (i in 0 until courseInputs.size) {
            val text = if (i < entry.courses.size) entry.courses[i] else ""
            courseInputs[i].setText(text)
        }
    }

    private fun saveCurrentFields() {
        val mr = binding.etMr.text?.toString()?.trim().orEmpty()
        val list = courseInputs.map { it.text?.toString()?.trim().orEmpty() }
            .dropLastWhile { it.isBlank() }

        repo.updateDayEntry(currentDay, currentOdd, mr.ifBlank { "" }, list)
    }
}