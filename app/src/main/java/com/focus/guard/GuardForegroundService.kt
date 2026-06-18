package com.focus.guard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

/**
 * Persistent guard service. Beyond keeping FocusGuard alive, this is the
 * **accessibility watchdog** — the part the AccessibilityService itself can't do.
 *
 * Android deliberately blinds an accessibility service to its own revoke screen
 * (confirmed on CPH2363: a 3.3s event blackout while "Remove access" was tapped),
 * so the in-service overlay can never catch the toggle. This SEPARATE service is
 * NOT blinded: it watches the `enabled_accessibility_services` secure setting via
 * a ContentObserver (+ a polling backstop). The instant FocusGuard's service is
 * removed, it slams up a full-screen nag overlay and re-opens the accessibility
 * settings page, so re-enabling is the path of least resistance.
 *
 * True prevention is impossible without a platform signature / custom ROM — this
 * is maximum friction, which is the realistic ceiling and what BlockerX/AppBlock
 * also do.
 */
class GuardForegroundService : Service() {

    companion object { private const val TAG = "FocusGuardWatch" }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var nag: View? = null
    private var observer: ContentObserver? = null

    private val poll = object : Runnable {
        override fun run() {
            syncNagWithState()
            mainHandler.postDelayed(this, 2000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val channelId = "guard"
        if (Build.VERSION.SDK_INT >= 26) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(
                    NotificationChannel(channelId, "Guard", NotificationManager.IMPORTANCE_LOW))
        }
        val n = NotificationCompat.Builder(this, channelId)
            .setContentTitle("FocusGuard active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true).build()
        startForeground(1, n)

        // Watch the accessibility setting for changes (fires on enable AND disable).
        observer = object : ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean) = syncNagWithState()
        }.also {
            contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                false, it
            )
        }
        // Polling backstop in case the observer misses a change.
        mainHandler.post(poll)
    }

    /** Show the nag iff our accessibility service is currently NOT enabled. */
    private fun syncNagWithState() {
        if (isAccessibilityEnabled()) removeNag() else showNag()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = "$packageName/$packageName.GuardAccessibilityService"
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {}
    }

    private fun showNag() {
        // Re-open settings each time we (re)assert the nag, so the toggle is one tap away.
        openAccessibilitySettings()

        if (nag != null) return
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Accessibility OFF but no overlay permission — cannot nag.")
            return
        }
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = FrameLayout(this).apply {
            setBackgroundColor(0xF21A1A1A.toInt())
            isClickable = true
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }
        column.addView(TextView(this).apply {
            text = "⚠  FocusGuard accessibility is OFF"
            setTextColor(Color.WHITE); textSize = 22f; gravity = Gravity.CENTER
        })
        column.addView(TextView(this).apply {
            text = "Your blocking is disabled. Re-enable FocusGuard accessibility to continue. " +
                   "This screen stays until you do — the app can't be uninstalled and the phone " +
                   "can't be reset from Settings, so re-enabling is the way forward."
            setTextColor(0xFFCCCCCC.toInt()); textSize = 15f; gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(28))
        })
        column.addView(Button(this).apply {
            text = "Re-enable accessibility"
            setOnClickListener { openAccessibilitySettings() }
        })
        root.addView(column, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ).apply { gravity = Gravity.CENTER })

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(root, lp)
            nag = root
            Log.d(TAG, "Nag overlay shown (accessibility OFF).")
        } catch (e: Exception) {
            nag = null
            Log.w(TAG, "Failed to show nag: ${e.message}")
        }
    }

    private fun removeNag() {
        val v = nag ?: return
        nag = null
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v)
            Log.d(TAG, "Nag overlay removed (accessibility back ON).")
        } catch (_: Exception) {}
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        syncNagWithState()
        return START_STICKY
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        val svc = Intent(applicationContext, GuardForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc) else startService(svc)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(poll)
        observer?.let { contentResolver.unregisterContentObserver(it) }
        removeNag()
        // Best-effort restart so the watchdog can't be permanently killed.
        try {
            val svc = Intent(applicationContext, GuardForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc) else startService(svc)
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
