package com.focus.guard

import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.focus.guard.Ui.Radius
import com.focus.guard.Ui.Space
import com.focus.guard.Ui.dp
import com.focus.guard.Ui.dpf
import com.focus.guard.Ui.icon
import com.focus.guard.Ui.lp
import com.focus.guard.Ui.rounded
import com.focus.guard.Ui.row
import com.focus.guard.Ui.scaffold
import com.focus.guard.Ui.switch
import com.focus.guard.Ui.text

/**
 * App blocklist picker. Blocking is PERMANENT by design — an already-blocked app
 * shows a locked toggle; a new block flips on then locks.
 *
 * Performance: the installed-app scan (+ icon loads) runs OFF the main thread and
 * results are posted back, so the screen opens instantly with a loading state
 * instead of janking while PackageManager is queried.
 */
class AppPickerActivity : ComponentActivity() {

    private data class Entry(val label: String, val pkg: String, val icon: Drawable?)

    private lateinit var listColumn: LinearLayout
    private lateinit var countLabel: TextView
    private lateinit var searchField: EditText
    private var allApps: List<Entry> = emptyList()
    private var loaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(scaffold(
            title = "Block apps",
            subtitle = "Tap a toggle to permanently block an app",
            onBack = { finish() }
        ) { content ->
            countLabel = text("Loading apps…", 13f, Ui.TEXT_DIM)
            content.addView(countLabel, lp(topMargin = dp(Space.xs)))

            searchField = EditText(this).apply {
                hint = "Search apps…"
                setHintTextColor(Ui.TEXT_MUTE)
                setTextColor(Ui.TEXT)
                textSize = 15f
                inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                background = rounded(Ui.SURFACE, dpf(Radius.md), Ui.STROKE, dp(1))
                setPadding(dp(16), dp(14), dp(16), dp(14))
                isEnabled = false
            }
            content.addView(searchField, lp(topMargin = dp(Space.lg)))

            listColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            content.addView(listColumn, lp(topMargin = dp(Space.md)))

            listColumn.addView(loadingRow())
        })

        searchField.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (loaded) renderList(s?.toString()?.trim()?.lowercase() ?: "")
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        loadAppsAsync()
    }

    private fun loadingRow(): View = text("Scanning installed apps…", 14f, Ui.TEXT_MUTE).apply {
        setPadding(dp(Space.xs), dp(Space.xl), 0, 0)
    }

    private fun loadAppsAsync() {
        Thread {
            val pm = packageManager
            val mine = packageName
            val result = try {
                pm.getInstalledApplications(0).asSequence()
                    .filter { it.packageName != mine }
                    .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                    .map { Entry(label(it, pm), it.packageName, iconOf(it, pm)) }
                    .sortedBy { it.label.lowercase() }
                    .toList()
            } catch (_: Exception) { emptyList() }
            runOnUiThread {
                allApps = result
                loaded = true
                searchField.isEnabled = true
                renderList("")
            }
        }.also { it.isDaemon = true }.start()
    }

    private fun label(info: ApplicationInfo, pm: android.content.pm.PackageManager): String =
        try { pm.getApplicationLabel(info).toString() } catch (_: Exception) { info.packageName }

    private fun iconOf(info: ApplicationInfo, pm: android.content.pm.PackageManager): Drawable? =
        try { pm.getApplicationIcon(info) } catch (_: Exception) { null }

    private fun renderList(query: String) {
        listColumn.removeAllViews()
        val blocked = Blocklist.userBlocked(this)
        countLabel.text = "${blocked.size} blocked · ${allApps.size} apps installed"

        val shown = allApps.filter {
            query.isEmpty() || query in it.label.lowercase() || query in it.pkg.lowercase()
        }
        if (shown.isEmpty()) {
            listColumn.addView(emptyState(
                if (query.isEmpty()) "No launchable apps found."
                else "No apps match “$query”."
            ))
            return
        }
        for (entry in shown) listColumn.addView(appRow(entry, entry.pkg in blocked), lp(topMargin = dp(Space.sm)))
    }

    private fun appRow(entry: Entry, blocked: Boolean): LinearLayout {
        val rowV = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Ui.SURFACE, dpf(Radius.md), Ui.STROKE, dp(1))
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        val iconWrap = LinearLayout(this).apply {
            background = rounded(Ui.SURFACE_HI, dpf(Radius.sm))
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        iconWrap.addView(ImageView(this).apply {
            setImageDrawable(entry.icon)
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(30))
        })
        rowV.addView(iconWrap)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = row(0, 1f)
            setPadding(dp(14), 0, dp(10), 0)
        }
        col.addView(text(entry.label, 15.5f, Ui.TEXT, bold = true).apply { maxLines = 1 })
        col.addView(text(if (blocked) "Blocked · permanent" else entry.pkg,
            11.5f, if (blocked) Ui.GREEN else Ui.TEXT_MUTE).apply { maxLines = 1 })
        rowV.addView(col)

        // Blocking is PERMANENT. Already-blocked → locked on. New block → on, then lock.
        val toggle = switch(blocked, enabled = !blocked)
        toggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Blocklist.setBlocked(this, entry.pkg, true)
                GuardAccessibilityService.blockedPackages.add(entry.pkg)
                toggle.isEnabled = false   // lock it on — no unblock
                (col.getChildAt(1) as TextView).apply {
                    text = "Blocked · permanent"; setTextColor(Ui.GREEN)
                }
                updateCount()
            }
        }
        rowV.addView(toggle)
        return rowV
    }

    private fun updateCount() {
        val n = Blocklist.userBlocked(this).size
        countLabel.text = "$n blocked · ${allApps.size} apps installed"
    }

    private fun emptyState(msg: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        background = rounded(Ui.SURFACE, dpf(Radius.lg), Ui.STROKE, dp(1))
        setPadding(dp(Space.xl), dp(Space.xxl), dp(Space.xl), dp(Space.xxl))
        addView(icon(R.drawable.ic_search, Ui.TEXT_MUTE, 28))
        addView(text(msg, 14f, Ui.TEXT_DIM).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(Space.md), 0, 0)
        })
    }
}
