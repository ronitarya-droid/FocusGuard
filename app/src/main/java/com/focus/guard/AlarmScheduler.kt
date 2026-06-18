package com.focus.guard

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/** Schedules daily alarms via AlarmManager.setAlarmClock (exact, doze-exempt). */
object AlarmScheduler {
    const val EXTRA_ID = "alarm_id"

    private fun am(context: Context) =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pending(context: Context, id: Int): PendingIntent {
        val i = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.focus.guard.ALARM_FIRE"
            putExtra(EXTRA_ID, id)
        }
        return PendingIntent.getBroadcast(
            context, id, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Next occurrence of hour:minute (today if still ahead, else tomorrow). */
    private fun nextTrigger(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= now.timeInMillis) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun schedule(context: Context, alarm: FgAlarm) {
        if (!alarm.enabled) { cancel(context, alarm.id); return }
        val trigger = nextTrigger(alarm.hour, alarm.minute)
        val show = PendingIntent.getActivity(
            context, 9000 + alarm.id,
            Intent(context, AlarmListActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            am(context).setAlarmClock(
                AlarmManager.AlarmClockInfo(trigger, show),
                pending(context, alarm.id)
            )
        } catch (_: Exception) {}
    }

    fun cancel(context: Context, id: Int) {
        try { am(context).cancel(pending(context, id)) } catch (_: Exception) {}
    }

    fun rescheduleAll(context: Context) {
        for (a in AlarmStore.all(context)) schedule(context, a)
    }
}
