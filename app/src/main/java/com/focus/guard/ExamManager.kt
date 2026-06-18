package com.focus.guard

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

object ExamManager {
    private const val PREFS = "focusguard_exam"
    private const val KEY_EXAM_DAY = "exam_epoch_day"
    private const val KEY_SYLLABUS_PERCENT = "syllabus_percent"

    fun examDay(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_EXAM_DAY, -1L)

    fun hasExam(context: Context): Boolean = examDay(context) > 0L

    fun setExamDay(context: Context, day: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_EXAM_DAY, day).apply()
    }

    fun syllabusPercent(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_SYLLABUS_PERCENT, 0).coerceIn(0, 100)

    fun setSyllabusPercent(context: Context, percent: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_SYLLABUS_PERCENT, percent.coerceIn(0, 100)).apply()
    }

    fun daysLeft(context: Context): Int {
        val day = examDay(context)
        if (day <= 0) return -1
        return (day - StudyPlannerStore.todayEpochDay()).toInt().coerceAtLeast(0)
    }

    fun targetPerDay(context: Context): Double {
        val days = daysLeft(context)
        if (days <= 0) return 0.0
        return ceil(((100 - syllabusPercent(context)).coerceAtLeast(0)).toDouble() / days.toDouble())
    }

    fun label(context: Context): String {
        val day = examDay(context)
        if (day <= 0) return "No exam date"
        return StudyPlannerStore.dayLabel(day)
    }

    fun dayFromCalendar(year: Int, month: Int, dayOfMonth: Int): Long {
        val c = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, dayOfMonth)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val offset = c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET)
        return (c.timeInMillis + offset) / 86_400_000L
    }

    fun calendarForDay(day: Long): Calendar {
        val c = Calendar.getInstance()
        c.timeInMillis = day * 86_400_000L - c.get(Calendar.ZONE_OFFSET) - c.get(Calendar.DST_OFFSET)
        return c
    }

    fun dateLabel(day: Long): String {
        val c = calendarForDay(day)
        return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(c.timeInMillis))
    }
}
