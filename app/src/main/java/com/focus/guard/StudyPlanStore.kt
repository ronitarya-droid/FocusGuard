package com.focus.guard

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StudyPlanSlot(
    val id: String,
    val dateEpochDay: Long,
    val startMinute: Int,
    val endMinute: Int,
    val taskId: String
)

object StudyPlanStore {
    private const val PREFS = "focusguard_study_plan_slots"
    private const val KEY = "slots"

    fun all(context: Context): List<StudyPlanSlot> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split('\n').mapNotNull { fromRow(it) }
    }

    fun forDate(context: Context, dateEpochDay: Long): List<StudyPlanSlot> =
        all(context).filter { it.dateEpochDay == dateEpochDay }.sortedWith(compareBy({ it.startMinute }, { it.id }))

    fun add(context: Context, slot: StudyPlanSlot) {
        val next = (all(context).toMutableList() + slot).sortedWith(compareBy<StudyPlanSlot> { it.dateEpochDay }.thenBy { it.startMinute }.thenBy { it.id })
        save(context, next)
    }

    fun delete(context: Context, id: String) {
        save(context, all(context).filterNot { it.id == id })
    }

    fun nextName(context: Context, dateEpochDay: Long): String {
        val n = forDate(context, dateEpochDay).size + 1
        return "Slot $n"
    }

    private fun save(context: Context, slots: List<StudyPlanSlot>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, slots.joinToString("\n") { it.toRow() })
            .apply()
    }

    private fun StudyPlanSlot.toRow(): String =
        "$id‖$dateEpochDay‖$startMinute‖$endMinute‖$taskId"

    private fun fromRow(row: String): StudyPlanSlot? {
        val p = row.split('‖')
        if (p.size < 5) return null
        return StudyPlanSlot(
            id = p[0],
            dateEpochDay = p[1].toLongOrNull() ?: return null,
            startMinute = p[2].toIntOrNull() ?: return null,
            endMinute = p[3].toIntOrNull() ?: return null,
            taskId = p[4]
        )
    }

    fun timeLabel(minute: Int): String {
        val h = minute / 60
        val m = minute % 60
        return String.format(Locale.getDefault(), "%02d:%02d", h, m)
    }

    fun dateLabel(epochDay: Long): String {
        val today = StudyPlannerStore.todayEpochDay()
        return when (epochDay) {
            today -> "Today"
            today + 1 -> "Tomorrow"
            else -> StudyPlannerStore.dayLabel(epochDay)
        }
    }

    fun epochDayFromCalendar(year: Int, month: Int, day: Int): Long {
        val c = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, year)
            set(java.util.Calendar.MONTH, month)
            set(java.util.Calendar.DAY_OF_MONTH, day)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val offset = c.get(java.util.Calendar.ZONE_OFFSET) + c.get(java.util.Calendar.DST_OFFSET)
        return (c.timeInMillis + offset) / 86_400_000L
    }

    fun calendarForEpochDay(epochDay: Long): java.util.Calendar {
        val c = java.util.Calendar.getInstance()
        c.timeInMillis = epochDay * 86_400_000L - c.get(java.util.Calendar.ZONE_OFFSET) - c.get(java.util.Calendar.DST_OFFSET)
        return c
    }
}
