package com.focus.guard

import android.content.Context
import kotlin.math.max

data class StudySession(
    val id: String,
    val taskId: String,
    val taskTitle: String,
    val startedAt: Long,
    val completedAt: Long,
    val minutes: Int,
    val creditSeconds: Long
)

object StudySessionStore {
    private const val PREFS = "focusguard_study_sessions"
    private const val KEY_COMPLETED = "completed_task_ids"
    private const val KEY_SESSIONS = "sessions"
    private const val KEY_ACTIVE_TASK = "active_task_id"
    private const val KEY_ACTIVE_START = "active_started_at"

    fun completedTaskIds(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_COMPLETED, emptySet()) ?: emptySet()

    fun completedCount(context: Context): Int = completedTaskIds(context).size

    fun activeTaskId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ACTIVE_TASK, null)

    fun activeStartedAt(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_ACTIVE_START, -1L)

    fun start(context: Context, task: StudyTask) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ACTIVE_TASK, task.id)
            .putLong(KEY_ACTIVE_START, System.currentTimeMillis())
            .apply()
    }

    fun finish(context: Context, creditManager: CreditManager): StudySession? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val taskId = prefs.getString(KEY_ACTIVE_TASK, null) ?: return null
        val startedAt = prefs.getLong(KEY_ACTIVE_START, -1L)
        if (startedAt <= 0) return null
        val completedAt = System.currentTimeMillis()
        val minutes = max(1, ((completedAt - startedAt) / 60_000L).toInt())
        val creditSeconds = creditManager.addStudyMinutes(minutes)
        val task = StudyData.load(context).firstOrNull { it.id == taskId }
        val title = task?.title ?: taskId
        val session = StudySession(
            id = "${completedAt}_$taskId".sanitizeId(),
            taskId = taskId,
            taskTitle = title,
            startedAt = startedAt,
            completedAt = completedAt,
            minutes = minutes,
            creditSeconds = creditSeconds
        )
        val sessions = all(context).toMutableList()
        sessions.add(0, session)
        prefs.edit()
            .remove(KEY_ACTIVE_TASK)
            .remove(KEY_ACTIVE_START)
            .putString(KEY_SESSIONS, sessions.joinToString("\n") { it.toRow() })
            .apply()

        val completed = completedTaskIds(context).toMutableSet()
        completed += taskId
        prefs.edit().putStringSet(KEY_COMPLETED, completed).apply()
        return session
    }

    fun cancel(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_ACTIVE_TASK)
            .remove(KEY_ACTIVE_START)
            .apply()
    }

    fun all(context: Context): List<StudySession> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SESSIONS, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split('\n').mapNotNull { fromRow(it) }
    }

    fun activeMinutes(context: Context): Int {
        val startedAt = activeStartedAt(context)
        if (startedAt <= 0) return 0
        return max(1, ((System.currentTimeMillis() - startedAt) / 60_000L).toInt())
    }

    fun todayMinutes(context: Context): Int {
        val today = StudyPlannerStore.todayEpochDay()
        return all(context).filter { epochDay(it.completedAt) == today }.sumOf { it.minutes }
    }

    private fun StudySession.toRow(): String =
        "$id‖$taskId‖$taskTitle‖$startedAt‖$completedAt‖$minutes‖$creditSeconds"

    private fun fromRow(row: String): StudySession? {
        val p = row.split('‖')
        if (p.size < 7) return null
        return StudySession(
            id = p[0],
            taskId = p[1],
            taskTitle = p[2],
            startedAt = p[3].toLongOrNull() ?: 0L,
            completedAt = p[4].toLongOrNull() ?: 0L,
            minutes = p[5].toIntOrNull() ?: 0,
            creditSeconds = p[6].toLongOrNull() ?: 0L
        )
    }

    private fun epochDay(ms: Long): Long {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        val offset = c.get(java.util.Calendar.ZONE_OFFSET) + c.get(java.util.Calendar.DST_OFFSET)
        return (ms + offset) / 86_400_000L
    }

    private fun String.sanitizeId(): String =
        lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
}
