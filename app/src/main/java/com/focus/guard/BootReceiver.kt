package com.focus.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // ---- SAFE MODE DETECTION ----
        // DISALLOW_SAFE_BOOT blocks the Settings toggle but NOT the hardware
        // Volume-Down + Power combo. In Safe Mode, all third-party apps
        // (including FocusGuard) are disabled and the user can uninstall us.
        //
        // We can't prevent Safe Mode entry without a custom ROM, BUT we CAN
        // detect on the next normal boot that Safe Mode was used, and:
        //   1. Record a tamper incident on the streak (so it's visible in UI)
        //   2. Trigger a relapse reset (Safe Mode entry is a deliberate act
        //      of weakening FocusGuard — exactly the impulse we're guarding)
        //   3. Re-apply all Device Owner protections immediately
        //
        // Detection: when the user is IN Safe Mode, the system sets
        // Settings.Global.SAFE_MODE = 1 AND the PackageManager reports
        // isSafeMode = true. We persist a flag when we observe either, then
        // on the next NORMAL boot (flag set + isSafeMode false) we fire the
        // tamper handler.
        val pm = context.packageManager
        val isCurrentlySafeMode = try { pm.isSafeMode } catch (_: Exception) { false }
        val prefs = context.getSharedPreferences("focusguard_policy", Context.MODE_PRIVATE)

        if (isCurrentlySafeMode) {
            // We're in Safe Mode right now — persist the flag and bail.
            // FocusGuard is mostly disabled in Safe Mode anyway, so we can't
            // do much here. The flag gets picked up on the next normal boot.
            prefs.edit().putBoolean("safe_mode_seen", true).apply()
            Log.w(TAG, "Safe Mode detected — flag set, will fire on next normal boot")
            return
        }

        if (prefs.getBoolean("safe_mode_seen", false)) {
            // We just came OUT of Safe Mode. Treat as tamper.
            prefs.edit().putBoolean("safe_mode_seen", false).apply()
            Log.w(TAG, "Safe Mode was used since last boot — recording tamper")
            try {
                val streak = StreakManager(context)
                streak.touch()  // runs normal clock-rollback check first
                // Force a relapse reset — Safe Mode entry is deliberate bypass.
                streak.recordRelapse()
            } catch (_: Exception) {}
            // Fall through: still re-engage all protections below.
        }

        GuardDeviceAdminReceiver.applyAllProtections(context)
        StreakManager(context).touch()
        context.startForegroundService(Intent(context, GuardForegroundService::class.java))
        AlarmScheduler.rescheduleAll(context)

        // MISSED-ALARM RE-FIRE: if the phone was powered off when an alarm
        // should have fired, detect it now and ring immediately — plus record
        // a streak relapse so powering off has a real cost. This is what makes
        // "I'll just switch off the phone" pointless: the alarm fires the
        // moment the phone boots back up, AND you lose your streak.
        checkMissedAlarms(context)

        // Re-apply the system-DNS lock (and the watchdog) on every boot.
        // The boot path is the most likely place a malicious actor would try
        // to revert Private DNS, so we do this unconditionally if the user
        // has previously engaged the lock.
        if (SystemDnsEnforcer.isEnabled(context)) {
            SystemDnsEnforcer.enforceNow(context)
            SystemDnsEnforcer.start(context)
            // Also re-launch the lightweight keeper service.
            DnsFilterService.start(context)
        }
    }

    companion object {
        private const val TAG = "FocusGuardBoot"
    }

    /**
     * Detects alarms that were missed because the phone was powered off.
     *
     * For each enabled alarm, computes today's scheduled time. If that time has
     * already passed AND the alarm hasn't fired since then (tracked via
     * [AlarmStore.markFired]), the phone was off when it should have rung.
     *
     * On detection:
     *  1. Records a streak relapse (powering off to dodge an alarm is a
     *     deliberate bypass — same as Safe Mode entry).
     *  2. Re-fires the alarm immediately (after a 3s delay to let the system
     *     settle post-boot), but only if it was missed within the last 6 hours
     *     — so we don't blast a 7am alarm at 9pm when the user finally boots.
     */
    private fun checkMissedAlarms(context: Context) {
        val now = System.currentTimeMillis()
        val reFireWindow = 6 * 60 * 60 * 1000L  // 6 hours
        var missedId = -1
        var missedTime = 0L

        for (a in AlarmStore.all(context)) {
            if (!a.enabled) continue
            val c = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, a.hour)
                set(java.util.Calendar.MINUTE, a.minute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val todayAlarm = c.timeInMillis
            // Today's alarm time has passed, but it never fired → phone was off.
            if (todayAlarm < now && AlarmStore.lastFired(context, a.id) < todayAlarm) {
                missedId = a.id
                missedTime = todayAlarm
                break
            }
        }

        if (missedId < 0) return

        Log.w(TAG, "Missed alarm $missedId (due $missedTime) — phone was off. Recording tamper + re-firing.")
        try { StreakManager(context).recordRelapse() } catch (_: Exception) {}

        if (now - missedTime > reFireWindow) return  // too long ago — skip the sound

        // Fire after a short delay so the system is fully booted.
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                val fire = Intent(context, AlarmReceiver::class.java).apply {
                    action = "com.focus.guard.ALARM_FIRE"
                    putExtra(AlarmScheduler.EXTRA_ID, missedId)
                }
                context.sendBroadcast(fire)
            } catch (_: Exception) {}
        }, 3000L)
    }
}
