package com.focus.guard

import android.content.Context

/**
 * Tiny persistent notepad store (SharedPreferences only). Notes are kept as an
 * ordered list of strings; the first line doubles as the title in the list.
 */
object NotesStore {
    private const val PREFS = "focusguard_notes"
    private const val KEY = "notes_list"
    private const val SEP = "␟"   // unit-separator, won't appear in typed text

    fun all(context: Context): MutableList<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        return if (raw.isEmpty()) mutableListOf() else raw.split(SEP).toMutableList()
    }

    private fun save(context: Context, list: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, list.joinToString(SEP)).apply()
    }

    fun add(context: Context, text: String) {
        val list = all(context); list.add(0, text); save(context, list)
    }

    fun update(context: Context, index: Int, text: String) {
        val list = all(context)
        if (index in list.indices) { list[index] = text; save(context, list) }
    }

    fun delete(context: Context, index: Int) {
        val list = all(context)
        if (index in list.indices) { list.removeAt(index); save(context, list) }
    }
}
