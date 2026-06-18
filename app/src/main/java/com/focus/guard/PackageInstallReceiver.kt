package com.focus.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Newly-installed apps are NO LONGER auto-blocked. The user disabled this because
 * it interfered with app updates / software updates and over-blocked. Only apps
 * hard-coded in [GuardAccessibilityService.blockedPackages] or explicitly chosen
 * in the app picker are blocked. This receiver is intentionally a no-op now;
 * kept registered only so the manifest stays valid.
 */
class PackageInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // No-op by design — see class doc.
    }
}
