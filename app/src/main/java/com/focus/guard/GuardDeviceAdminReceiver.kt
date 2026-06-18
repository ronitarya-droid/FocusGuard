package com.focus.guard

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import android.util.Log

/**
 * The heart of FocusGuard's "hardcore" protection.
 *
 * All of the strong locks below ONLY work when this app is the **Device Owner**.
 * Device Owner is provisioned ONCE, on a freshly factory-reset device with no
 * accounts added, via:
 *
 * adb shell dpm set-device-owner com.focus.guard/.GuardDeviceAdminReceiver
 *
 * See README.md for the full provisioning walkthrough. Once set, the only way
 * to remove the app is a Recovery Mode factory reset — which is the intended,
 * deliberate escape hatch.
 */
class GuardDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "FocusGuardPolicy"

        fun adminComponent(context: Context): ComponentName =
            ComponentName(context, GuardDeviceAdminReceiver::class.java)

        private fun dpm(context: Context): DevicePolicyManager =
            context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        fun isDeviceOwner(context: Context): Boolean =
            dpm(context).isDeviceOwnerApp(context.packageName)

        /** Restrictions we add. Each is applied defensively (OEMs may reject some). */
        private val USER_RESTRICTIONS = listOf(
            UserManager.DISALLOW_FACTORY_RESET,           // no Settings-based wipe
            UserManager.DISALLOW_SAFE_BOOT,               // no Safe Mode key combo
            UserManager.DISALLOW_DEBUGGING_FEATURES,      // no USB debugging / ADB
            UserManager.DISALLOW_ADD_USER,                // no new users to dodge policy
            UserManager.DISALLOW_CONFIG_VPN               // no rogue VPN to bypass filtering
            // REMOVED: DISALLOW_INSTALL_APPS / DISALLOW_INSTALL_UNKNOWN_SOURCES —
            //   they blocked app updates AND OS software updates. User wants
            //   installs/updates to always work; only specific apps are blocked.
        )

        /** Keep Private DNS locked once the family DNS filter is enabled. */
        private val DNS_RESTRICTIONS = listOf(UserManager.DISALLOW_CONFIG_PRIVATE_DNS)

        /**
         * Apply every protection. Safe to call repeatedly (idempotent) — we call
         * it on boot, on app launch, and on admin-enabled so policy self-heals.
         */
        fun applyAllProtections(context: Context) {
            val dpm = dpm(context)
            val admin = adminComponent(context)
            if (!dpm.isDeviceOwnerApp(context.packageName)) {
                Log.w(TAG, "Not Device Owner — strong protections unavailable.")
                return
            }

            // 1. Block uninstall of FocusGuard specifically. (Ye FocusGuard ko delete hone se rokega)
            safe("setUninstallBlocked") { dpm.setUninstallBlocked(admin, context.packageName, true) }
            // Prevent reinstall after any uninstall attempt (backup for setUninstallBlocked).
            safe("setKeepUninstalledPackages") {
                dpm.setKeepUninstalledPackages(admin, listOf(context.packageName))
            }
            // Anti-disable / anti-force-stop / anti-clear-data is per-app via the
            // a11y service's self-defense (blocks Settings/ColorOS app info screens
            // targeting FocusGuard). Global DISALLOW_APPS_CONTROL is NOT used so
            // that other apps remain force-stop/disable-able. We explicitly clear
            // it here in case an old version of FocusGuard applied it globally.
            safe("clearAppsControlRestriction") {
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL)
            }

            // 2. System-wide user restrictions. Full lockdown requires BOTH
            //    maintenance_mode=false AND lockdown_confirmed=true. The second
            //    flag is ONLY set by the UI "Engage Full Lockdown" dialog, so a
            //    direct ADB SharedPreferences write can never accidentally lock
            //    the device — the user must explicitly tap confirm.
            val prefs = context.getSharedPreferences("focusguard_policy", Context.MODE_PRIVATE)
            val maintenance = prefs.getBoolean("maintenance_mode", true)
            val lockdownConfirmed = prefs.getBoolean("lockdown_confirmed", false)
            val locked = !maintenance && lockdownConfirmed
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (prefs.getBoolean("dns_filter_enabled", false)) {
                    DNS_RESTRICTIONS.forEach { r ->
                        safe("addDnsSettingRestriction:$r") { dpm.addUserRestriction(admin, r) }
                    }
                } else {
                    DNS_RESTRICTIONS.forEach { r ->
                        safe("clearDnsSettingRestriction:$r") { dpm.clearUserRestriction(admin, r) }
                    }
                }
            }
            // CRITICAL: When NOT in full lockdown, explicitly clear the maintenance
            // restrictions. The old code only skipped adding them — if they were
            // previously added during a lockdown, they'd stay locked forever.
            if (!locked) {
                MAINTENANCE_RESTRICTIONS.forEach { r ->
                    safe("clearUserRestriction(maintenance):$r") { dpm.clearUserRestriction(admin, r) }
                }
            }
            USER_RESTRICTIONS.forEach { r ->
                if (!locked && r in MAINTENANCE_RESTRICTIONS) return@forEach
                safe("addUserRestriction:$r") { dpm.addUserRestriction(admin, r) }
            }

            // 3. Reset the global permission policy back to PROMPT — undoing any
            //    previously-applied AUTO_GRANT, which had greyed out every app's
            //    notification toggle (POST_NOTIFICATIONS is a runtime permission on
            //    Android 13+). Then grant ONLY FocusGuard's own notification perm,
            //    leaving all other apps user-controllable.
            safe("resetPermissionPolicy") {
                dpm.setPermissionPolicy(admin, DevicePolicyManager.PERMISSION_POLICY_PROMPT)
            }
            // CRITICAL: resetting the policy does NOT un-grey permissions that the
            // old AUTO_GRANT already locked. We must explicitly hand each app's
            // notification permission back to user control (GRANT_STATE_DEFAULT).
            // Run once (flagged) so we don't iterate every package on every launch.
            if (!prefs.getBoolean("notif_released_v2", false)) {
                safe("releaseAllNotificationToggles") {
                    val pm = context.packageManager
                    for (app in pm.getInstalledApplications(0)) {
                        if (app.packageName == context.packageName) continue
                        try {
                            dpm.setPermissionGrantState(
                                admin, app.packageName,
                                android.Manifest.permission.POST_NOTIFICATIONS,
                                DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
                            )
                        } catch (_: Exception) {}
                    }
                }
                prefs.edit().putBoolean("notif_released_v2", true).apply()
            }
            // Force-grant FocusGuard's OWN notification permission so the alarm
            // full-screen-intent and the watchdog notice can always post. This
            // only locks OUR toggle on — other apps stay user-controllable.
            safe("grantOwnNotifications") {
                dpm.setPermissionGrantState(
                    admin, context.packageName,
                    android.Manifest.permission.POST_NOTIFICATIONS,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                )
            }

            // 3b. Allowlist accessibility services to ours + common system services.
            //     NOTE: this does NOT stop the user disabling OUR service (Android
            //     reserves that as a user action — see GuardForegroundService
            //     watchdog), but it does stop a rogue accessibility service being
            //     added to fight FocusGuard.
            safe("setPermittedAccessibilityServices") {
                dpm.setPermittedAccessibilityServices(admin, listOf(
                    context.packageName,
                    "com.google.android.marvin.talkback",
                    "com.android.talkback",
                    "com.samsung.android.app.talkback",
                    "com.google.android.apps.accessibility.selecttospeak",
                    "com.outfit7.miwise.smartthings"  // Select-to-Speak (some variants)
                ))
            }

            // 4. Private DNS policy:
            //    - If the user has NOT engaged the DNS filter, pin to
            //      "opportunistic" so a triggering DoH provider can't be used
            //      to tunnel around domain filtering.
            //    - If the user HAS engaged the DNS filter, leave the
            //      hostname lock alone — SystemDnsEnforcer manages it. Doing
            //      anything here would clobber the family's hostname pin.
            //    Requires API 29+ and Device Owner.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (prefs.getBoolean("dns_filter_enabled", false)) {
                    // Re-assert the family filter hostname in case something
                    // (boot, OEM wizard, malicious app) flipped it off.
                    safe("enforceFamilyDns") {
                        SystemDnsEnforcer.enforceNow(context)
                    }
                } else {
                    safe("setGlobalPrivateDnsModeOpportunistic") {
                        dpm.setGlobalPrivateDnsModeOpportunistic(admin)
                    }
                }
            }

            // 5. Keep FocusGuard exempt from battery optimization so the guard
            //    services are never killed (low-battery requirement: the services
            //    themselves are lightweight; this just prevents Doze kills).
            Log.i(TAG, "All available protections applied.")
        }

        /** Maintenance restrictions: lifted together so that, during maintenance,
         * adb works AND adb can install app updates. (Install-blocking alone
         * would still reject `adb install` even with debugging back on.) */
        private val MAINTENANCE_RESTRICTIONS = listOf(
            UserManager.DISALLOW_DEBUGGING_FEATURES
        )

        /** Lifts the debugging + install restrictions so adb / Developer Options
         * work again AND updates can be installed — without removing the core
         * protections (uninstall, VPN, safe-boot, factory-reset stay ON).
         * This is the escape hatch from the bootstrap trap. */
        fun unlockDebugging(context: Context) {
            val dpm = dpm(context)
            val admin = adminComponent(context)
            if (!dpm.isDeviceOwnerApp(context.packageName)) return
            MAINTENANCE_RESTRICTIONS.forEach { r ->
                safe("clearMaintenanceRestriction:$r") { dpm.clearUserRestriction(admin, r) }
            }
        }

        /** Removes the locks. Only usable while still Device Owner; intended for
         * a future "supervised unlock" flow, NOT exposed in normal UI. */
        fun releaseProtections(context: Context) {
            val dpm = dpm(context)
            val admin = adminComponent(context)
            if (!dpm.isDeviceOwnerApp(context.packageName)) return
            safe("clearUninstallBlocked") { dpm.setUninstallBlocked(admin, context.packageName, false) }
            USER_RESTRICTIONS.forEach { r ->
                safe("clearUserRestriction:$r") { dpm.clearUserRestriction(admin, r) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                DNS_RESTRICTIONS.forEach { r ->
                    safe("clearDnsSettingRestriction:$r") { dpm.clearUserRestriction(admin, r) }
                }
            }
        }

        private inline fun safe(label: String, block: () -> Unit) {
            try {
                block()
            } catch (e: Exception) {
                Log.w(TAG, "Policy step failed: $label -> ${e.message}")
            }
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        applyAllProtections(context)
    }

    /** Fired when Device Owner provisioning completes via the ADB command. */
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        applyAllProtections(context)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "FocusGuard is locked. It can only be removed via a Recovery Mode factory reset."
}