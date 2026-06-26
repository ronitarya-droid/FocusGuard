package com.focus.guard

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import com.focus.guard.Ui.Radius
import com.focus.guard.Ui.Space
import com.focus.guard.Ui.dp
import com.focus.guard.Ui.dpf
import com.focus.guard.Ui.gradient
import com.focus.guard.Ui.icon
import com.focus.guard.Ui.iconChip
import com.focus.guard.Ui.lp
import com.focus.guard.Ui.pressable
import com.focus.guard.Ui.rounded
import com.focus.guard.Ui.row
import com.focus.guard.Ui.scaffold
import com.focus.guard.Ui.text
import com.focus.guard.Ui.tintWash
import com.focus.guard.Ui.title

/**
 * User-managed website + keyword blocklists. Adding is instant and PERMANENT —
 * there is no remove/unblock, by design. Entries persist via [Blocklist] and feed
 * the live accessibility scanner immediately. The whole screen is PIN-gated.
 */
class WebsiteBlockActivity : ComponentActivity() {

    private lateinit var siteList: LinearLayout
    private lateinit var wordList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GuardDeviceAdminReceiver.applyAllProtections(this)
        setContentView(PinGate.lockView(this, "Block sites & words") { buildUi() })
    }

    private fun buildUi() {
        setContentView(scaffold(
            title = "Block sites & words",
            subtitle = "Adding is permanent — entries can't be removed",
            onBack = { finish() }
        ) { content ->
            // WEBSITES
            content.addView(sectionHeader(R.drawable.ic_globe, "WEBSITES",
                "Any page whose address or on-screen text contains this is blocked."),
                lp(topMargin = dp(Space.sm)))
            content.addView(inputRow("e.g. example.com", InputType.TYPE_TEXT_VARIATION_URI) { raw ->
                Blocklist.addDomain(this, raw); renderSites()
            }, lp(topMargin = dp(Space.md)))
            siteList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            content.addView(siteList, lp(topMargin = dp(Space.sm)))

            // KEYWORDS
            content.addView(sectionHeader(R.drawable.ic_search, "KEYWORDS",
                "If this word shows up on screen in any app or browser, you're bounced."),
                lp(topMargin = dp(Space.xxl)))
            content.addView(inputRow("e.g. a trigger word", InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) { raw ->
                Blocklist.addKeyword(this, raw); renderWords()
            }, lp(topMargin = dp(Space.md)))
            wordList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            content.addView(wordList, lp(topMargin = dp(Space.sm)))

            renderSites()
            renderWords()
        })
    }

    private fun sectionHeader(iconRes: Int, label: String, desc: String): LinearLayout {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(icon(iconRes, Ui.TEXT_DIM, 16).apply {
            (layoutParams as LinearLayout.LayoutParams).rightMargin = dp(8)
        })
        head.addView(title(label))
        col.addView(head)
        col.addView(text(desc, 12.5f, Ui.TEXT_MUTE).apply { setPadding(0, dp(6), 0, 0) })
        return col
    }

    private fun inputRow(hint: String, extraInput: Int, onAdd: (String) -> Unit): LinearLayout {
        val rowV = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val input = EditText(this).apply {
            this.hint = hint
            setHintTextColor(Ui.TEXT_MUTE); setTextColor(Ui.TEXT); textSize = 15f
            inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or extraInput
            background = rounded(Ui.SURFACE, dpf(Radius.md), Ui.STROKE, dp(1))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = row(0, 1f)
        }
        val add = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = pressable(gradient(Ui.ACCENT, Ui.ACCENT_2, dpf(Radius.md)))
            setPadding(dp(16), dp(13), dp(16), dp(13))
            isClickable = true
            addView(icon(R.drawable.ic_plus, Ui.TEXT, 20))
            setOnClickListener {
                val v = input.text.toString().trim()
                if (v.isNotEmpty()) { onAdd(v); input.setText("") }
            }
        }
        rowV.addView(input)
        rowV.addView(add, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            leftMargin = dp(Space.sm)
        })
        return rowV
    }

    private fun renderSites() {
        siteList.removeAllViews()
        val items = Blocklist.userDomains(this).sorted()
        if (items.isEmpty()) {
            siteList.addView(emptyHint("No custom sites yet. Built-in adult/social sites are already blocked."))
            return
        }
        for (d in items) siteList.addView(chipRow(R.drawable.ic_globe, d), lp(topMargin = dp(Space.sm)))
    }

    private fun renderWords() {
        wordList.removeAllViews()
        val items = Blocklist.userKeywords(this).sorted()
        if (items.isEmpty()) {
            wordList.addView(emptyHint("No custom keywords yet."))
            return
        }
        for (k in items) wordList.addView(chipRow(R.drawable.ic_search, k), lp(topMargin = dp(Space.sm)))
    }

    /** A blocked entry. PERMANENT — there is no remove/unblock action, by design. */
    private fun chipRow(iconRes: Int, label: String): LinearLayout {
        val rowV = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Ui.SURFACE, dpf(Radius.md), Ui.STROKE, dp(1))
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        rowV.addView(iconChip(iconRes, Ui.TEXT_DIM, tintWash(Ui.TEXT_DIM), 32, Radius.sm))
        rowV.addView(text(label, 15f, Ui.TEXT, bold = true).apply {
            setPadding(dp(12), 0, dp(10), 0)
            layoutParams = row(0, 1f)
            maxLines = 1
        })
        val badge = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = rounded(Ui.GREEN_SOFT, dpf(Radius.pill))
            setPadding(dp(10), dp(6), dp(11), dp(6))
        }
        badge.addView(icon(R.drawable.ic_lock, Ui.GREEN, 13).apply {
            (layoutParams as LinearLayout.LayoutParams).rightMargin = dp(5)
        })
        badge.addView(text("Blocked", 12f, Ui.GREEN, bold = true))
        rowV.addView(badge)
        return rowV
    }

    private fun emptyHint(t: String): View = text(t, 13f, Ui.TEXT_MUTE).apply {
        background = rounded(Ui.SURFACE_LO, dpf(Radius.md))
        setPadding(dp(14), dp(14), dp(14), dp(14))
    }
}
