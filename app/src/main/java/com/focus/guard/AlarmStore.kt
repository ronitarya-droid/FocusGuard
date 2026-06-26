package com.focus.guard

import android.content.Context

/** One alarm. [uri] is a persisted content URI of a chosen mp3, or null = default. */
data class FgAlarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val uri: String?,
    val enabled: Boolean
)

/** Persistent list of alarms (SharedPreferences). */
object AlarmStore {
    private const val PREFS = "focusguard_alarms"
    private const val KEY = "alarms"
    private const val ROW = "\n"
    private const val SEP = "‖"

    fun all(context: Context): List<FgAlarm> {
        val raw = prefs(context).getString(KEY, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(ROW).mapNotNull { line ->
            val p = line.split(SEP)
            if (p.size < 5) return@mapNotNull null
            val id = p[0].toIntOrNull() ?: return@mapNotNull null
            FgAlarm(
                id = id,
                hour = p[1].toIntOrNull() ?: 7,
                minute = p[2].toIntOrNull() ?: 0,
                enabled = p[3] == "1",
                uri = p[4].ifEmpty { null }
            )
        }
    }

    fun get(context: Context, id: Int): FgAlarm? = all(context).firstOrNull { it.id == id }

    /** Records the wall-clock time an alarm actually fired, so BootReceiver can
     *  tell whether the phone was powered off when it should have rung. */
    fun markFired(context: Context, id: Int) {
        prefs(context).edit().putLong("fired_$id", System.currentTimeMillis()).apply()
    }

    /** Last wall-clock time the given alarm fired, or 0 if never. */
    fun lastFired(context: Context, id: Int): Long =
        prefs(context).getLong("fired_$id", 0L)

    fun put(context: Context, alarm: FgAlarm) {
        val list = all(context).filter { it.id != alarm.id } + alarm
        save(context, list)
    }

    fun delete(context: Context, id: Int) = save(context, all(context).filter { it.id != id })

    fun nextId(context: Context): Int = (all(context).maxOfOrNull { it.id } ?: 0) + 1

    private fun save(context: Context, list: List<FgAlarm>) {
        val raw = list.joinToString(ROW) {
            "${it.id}$SEP${it.hour}$SEP${it.minute}$SEP${if (it.enabled) "1" else "0"}$SEP${it.uri ?: ""}"
        }
        prefs(context).edit().putString(KEY, raw).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
