package com.focus.guard

import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.focus.guard.Ui.dp
import com.focus.guard.Ui.gradient
import com.focus.guard.Ui.lp
import com.focus.guard.Ui.rounded
import com.focus.guard.Ui.text
import com.focus.guard.Ui.title

/**
 * Feature: user-managed website + keyword blocklists. Adding is instant and
 * PERMANENT — there is no remove/unblock, by design. Entries persist via
 * [Blocklist] and feed the live accessibility scanner immediately.
 */
class WebsiteBlockActivity : ComponentActivity() {

    private lateinit var siteList: LinearLayout
    private lateinit var wordList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GuardDeviceAdminReceiver.applyAllProtections(this)
        // PIN-gate the whole screen (same PIN as Notes).
        setContentView(PinGate.lockView(this, "Block sites & words") { buildUi() })
    }

    private fun buildUi() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(dp(20), dp(28), dp(20), dp(32))
        }

        content.addView(text("Block sites & words", 24f, Ui.TEXT, bold = true).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        })
        content.addView(text("Adding is permanent — blocked entries can never be removed.", 13.5f, Ui.TEXT_DIM),
            lp(topMargin = dp(4)))

        // ----- WEBSITES -----
        content.addView(title("WEBSITES"), lp(topMargin = dp(26)))
        content.addView(text("Any page whose address or on-screen text contains this is blocked.",
            12.5f, Ui.TEXT_MUTE), lp(topMargin = dp(6)))
        content.addView(inputRow("e.g. example.com", "Block") { raw ->
            Blocklist.addDomain(this, raw); renderSites()
        }, lp(topMargin = dp(12)))
        siteList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(siteList, lp(topMargin = dp(8)))

        // ----- KEYWORDS -----
        content.addView(title("KEYWORDS"), lp(topMargin = dp(28)))
        content.addView(text("If this word shows up on screen in any app or browser, you're bounced.",
            12.5f, Ui.TEXT_MUTE), lp(topMargin = dp(6)))
        content.addView(inputRow("e.g. a trigger word", "Block") { raw ->
            Blocklist.addKeyword(this, raw); renderWords()
        }, lp(topMargin = dp(12)))
        wordList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(wordList, lp(topMargin = dp(8)))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Ui.BG); isFillViewport = true; addView(content)
        })

        renderSites()
        renderWords()
    }

    private fun inputRow(hint: String, action: String, onAdd: (String) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val input = EditText(this).apply {
            this.hint = hint
            setHintTextColor(Ui.TEXT_MUTE); setTextColor(Ui.TEXT); textSize = 15f
            inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_URI
            background = rounded(Ui.SURFACE, dp(14).toFloat(), Ui.STROKE, dp(1))
            setPadding(dp(14), dp(13), dp(14), dp(13))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val add = text(action, 14.5f, Ui.TEXT, bold = true).apply {
            background = gradient(Ui.ACCENT, Ui.ACCENT_2, dp(14).toFloat())
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(14), dp(18), dp(14))
            isClickable = true
            setOnClickListener {
                val v = input.text.toString().trim()
                if (v.isNotEmpty()) { onAdd(v); input.setText("") }
            }
        }
        row.addView(input)
        row.addView(add, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { leftMargin = dp(10) })
        return row
    }

    private fun renderSites() {
        siteList.removeAllViews()
        val items = Blocklist.userDomains(this).sorted()
        if (items.isEmpty()) {
            siteList.addView(emptyHint("No custom sites yet. Built-in adult/social sites are already blocked."))
            return
        }
        for (d in items) siteList.addView(chipRow("🌐", d), lp(topMargin = dp(8)))
    }

    private fun renderWords() {
        wordList.removeAllViews()
        val items = Blocklist.userKeywords(this).sorted()
        if (items.isEmpty()) {
            wordList.addView(emptyHint("No custom keywords yet."))
            return
        }
        for (k in items) wordList.addView(chipRow("🔤", k), lp(topMargin = dp(8)))
    }

    /** A blocked entry. PERMANENT — there is no remove/unblock action, by design.
     *  Once a site or keyword is added it can never be removed from the app. */
    private fun chipRow(glyph: String, label: String): LinearLayout {
        val badge = text("🔒 Blocked", 12.5f, Ui.GREEN, bold = true).apply {
            background = rounded(0x2234D399, dp(10).toFloat())
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Ui.SURFACE, dp(14).toFloat(), Ui.STROKE, dp(1))
            setPadding(dp(14), dp(12), dp(12), dp(12))
            // NOT clickable — permanent, no unblock.
        }
        row.addView(text(glyph, 16f, Ui.TEXT))
        row.addView(text(label, 15f, Ui.TEXT, bold = true).apply {
            setPadding(dp(12), 0, dp(10), 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            maxLines = 1
        })
        row.addView(badge)
        return row
    }

    private fun emptyHint(t: String) = text(t, 13f, Ui.TEXT_MUTE).apply {
        setPadding(dp(4), dp(10), dp(4), 0)
    }

    private fun toast(m: String) =
        android.widget.Toast.makeText(this, m, android.widget.Toast.LENGTH_SHORT).show()
}