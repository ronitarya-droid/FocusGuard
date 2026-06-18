package com.focus.guard

import android.content.pm.ApplicationInfo
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.focus.guard.Ui.dp
import com.focus.guard.Ui.lp
import com.focus.guard.Ui.rounded
import com.focus.guard.Ui.text

class AppPickerActivity : ComponentActivity() {

    private data class Entry(val label: String, val pkg: String, val icon: Drawable?)

    private lateinit var listColumn: LinearLayout
    private lateinit var countLabel: TextView
    private var allApps: List<Entry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(dp(20), dp(28), dp(20), dp(28))
        }

        // header
        content.addView(text("Block apps", 24f, Ui.TEXT, bold = true).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        })
        countLabel = text("", 13.5f, Ui.TEXT_DIM)
        content.addView(countLabel, lp(topMargin = dp(4)))

        // search field
        val search = EditText(this).apply {
            hint = "Search apps…"
            setHintTextColor(Ui.TEXT_MUTE)
            setTextColor(Ui.TEXT)
            textSize = 15f
            background = rounded(Ui.SURFACE, dp(14).toFloat(), Ui.STROKE, dp(1))
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        content.addView(search, lp(topMargin = dp(18)))

        listColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(listColumn, lp(topMargin = dp(14)))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Ui.BG)
            isFillViewport = true
            addView(content)
        })

        loadApps()
        renderList("")

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) =
                renderList(s?.toString()?.trim()?.lowercase() ?: "")
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }

    private fun loadApps() {
        val pm = packageManager
        val mine = packageName
        allApps = pm.getInstalledApplications(0).asSequence()
            .filter { it.packageName != mine }
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { Entry(label(it, pm), it.packageName, iconOf(it, pm)) }
            .sortedBy { it.label.lowercase() }
            .toList()
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
            listColumn.addView(text("No apps match “$query”.", 14f, Ui.TEXT_MUTE).apply {
                setPadding(dp(4), dp(20), 0, 0)
            })
            return
        }
        for (entry in shown) listColumn.addView(appRow(entry, entry.pkg in blocked), lp(topMargin = dp(10)))
    }

    private fun appRow(entry: Entry, blocked: Boolean): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Ui.SURFACE, dp(16).toFloat(), Ui.STROKE, dp(1))
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        val icon = ImageView(this).apply {
            setImageDrawable(entry.icon)
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(14), 0, dp(10), 0)
        }
        col.addView(text(entry.label, 15.5f, Ui.TEXT, bold = true).apply { maxLines = 1 })
        col.addView(text(entry.pkg, 11.5f, Ui.TEXT_MUTE).apply { maxLines = 1 })

        // Blocking is PERMANENT. An already-blocked app shows a locked switch
        // (on + disabled). A new block flips on, then locks — there is no way to
        // turn it off again from the UI, by design.
        val toggle = Switch(this)
        toggle.isChecked = blocked
        toggle.isEnabled = !blocked
        toggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Blocklist.setBlocked(this, entry.pkg, true)
                GuardAccessibilityService.blockedPackages.add(entry.pkg)
                toggle.isEnabled = false   // lock it on — no unblock
                updateCount()
            }
        }

        row.addView(icon); row.addView(col); row.addView(toggle)
        return row
    }

    private fun updateCount() {
        val n = Blocklist.userBlocked(this).size
        countLabel.text = "$n blocked · ${allApps.size} apps installed"
    }
}
