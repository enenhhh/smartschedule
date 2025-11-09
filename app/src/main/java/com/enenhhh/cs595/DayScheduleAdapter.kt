package com.enenhhh.smartschedule

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.enenhhh.smartschedule.databinding.ItemDayScheduleBinding
import com.google.android.material.chip.Chip

class DayScheduleAdapter : RecyclerView.Adapter<DayScheduleAdapter.VH>() {

    private val items = mutableListOf<DayUi>()
    var todayDay: String? = null
    var tomorrowDay: String? = null
    var weekOffset: Int = 0

    fun submit(list: List<DayUi>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val binding: ItemDayScheduleBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemDayScheduleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val b = holder.binding

        b.tvDay.text = item.dayName

        // 今日/明日高亮（仅当前周）
        val ctx = b.root.context
        val isToday = (todayDay == item.dayName)
        val isTomorrow = (tomorrowDay == item.dayName)
        when {
            isToday -> {
                b.card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.card_today))
            }
            isTomorrow -> {
                b.card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.card_tomorrow))
            }
            else -> {
                b.card.setCardBackgroundColor(ContextCompat.getColor(ctx, android.R.color.transparent))
            }
        }

        // 早读
        if (!item.morningRead.isNullOrBlank()) {
            b.rowMorning.visibility = View.VISIBLE
            b.chipMr.text = "早读：${item.morningRead}"
        } else {
            b.rowMorning.visibility = View.GONE
        }

        // 动态课程chips
        b.chipGroupCourses.removeAllViews()
        item.periods.forEachIndexed { idx, name ->
            if (name.isNotBlank()) {
                val chip = Chip(ctx).apply {
                    text = "${idx + 1} $name"
                    isCheckable = false
                    isClickable = false
                    isEnabled = true
                }
                b.chipGroupCourses.addView(chip)
            }
        }
    }

    override fun getItemCount(): Int = items.size
}