package com.focus.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
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
}
