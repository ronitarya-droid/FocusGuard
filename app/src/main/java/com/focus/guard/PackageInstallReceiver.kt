package com.focus.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Permanent install quarantine — default-deny with zero approval gate.
 *
 * Every brand-new app install is **permanently** blocked. There is no approval
 * mechanism — the only way to install a new app is via ADB (which requires
 * enabling debugging, which itself is locked behind maintenance mode).
 *
 * Heuristic detection: if the package name contains adult or suspicious
 * signatures, it gets an extra-hard block with a logged reason.
 */
class PackageInstallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "FocusGuardInstall"
        const val QUARANTINE_PREFS = "focusguard_quarantine"

        /** Package-name fragments that SCREAM "adult / porn / nsfw". */
        private val ADULT_SIGNATURES = listOf(
            "porn", "xxx", "adult", "18+", "nsfw", "hentai", "jav",
            "sexy", "nude", "cam", "escort", "hookup", "swipe",
            "dating", "milf", "creampie", "blowjob", "threesome",
            "gangbang", "onlyfans", "chaturbate", "stripchat",
            "xvideo", "xnxx", "xhamster", "brazzers", "redtube"
        )

        /** Package-name fragments for suspicious / unblocker / modded apps. */
        private val SUSPICIOUS_SIGNATURES = listOf(
            "mod", "hack", "cheat", "crack", "unblock", "bypass",
            "proxy", "vpn", "tor.", "psiphon", "tunnel"
        )

        /** Returns the set of packages that entered quarantine, with install
         *  timestamps (epoch ms). Sorted newest-first. */
        fun quarantineList(context: Context): List<Pair<String, Long>> =
            context.getSharedPreferences(QUARANTINE_PREFS, Context.MODE_PRIVATE).all
                .mapNotNull { (k, v) ->
                    if (v is Long) k to v else null
                }
                .sortedByDescending { it.second }

        /** Check whether a package name contains adult content signatures. */
        fun hasAdultSignature(pkg: String): Boolean =
            ADULT_SIGNATURES.any { it in pkg.lowercase() }

        /** Check whether a package name contains suspicious/unblocker signatures. */
        fun hasSuspiciousSignature(pkg: String): Boolean =
            SUSPICIOUS_SIGNATURES.any { it in pkg.lowercase() }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED) return

        // Updates pass through — preserves Play Store, SelfUpdater, OS updates.
        val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        if (isReplacing) return

        val pkg = intent.data?.schemeSpecificPart ?: return
        if (pkg.isEmpty()) return

        // Never quarantine FocusGuard itself or system packages.
        if (pkg == context.packageName) return
        if (pkg.startsWith("com.android.") || pkg.startsWith("com.google.android.")) return

        // Skip whitelisted packages (WhatsApp etc.).
        if (pkg in GuardAccessibilityService.WHITELISTED_PACKAGES) {
            Log.i(TAG, "Skipping whitelisted package: $pkg")
            return
        }

        // Skip already-blocked packages.
        if (Blocklist.isBlocked(context, pkg)) {
            Log.d(TAG, "Already blocked: $pkg")
            return
        }

        // PERMANENT QUARANTINE — no approval, no expiry.
        Blocklist.add(context, pkg)
        Blocklist.pushLive(context)
        context.getSharedPreferences(QUARANTINE_PREFS, Context.MODE_PRIVATE)
            .edit().putLong(pkg, System.currentTimeMillis()).apply()

        // Log detection reason for auditing.
        val pkgLc = pkg.lowercase()
        val reason = when {
            hasAdultSignature(pkgLc) -> "ADULT signature detected"
            hasSuspiciousSignature(pkgLc) -> "SUSPICIOUS signature detected"
            else -> "default-deny quarantine"
        }
        Log.i(TAG, "PERMANENTLY QUARANTINED [$reason]: $pkg")
    }
}
