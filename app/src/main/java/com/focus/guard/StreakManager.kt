package com.focus.guard

import android.content.Context
import android.os.SystemClock
import android.util.Log
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
 *
 * CLOCK-ROLLBACK DEFENSE (v2):
 * Wall-clock time (System.currentTimeMillis) is user-settable from Settings.
 * Without protection, a user in a moment of weakness could:
 *   - Set the date BACK one day to undo a relapse they just recorded
 *   - Set the date FORWARD to "skip" a no-touch gap
 *
 * We defend by tracking BOTH:
 *   1. Wall-clock time (for the user-facing "calendar day" semantics)
 *   2. elapsedRealtime() — a monotonic counter that ONLY goes up, never
 *      rolls back, and survives reboots (resets only on factory reset,
 *      which is already the FocusGuard escape hatch)
 *
 * On every touch() we check: has wall-clock gone BACKWARDS, or has it
 * jumped FORWARD by more than elapsedRealtime would allow? If so, we
 * treat it as a tamper attempt:
 *   - Bank the current streak as best (don't let them keep a fraudulent one)
 *   - Reset the streak to zero
 *   - Log the incident
 *
 * Note: elapsedRealtime resets to 0 on factory reset — but factory reset
 * is already the FocusGuard escape hatch, so post-reset the user is
 * reinstalling from scratch anyway. No data integrity loss.
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

    /** Last wall-clock we saw at touch() — used to detect rollback. */
    private var lastWallClockMs: Long
        get() = prefs.getLong(KEY_LAST_WALL_CLOCK, 0L)
        set(v) = prefs.edit().putLong(KEY_LAST_WALL_CLOCK, v).apply()

    /** Last monotonic timestamp (elapsedRealtime) we saw at touch(). */
    private var lastMonotonicMs: Long
        get() = prefs.getLong(KEY_LAST_MONOTONIC, 0L)
        set(v) = prefs.edit().putLong(KEY_LAST_MONOTONIC, v).apply()

    /** Count of clock-tamper incidents detected. Surfaced in the UI as
     *  "We noticed the system clock was changed N times." */
    private var tamperCount: Int
        get() = prefs.getInt(KEY_TAMPER_COUNT, 0)
        set(v) = prefs.edit().putInt(KEY_TAMPER_COUNT, v).apply()

    /** True if a clock rollback was detected at least once. The UI can
     *  read this to shame the user appropriately. */
    fun wasTampered(): Boolean = tamperCount > 0
    fun tamperIncidents(): Int = tamperCount

    /** Ensures the streak is initialized; call on every launch/boot.
     *  Resets the streak if more than one calendar day has elapsed since the
     *  last touch — idle days should not count toward the streak.
     *
     *  ALSO detects clock-tamper: if wall-clock went backwards, or if it
     *  jumped forward by more than elapsedRealtime advanced, we treat it as
     *  a tamper attempt and reset the streak. */
    fun touch() {
        val today = todayEpochDay()
        val nowWall = System.currentTimeMillis()
        val nowMono = SystemClock.elapsedRealtime()

        // First-ever call — initialize everything, no tamper check possible.
        if (!prefs.contains(KEY_START_DAY)) {
            startEpochDay = today
            lastTouchEpochDay = today
            lastWallClockMs = nowWall
            lastMonotonicMs = nowMono
            return
        }

        // TAMPER CHECK 1: Wall clock went BACKWARDS.
        //   Tolerance: 5 minutes (NTP corrections can drift wall clock slightly).
        if (nowWall < lastWallClockMs - ROLLBACK_TOLERANCE_MS) {
            handleTamper("rollback",
                "wall went backwards: last=$lastWallClockMs now=$nowWall " +
                "delta=${lastWallClockMs - nowWall}ms")
            return  // handleTamper resets state; we're done for this touch()
        }

        // TAMPER CHECK 2: Wall clock jumped FORWARD by more than monotonic.
        //   Example: monotonic advanced 1 hour, but wall clock advanced 3 days
        //   → user set date forward to skip past a no-touch gap.
        //   Tolerance: 1 hour (handles normal background time + small NTP jumps).
        val wallDelta = nowWall - lastWallClockMs
        val monoDelta = nowMono - lastMonotonicMs
        if (wallDelta > monoDelta + FORWARD_TOLERANCE_MS && monoDelta >= 0) {
            handleTamper("fast-forward",
                "wall advanced $wallDelta ms but monotonic only $monoDelta ms " +
                "(delta=${wallDelta - monoDelta}ms)")
            return
        }

        // Normal path: no tamper detected. Roll streak forward / decay as before.
        val last = lastTouchEpochDay
        if (last >= 0 && today - last > 1) {
            // User skipped a day — bank current streak as best, reset.
            if (currentStreakDays() > best) best = currentStreakDays()
            startEpochDay = today
        }
        lastTouchEpochDay = today
        lastWallClockMs = nowWall
        lastMonotonicMs = nowMono

        if (currentStreakDays() > best) best = currentStreakDays()
    }

    /** Tamper detected — bank the (potentially fraudulent) run as best so
     *  they can't keep it, reset to zero, log the incident. */
    private fun handleTamper(kind: String, detail: String) {
        Log.w(TAG, "Clock tamper detected [$kind]: $detail")
        if (currentStreakDays() > best) best = currentStreakDays()
        val today = todayEpochDay()
        startEpochDay = today
        lastTouchEpochDay = today
        lastWallClockMs = System.currentTimeMillis()
        lastMonotonicMs = SystemClock.elapsedRealtime()
        tamperCount = tamperCount + 1
    }

    fun currentStreakDays(): Int =
        (todayEpochDay() - startEpochDay).coerceAtLeast(0).toInt()

    fun bestStreakDays(): Int = maxOf(best, currentStreakDays())

    /** User self-reports a relapse: bank the record, reset to zero. */
    fun recordRelapse() {
        if (currentStreakDays() > best) best = currentStreakDays()
        startEpochDay = todayEpochDay()
        lastTouchEpochDay = todayEpochDay()
        lastWallClockMs = System.currentTimeMillis()
        lastMonotonicMs = SystemClock.elapsedRealtime()
    }

    private fun todayEpochDay(): Long =
        TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())

    companion object {
        private const val TAG = "FocusGuardStreak"
        private const val KEY_START_DAY = "start_epoch_day"
        private const val KEY_BEST = "best_streak"
        private const val KEY_LAST_TOUCH = "last_touch_epoch_day"
        private const val KEY_LAST_WALL_CLOCK = "last_wall_clock_ms"
        private const val KEY_LAST_MONOTONIC = "last_monotonic_ms"
        private const val KEY_TAMPER_COUNT = "tamper_count"

        /** Tolerance for wall-clock going backwards. 5 minutes absorbs NTP
         *  corrections without false-flagging legitimate users. */
        private const val ROLLBACK_TOLERANCE_MS = 5L * 60_000L

        /** Tolerance for wall-clock going forward faster than monotonic time.
         *  1 hour absorbs small NTP jumps + normal background-time jitter. */
        private const val FORWARD_TOLERANCE_MS = 60L * 60_000L
    }
}
