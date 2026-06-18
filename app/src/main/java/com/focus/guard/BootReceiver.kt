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
}
