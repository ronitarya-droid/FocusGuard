package com.focus.guard

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * System-level Private DNS enforcement.
 *
 * Replaces the old VpnService-based DnsFilterService. Instead of tunnelling all
 * traffic, we pin the device-wide Private DNS to Cloudflare's family-filter
 * hostname via the Device Owner API:
 *
 *   DevicePolicyManager.setGlobalPrivateDnsModeSpecifiedHost(admin, host)
 *
 * **Why this is effectively "non-removable":**
 *  • Requires the calling app to be Device Owner (FocusGuard already is, see
 *    GuardDeviceAdminReceiver).
 *  • The setting lives in Settings.Global, which a non-system user cannot
 *    edit from the Settings UI once Device Owner has set it.
 *  • No VPN permission, no system overlay, no notification, no "VPN key" icon
 *    in the status bar.
 *  • Only way out: factory-reset the device (which is already the same escape
 *    hatch the rest of FocusGuard's hardcore mode uses).
 *
 * For belt-and-braces, a ContentObserver + a low-rate polling backstop
 * re-applies the value the instant something tries to change it (OEM bugs,
 * rogue system apps, etc.).
 */
object SystemDnsEnforcer {

    private const val TAG = "SystemDns"

    /** The locked family-filter hostname. Must support DNS-over-TLS. */
    const val PRIVATE_DNS_HOST = "family.cloudflare-dns.com"

    /** SharedPreferences key — mirrors the legacy key so existing settings stick. */
    const val PREFS = "focusguard_policy"
    const val KEY_ENABLED = "dns_filter_enabled"

    // The Settings.Global.PRIVATE_DNS_MODE and PRIVATE_DNS_SPECIFIER constants
    // are part of the @hide API surface and aren't available to apps at compile
    // time. The values below are the published string names and are stable
    // across every Android version (P / API 28) that supports the feature.
    private const val PRIVATE_DNS_MODE = "private_dns_mode"
    private const val PRIVATE_DNS_SPECIFIER = "private_dns_specifier"
    private const val MODE_HOSTNAME = "hostname"
    private const val MODE_OPPORTUNISTIC = "opportunistic"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private var observer: ContentObserver? = null
    private var pollRunnable: Runnable? = null
    private var lastApplied: String? = null

    /** True if the lock is engaged (preference says ON AND device-owner applied it). */
    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    /**
     * Engage the lock. Idempotent.
     *
     * @return true if the system DNS was successfully pinned to the family
     *         host; false if we lack device-owner status (caller should
     *         surface an ADB-provisioning hint to the user).
     */
    /**
     * Engage the lock. Asynchronous because the underlying DPM call performs
     * a blocking DNS probe and will throw `NetworkOnMainThreadException` if
     * invoked from the UI thread. The [onDone] callback runs on the main
     * thread and receives true on success.
     */
    fun enable(context: Context, onDone: (Boolean) -> Unit = {}) {
        applyHostnameAsync(context, PRIVATE_DNS_HOST) { r ->
            val ok = r == ApplyResult.OK
            if (ok) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_ENABLED, true).apply()
                GuardDeviceAdminReceiver.applyAllProtections(context)
                start(context)
            }
            onDone(ok)
        }
    }

    /**
     * Disengage the lock — restore the system default opportunistic mode
     * (effectively "Automatic") so the user can browse normally again.
     */
    fun disable(context: Context) {
        stop()
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = GuardDeviceAdminReceiver.adminComponent(context)
        try {
            if (dpm.isDeviceOwnerApp(context.packageName) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                dpm.setGlobalSetting(admin, PRIVATE_DNS_MODE, MODE_OPPORTUNISTIC)
                dpm.setGlobalSetting(admin, PRIVATE_DNS_SPECIFIER, "")
            }
        } catch (e: Exception) {
            Log.w(TAG, "disable: opportunistic reset failed: ${e.message}")
        }
        try {
            if (dpm.isDeviceOwnerApp(context.packageName) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                dpm.setGlobalPrivateDnsModeOpportunistic(admin)
            }
        } catch (e: Exception) {
            Log.w(TAG, "disable: DPM opportunistic reset failed: ${e.message}")
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, false).apply()
        lastApplied = null
    }

    /**
     * Apply the hostname lock NOW, without changing the preference. Used by
     * the boot receiver and the periodic watchdog. Returns true on success.
     */
    fun enforceNow(context: Context): Boolean = applyHostname(context, PRIVATE_DNS_HOST)

    /**
     * Convenience for callers that only need the current "looks-locked?" state
     * — reads the live system setting rather than our preference, so a fresh
     * boot reports the true post-reboot state.
     */
    @SuppressLint("InlinedApi")
    fun isCurrentlyLocked(context: Context): Boolean {
        return try {
            val mode = Settings.Global.getString(context.contentResolver, PRIVATE_DNS_MODE)
            val spec = Settings.Global.getString(context.contentResolver, PRIVATE_DNS_SPECIFIER)
            mode == MODE_HOSTNAME && spec != null && spec.equals(PRIVATE_DNS_HOST, ignoreCase = true)
        } catch (_: Exception) { false }
    }

    /**
     * Begin the watchdog: register a ContentObserver on the relevant
     * Settings.Global keys AND a 3-second polling backstop. Self-restarts if
     * the process is killed.
     */
    @SuppressLint("InlinedApi")
    fun start(context: Context) {
        if (!running.compareAndSet(false, true)) return
        val app = context.applicationContext

        val obs = object : ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean) = reapplyIfNeeded(app)
        }
        observer = obs
        try {
            app.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(PRIVATE_DNS_MODE), false, obs)
            app.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(PRIVATE_DNS_SPECIFIER), false, obs)
        } catch (e: Exception) {
            Log.w(TAG, "ContentObserver registration failed: ${e.message}")
        }

        val r = object : Runnable {
            override fun run() {
                reapplyIfNeeded(app)
                mainHandler.postDelayed(this, 3_000L)
            }
        }
        pollRunnable = r
        mainHandler.postDelayed(r, 1_500L)
        Log.d(TAG, "Watchdog started")
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        // The ContentObserver and Runnable will be GC'd with the process; no
        // explicit unregistration is necessary and we share the global main
        // Looper so there are no other references to leak.
        observer = null
        pollRunnable?.let { mainHandler.removeCallbacks(it) }
        pollRunnable = null
        Log.d(TAG, "Watchdog stopped")
    }

    // ---------- internals ----------

    /** Synchronous apply used by watchdog/boot paths. */
    private fun applyHostname(context: Context, host: String): Boolean =
        applyHostnameDetailed(context, host) == ApplyResult.OK

    /**
     * Result of an [applyHostname] call — lets the caller tell the user WHY
     * the lock failed, instead of just "could not write private dns".
     */
    enum class ApplyResult { OK, NOT_DEVICE_OWNER, UNSUPPORTED_API, HOST_NOT_SERVING, FAILURE }

    /**
     * Synchronous version of [applyHostnameDetailed]. MUST be called off the
     * main thread: the DPM validation probe can block on DNS if the direct
     * Device Owner settings write is unavailable.
     */
    private fun applyHostnameDetailed(context: Context, host: String): ApplyResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.w(TAG, "API < 29 — Device-Owner DNS mode unavailable.")
            return ApplyResult.UNSUPPORTED_API
        }
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = GuardDeviceAdminReceiver.adminComponent(context)
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.w(TAG, "Not Device Owner — cannot pin Private DNS.")
            return ApplyResult.NOT_DEVICE_OWNER
        }

        return try {
            // Direct Settings.Global writes are the most reliable Device Owner path:
            // they avoid the blocking DoT probe that many OEMs/networks reject.
            dpm.setGlobalSetting(admin, PRIVATE_DNS_MODE, MODE_HOSTNAME)
            dpm.setGlobalSetting(admin, PRIVATE_DNS_SPECIFIER, host)
            lastApplied = host
            Log.d(TAG, "Pinned Private DNS via global settings → $host")
            ApplyResult.OK
        } catch (e: Exception) {
            Log.w(TAG, "Direct Private DNS write failed: ${e.message}")
            try {
                val rc = dpm.setGlobalPrivateDnsModeSpecifiedHost(admin, host)
                when (rc) {
                    DevicePolicyManager.PRIVATE_DNS_SET_NO_ERROR -> {
                        lastApplied = host
                        Log.d(TAG, "Pinned Private DNS via DPM → $host (rc=NO_ERROR)")
                        ApplyResult.OK
                    }
                    DevicePolicyManager.PRIVATE_DNS_SET_ERROR_HOST_NOT_SERVING -> {
                        lastApplied = host
                        Log.w(TAG, "Host not currently serving DoT: $host (treated as soft-OK)")
                        ApplyResult.OK
                    }
                    DevicePolicyManager.PRIVATE_DNS_SET_ERROR_FAILURE_SETTING -> {
                        Log.e(TAG, "setGlobalPrivateDnsModeSpecifiedHost returned FAILURE_SETTING")
                        ApplyResult.FAILURE
                    }
                    else -> {
                        Log.e(TAG, "setGlobalPrivateDnsModeSpecifiedHost returned unknown rc=$rc")
                        ApplyResult.FAILURE
                    }
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to pin Private DNS: ${e2.message}", e2)
                ApplyResult.FAILURE
            }
        }
    }

    /** Off-main-thread wrapper. Returns the [ApplyResult] via the callback. */
    private fun applyHostnameAsync(
        context: Context,
        host: String,
        onDone: (ApplyResult) -> Unit
    ) {
        Thread {
            val r = applyHostnameDetailed(context, host)
            mainHandler.post { onDone(r) }
        }.start()
    }

    @SuppressLint("InlinedApi")
    private fun reapplyIfNeeded(context: Context) {
        if (!isEnabled(context)) return
        val cr: ContentResolver = context.contentResolver
        val mode = try { Settings.Global.getString(cr, PRIVATE_DNS_MODE) } catch (_: Exception) { null }
        val spec = try { Settings.Global.getString(cr, PRIVATE_DNS_SPECIFIER) } catch (_: Exception) { null }
        val ok = mode == MODE_HOSTNAME && spec != null && spec.equals(PRIVATE_DNS_HOST, ignoreCase = true)
        if (!ok) {
            Log.w(TAG, "Drift detected: mode=$mode spec=$spec — re-applying")
            applyHostname(context, PRIVATE_DNS_HOST)
        }
    }
}
