package com.focus.guard

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

data class DayPlan(val dayIndex: Int, val dateLabel: String, val tasks: List<StudyTask>)

object StudyPlannerStore {
    private const val PREFS = "focusguard_study_planner"
    private const val KEY_TARGET_DAY = "target_epoch_day"
    private const val KEY_DAILY_MINUTES = "daily_study_minutes"

    fun targetDay(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_TARGET_DAY, todayEpochDay() + 180L)

    fun setTargetDay(context: Context, day: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_TARGET_DAY, day).apply()
    }

    fun dailyMinutes(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_DAILY_MINUTES, 6 * 60)

    fun setDailyMinutes(context: Context, minutes: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_DAILY_MINUTES, minutes.coerceIn(30, 24 * 60)).apply()
    }

    fun generate(context: Context, targetDay: Long = targetDay(context), dailyMinutes: Int = dailyMinutes(context)): List<DayPlan> {
        val all = StudyData.load(context)
        val done = StudySessionStore.completedTaskIds(context)
        val remaining = all.filterNot { it.id in done }
        val total = remaining.sumOf { it.estimatedMinutes }
        val neededDays = if (dailyMinutes > 0) ceil(total.toDouble() / dailyMinutes.toDouble()).toInt() else 0
        val daysUntilTarget = (targetDay - todayEpochDay() + 1).coerceAtLeast(1).toInt()
        val plannedDayCount = minOf(daysUntilTarget, neededDays.coerceAtLeast(1))

        val plan = mutableListOf<DayPlan>()
        var dayMinutes = 0
        var dayTasks = mutableListOf<StudyTask>()
        var dayIndex = 0

        fun flush() {
            if (dayTasks.isNotEmpty()) {
                plan += DayPlan(dayIndex, dayLabel(todayEpochDay() + dayIndex), dayTasks.toList())
                dayIndex++
            }
            dayTasks = mutableListOf()
            dayMinutes = 0
        }

        remaining.forEach { task ->
            if (dayMinutes + task.estimatedMinutes > dailyMinutes && dayTasks.isNotEmpty()) flush()
            if (dayMinutes + task.estimatedMinutes > dailyMinutes && dayMinutes > 0) flush()
            dayTasks += task
            dayMinutes += task.estimatedMinutes
        }
        flush()
        return plan
    }

    fun todayTasks(context: Context): List<StudyTask> =
        generate(context).firstOrNull()?.tasks ?: emptyList()

    fun todayEpochDay(): Long {
        val c = Calendar.getInstance()
        val offset = c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET)
        return (c.timeInMillis + offset) / 86_400_000L
    }

    fun dayLabel(epochDay: Long): String {
        val c = Calendar.getInstance()
        c.timeInMillis = epochDay * 86_400_000L - c.get(Calendar.ZONE_OFFSET) - c.get(Calendar.DST_OFFSET)
        return SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(Date(c.timeInMillis))
    }
}
