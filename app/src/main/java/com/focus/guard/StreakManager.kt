package com.focus.guard

import android.content.Context
import java.util.concurrent.TimeUnit

/**
 * Streak tracking with zero extra dependencies (SharedPreferences only).
 *
 * Model: the user has a continuous "clean" streak measured in days. Each day the
 * app is opened (or boot occurs) we roll the streak forward. A relapse — recorded
 * when the user explicitly taps "I slipped" — resets it to zero and stores the
 * best streak as a personal record.
 *
 * Streak decays: if the user does not touch() for more than 1 calendar day the
 * streak resets (the current run is banked as best). This prevents counting
 * idle days as "clean" when the user simply stopped engaging.
 */
class StreakManager(context: Context) {

    private val prefs = context.getSharedPreferences("focusguard_streak", Context.MODE_PRIVATE)

    private var startEpochDay: Long
        get() = prefs.getLong(KEY_START_DAY, todayEpochDay())
        set(v) = prefs.edit().putLong(KEY_START_DAY, v).apply()

    private var lastTouchEpochDay: Long
        get() = prefs.getLong(KEY_LAST_TOUCH, -1L)
        set(v) = prefs.edit().putLong(KEY_LAST_TOUCH, v).apply()

    private var best: Int
        get() = prefs.getInt(KEY_BEST, 0)
        set(v) = prefs.edit().putInt(KEY_BEST, v).apply()

    /** Ensures the streak is initialized; call on every launch/boot.
     *  Resets the streak if more than one calendar day has elapsed since the
     *  last touch — idle days should not count toward the streak. */
    fun touch() {
        val today = todayEpochDay()
        if (!prefs.contains(KEY_START_DAY)) {
            startEpochDay = today
            lastTouchEpochDay = today
        } else {
            val last = lastTouchEpochDay
            // Reset the streak if >1 day gap (user skipped a day).
            if (last >= 0 && today - last > 1) {
                if (currentStreakDays() > best) best = currentStreakDays()
                startEpochDay = today
            }
            lastTouchEpochDay = today
        }
        if (currentStreakDays() > best) best = currentStreakDays()
    }

    fun currentStreakDays(): Int =
        (todayEpochDay() - startEpochDay).coerceAtLeast(0).toInt()

    fun bestStreakDays(): Int = maxOf(best, currentStreakDays())

    /** User self-reports a relapse: bank the record, reset to zero. */
    fun recordRelapse() {
        if (currentStreakDays() > best) best = currentStreakDays()
        startEpochDay = todayEpochDay()
        lastTouchEpochDay = todayEpochDay()
    }

    private fun todayEpochDay(): Long =
        TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())

    companion object {
        private const val KEY_START_DAY = "start_epoch_day"
        private const val KEY_BEST = "best_streak"
        private const val KEY_LAST_TOUCH = "last_touch_epoch_day"
    }
}
