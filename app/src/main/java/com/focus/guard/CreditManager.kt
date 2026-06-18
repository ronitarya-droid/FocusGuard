package com.focus.guard

import android.content.Context
import java.util.Calendar

/**
 * Study → credit → Minecraft economy.
 *
 * Rules (per user spec):
 *  - Every night, ONLY between 22:00 and 24:00, a window asks how many hours you
 *    studied that day. Outside that window you cannot log (the day's entry locks).
 *  - Earning rate: 24 hours of study === 10 minutes of play credit.
 *    => creditMinutes = studyHours * (10 / 24).
 *  - Credits accumulate (bank). Playing Minecraft spends them in real time.
 *  - When credits hit zero, Minecraft is blocked until you earn more by studying.
 *
 * All state is in SharedPreferences — no dependencies. Credit is stored in
 * SECONDS for smooth real-time spend while playing.
 */
class CreditManager(context: Context) {

    private val prefs = context.getSharedPreferences("focusguard_credits", Context.MODE_PRIVATE)

    companion object {
        const val MINECRAFT_PKG = "com.mojang.minecraftpe"
        // 24 study hours -> 10 play minutes -> 600 play seconds.
        private const val SECONDS_PER_STUDY_HOUR = 600.0 / 24.0   // = 25s play per study hour
        private const val LOG_WINDOW_START_HOUR = 22   // 10 PM
        private const val LOG_WINDOW_END_HOUR = 24     // midnight

        private const val KEY_CREDIT_SECONDS = "credit_seconds"
        private const val KEY_LAST_LOG_DAY = "last_log_epoch_day"
        private const val KEY_HISTORY = "study_history"
        private const val KEY_HIGHEST_DAY = "highest_day_seen"  // clock-rollback guard
    }

    // ---- credit balance (seconds) ----
    fun creditSeconds(): Long = prefs.getLong(KEY_CREDIT_SECONDS, 0L).coerceAtLeast(0L)
    fun creditMinutes(): Long = creditSeconds() / 60
    fun hasCredit(): Boolean = creditSeconds() > 0L

    fun spendSeconds(seconds: Long) {
        val next = (creditSeconds() - seconds).coerceAtLeast(0L)
        prefs.edit().putLong(KEY_CREDIT_SECONDS, next).apply()
    }

    private fun addStudyHours(hours: Double) {
        val add = (hours * SECONDS_PER_STUDY_HOUR).toLong()
        prefs.edit().putLong(KEY_CREDIT_SECONDS, creditSeconds() + add).apply()
    }

    fun addStudyMinutes(minutes: Int): Long {
        val add = (minutes * SECONDS_PER_STUDY_HOUR / 60.0).toLong().coerceAtLeast(0L)
        prefs.edit().putLong(KEY_CREDIT_SECONDS, creditSeconds() + add).apply()
        return add
    }

    // ---- nightly logging window ----
    /** True only during 22:00–24:00. */
    fun isLogWindowOpen(): Boolean {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return h in LOG_WINDOW_START_HOUR until LOG_WINDOW_END_HOUR
    }

    /** True if today's study has already been logged (one entry per day). */
    fun isLoggedToday(): Boolean = prefs.getLong(KEY_LAST_LOG_DAY, -1L) == todayEpochDay()

    fun canLogNow(): Boolean = isLogWindowOpen() && !isLoggedToday() && !wasClockRolledBackToday()

    /**
     * CLOCK-ROLLBACK GUARD for the credit window.
     *
     * Without this, a user could:
     *   1. Log study at 22:30 (now logged as "today")
     *   2. Realize they want more credits
     *   3. Set the phone date FORWARD to the next day
     *   4. Now isLoggedToday() returns false again → log again
     *   5. Repeat for unlimited credits
     *
     * Defense: track the highest wall-clock day we've ever seen. If
     * todayEpochDay() is LESS than that highest-seen day, the clock was
     * rolled back — refuse to log until the user comes back to (or past)
     * the day they tried to skip to.
     */
    private var highestDaySeen: Long
        get() = prefs.getLong(KEY_HIGHEST_DAY, 0L)
        set(v) = prefs.edit().putLong(KEY_HIGHEST_DAY, v).apply()

    /** True if the user has rolled the clock back past the highest day
     *  we've previously recorded. Blocks credit logging until they
     *  restore the real date. */
    fun wasClockRolledBackToday(): Boolean {
        val today = todayEpochDay()
        if (today > highestDaySeen) {
            highestDaySeen = today
            return false
        }
        return today < highestDaySeen
    }

    /**
     * Log today's study hours and bank the credit. Returns the play-seconds added,
     * or -1 if logging isn't allowed right now (window closed, already logged,
     * or clock rolled back).
     */
    fun logStudyHours(hours: Double): Long {
        if (!canLogNow()) return -1L
        val before = creditSeconds()
        addStudyHours(hours)
        recordTodayMinutes((hours * 60.0).toInt())
        prefs.edit().putLong(KEY_LAST_LOG_DAY, todayEpochDay()).apply()
        // Advance highestDaySeen so a rollback after logging is caught.
        highestDaySeen = maxOf(highestDaySeen, todayEpochDay())
        return creditSeconds() - before
    }

    fun studyMinutesToday(): Int =
        history().firstOrNull { it.first == todayEpochDay() }?.second ?: 0

    fun last7Days(): List<Pair<Long, Int>> {
        val map = history().toMap()
        return (0 until 7).map { todayEpochDay() - it }.map { it to (map[it] ?: 0) }
    }

    private fun recordTodayMinutes(minutes: Int) {
        val day = todayEpochDay()
        val map = mutableMapOf<Long, Int>()
        history().forEach { map[it.first] = it.second }
        map[day] = (map[day] ?: 0) + minutes.coerceAtLeast(0)
        prefs.edit().putString(KEY_HISTORY, map.entries.joinToString("\n") { entry -> "${entry.key}:${entry.value}" }).apply()
    }

    private fun history(): List<Pair<Long, Int>> {
        val raw = prefs.getString(KEY_HISTORY, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split('\n').mapNotNull { line ->
            val p = line.split(':')
            if (p.size < 2) return@mapNotNull null
            val day = p[0].toLongOrNull() ?: return@mapNotNull null
            val minutes = p[1].toIntOrNull() ?: return@mapNotNull null
            day to minutes
        }
    }

    private fun todayEpochDay(): Long {
        val c = Calendar.getInstance()
        // Local-day bucket: days since epoch in local time.
        val offset = c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET)
        return (c.timeInMillis + offset) / 86_400_000L
    }
}
