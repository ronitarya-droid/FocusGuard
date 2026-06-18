package com.focus.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * New-install quarantine.
 *
 * The original problem: any brand-new sideloaded JAV / hentai / unblocker app
 * silently passed because we used to no-op here (we disabled auto-block so app
 * UPDATES wouldn't be rejected).
 *
 * Fix: distinguish NEW installs from UPDATES via Intent.EXTRA_REPLACING.
 *   - Updates (EXTRA_REPLACING=true)  → ignored, so Play Store / SelfUpdater
 *     keep working and OS updates aren't broken.
 *   - New installs (EXTRA_REPLACING=false / absent) → automatically added to
 *     the blocklist AND recorded in a "quarantine" store with a timestamp.
 *     The user can review the quarantine list in FocusGuard and explicitly
 *     approve (unblock) anything legit. This is default-deny: the safest
 *     possible posture when the question is "how do I block every adult app
 *     I don't even know exists yet?"
 *
 * This single change closes the "I'll just install a fresh JAV app" loophole
 * without breaking updates — which was the trade-off that forced the original
 * no-op.
 */
class PackageInstallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "FocusGuardInstall"
        const val QUARANTINE_PREFS = "focusguard_quarantine"

        /** Hours a freshly-installed app stays auto-blocked before the user
         *  is even allowed to approve it. Prevents the impulse "just approve
         *  it now and use it" reflex. */
        const val QUARANTINE_HOURS = 24L

        /** True if the package is currently in the 24h quarantine window. */
        fun isQuarantined(context: Context, pkg: String): Boolean {
            val ts = context.getSharedPreferences(QUARANTINE_PREFS, Context.MODE_PRIVATE)
                .getLong(pkg, 0L)
            if (ts == 0L) return false
            val ageMs = System.currentTimeMillis() - ts
            return ageMs < QUARANTINE_HOURS * 3_600_000L
        }

        /** Returns the set of packages that entered quarantine at any point,
         *  paired with their install timestamps (epoch ms). Sorted newest-first. */
        fun quarantineList(context: Context): List<Pair<String, Long>> =
            context.getSharedPreferences(QUARANTINE_PREFS, Context.MODE_PRIVATE).all
                .mapNotNull { (k, v) ->
                    if (v is Long) k to v else null
                }
                .sortedByDescending { it.second }

        /** Mark a quarantined app as approved by removing it from quarantine
         *  AND from the live blocklist. Caller is Challenge.toUnblock(...) so
         *  the user has to type a random code first. */
        fun approve(context: Context, pkg: String) {
            context.getSharedPreferences(QUARANTINE_PREFS, Context.MODE_PRIVATE)
                .edit().remove(pkg).apply()
            Blocklist.remove(context, pkg)
            Blocklist.pushLive(context)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED) return

        // The single most important check: is this an UPDATE of an existing
        // app, or a brand-new install? EXTRA_REPLACING is true for updates.
        val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        if (isReplacing) {
            // Update of an existing app — let it through. This preserves:
            //   - Play Store updates
            //   - SelfUpdater (FocusGuard's own APK install flow)
            //   - OS software updates
            // The original code no-op'd everything because of this case;
            // we now distinguish it explicitly.
            return
        }

        val pkg = intent.data?.schemeSpecificPart ?: return
        if (pkg.isEmpty()) return

        // Never quarantine FocusGuard itself or system packages.
        if (pkg == context.packageName) return
        if (pkg.startsWith("com.android.") || pkg.startsWith("com.google.android.")) {
            // Conservative: many Google/Android system packages self-update.
            // If a user wants to block a Google app (e.g. YouTube) they should
            // do it explicitly via the App Picker — those are in the hard-coded
            // blockedPackages set already.
            return
        }

        // Skip whitelisted packages (WhatsApp etc.) — user wants these always
        // allowed even if reinstalled.
        if (pkg in GuardAccessibilityService.WHITELISTED_PACKAGES) {
            Log.i(TAG, "Skipping whitelisted package: $pkg")
            return
        }

        // Skip apps that are ALREADY on the user's explicit blocklist — they
        // don't need to be quarantined again (no behavior change).
        if (Blocklist.isBlocked(context, pkg)) {
            Log.d(TAG, "Already blocked (no quarantine needed): $pkg")
            return
        }

        // QUARANTINE: block by default + record install timestamp.
        Blocklist.add(context, pkg)
        Blocklist.pushLive(context)
        context.getSharedPreferences(QUARANTINE_PREFS, Context.MODE_PRIVATE)
            .edit().putLong(pkg, System.currentTimeMillis()).apply()

        Log.i(TAG, "New install QUARANTINED for ${QUARANTINE_HOURS}h: $pkg")
    }
}
