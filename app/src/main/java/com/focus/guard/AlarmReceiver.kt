package com.focus.guard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Fired by AlarmManager at the alarm time. Launches the full-screen ring screen
 * (via a full-screen-intent notification, which reliably starts an Activity even
 * from the background / lock screen), then reschedules the same alarm for the
 * next day so it repeats daily.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, -1)
        if (id < 0) return

        AlarmStore.markFired(context, id)

        val ch = "alarm_fire"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(ch, "Alarm", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val ring = Intent(context, AlarmRingActivity::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ID, id)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val fsi = PendingIntent.getActivity(
            context, 7000 + id, ring,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val n = NotificationCompat.Builder(context, ch)
            .setContentTitle("⏰ Alarm")
            .setContentText("Walk 100 steps, then solve 5 maths to turn it off")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fsi, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
        nm.notify(7000 + id, n)

        // Also try a direct launch (we hold overlay permission → background-launch OK).
        try { context.startActivity(ring) } catch (_: Exception) {}

        // Reschedule for next day so the alarm repeats daily.
        AlarmStore.get(context, id)?.let { AlarmScheduler.schedule(context, it) }
    }
}
