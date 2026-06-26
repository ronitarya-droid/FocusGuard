package com.focus.guard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Lightweight keeper for [SystemDnsEnforcer].
 *
 * Old behaviour: this class extended [android.net.VpnService] and built a
 * packet-relay tunnel to Cloudflare Family DNS. That required a per-launch VPN
 * consent prompt, showed a persistent VPN-key notification, and frequently
 * failed with "DNS permission not granted" when the user dismissed the
 * prompt — which is what the user reported.
 *
 * New behaviour: pure system-level Private DNS pinning via Device Owner.
 * This service's only job is to keep the process alive (so the watchdog
 * can't be killed) and to give a single, silent foreground notification
 * on Android 8+ so the system doesn't kill us.
 *
 * There is NO VPN icon, NO consent prompt, NO system overlay.
 */
class DnsFilterService : Service() {

    companion object {
        const val CHANNEL_ID = "dns_filter"
        const val NOTIF_ID = 42
        const val TAG = "DnsFilter"

        @Volatile var isRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, DnsFilterService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** No-op for API compatibility — the lock is now managed by the
         *  user's preference + Device Owner policy, not by this service. */
        fun stop(context: Context) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        SystemDnsEnforcer.start(this)
        isRunning = true
        Log.d(TAG, "Service up — system DNS enforcement active")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        isRunning = false
        // Intentionally do NOT call SystemDnsEnforcer.stop() — we want the
        // watchdog to keep running in the main process / via the other
        // foreground service. Kill only stops the notification.
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground() {
        if (Build.VERSION.SDK_INT >= 26) {
            val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            // createNotificationChannel is idempotent — creating an existing
            // channel is a no-op (it keeps its old importance). We must NOT
            // delete+recreate it here: while a foreground service holds the
            // channel, deleteNotificationChannel throws
            //   SecurityException: Not allowed to delete channel dns_filter
            //     with a foreground service
            // which crashed the whole process (and took the accessibility
            // service down with it). Just ensure it exists.
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "DNS Filter",
                    NotificationManager.IMPORTANCE_MIN).apply {
                    setShowBadge(false)
                    description = "Keeps the family DNS lock active."
                })
        }
        val n: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FocusGuard")
            .setContentText("Family DNS is locked")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
        try {
            startForeground(NOTIF_ID, n)
        } catch (e: Exception) {
            // Should never happen — we have a notification channel — but if it
            // does, fall back to a regular service so we at least stay alive.
            Log.w(TAG, "startForeground failed: ${e.message}")
        }
    }
}
