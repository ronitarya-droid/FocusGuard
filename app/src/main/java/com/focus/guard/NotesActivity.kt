package com.focus.guard

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import com.focus.guard.Ui.dp
import com.focus.guard.Ui.gradient
import com.focus.guard.Ui.lp
import com.focus.guard.Ui.rounded
import com.focus.guard.Ui.text

/**
 * Password-protected notepad. Opens on a PIN lock (4377). After unlock it shows
 * a simple notes list with add / edit / delete. Notes persist via [NotesStore].
 */
class NotesActivity : ComponentActivity() {

    private val PIN = "4377"
    private var unlocked = false
    private lateinit var rootScroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rootScroll = ScrollView(this).apply { setBackgroundColor(Ui.BG); isFillViewport = true }
        setContentView(rootScroll)
        showLock()
    }

    // ---- lock screen ----
    private fun showLock() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Ui.BG)
            setPadding(dp(28), dp(80), dp(28), dp(28))
        }
        col.addView(text("🔒", 48f, Ui.TEXT).apply { gravity = Gravity.CENTER })
        col.addView(text("Private Notes", 24f, Ui.TEXT, bold = true).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(12), 0, 0)
        })
        col.addView(text("Enter your PIN to unlock", 14f, Ui.TEXT_DIM).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(6), 0, dp(28))
        })

        val pin = EditText(this).apply {
            hint = "••••"
            gravity = Gravity.CENTER
            textSize = 26f
            setTextColor(Ui.TEXT); setHintTextColor(Ui.TEXT_MUTE)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            letterSpacing = 0.4f
            background = rounded(Ui.SURFACE, dp(14).toFloat(), Ui.STROKE, dp(1))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        col.addView(pin, lp(width = dp(220)))

        val btn = text("Unlock", 16f, Ui.TEXT, bold = true).apply {
            gravity = Gravity.CENTER
            background = gradient(Ui.ACCENT, Ui.ACCENT_2, dp(16).toFloat())
            setPadding(dp(40), dp(15), dp(40), dp(15))
            isClickable = true
            setOnClickListener {
                if (pin.text.toString() == PIN) { unlocked = true; showNotes() }
                else { pin.error = "Wrong PIN"; pin.text.clear() }
            }
        }
        col.addView(btn, lp(width = ViewGroup.LayoutParams.WRAP_CONTENT, topMargin = dp(20)))

        rootScroll.removeAllViews(); rootScroll.addView(col)
    }

    // ---- notes list ----
    private fun showNotes() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(dp(20), dp(28), dp(20), dp(32))
        }
        col.addView(text("Private Notes", 24f, Ui.TEXT, bold = true))

        col.addView(text("➕  New note", 15.5f, Ui.TEXT, bold = true).apply {
            gravity = Gravity.CENTER
            background = gradient(Ui.ACCENT, Ui.ACCENT_2, dp(16).toFloat())
            setPadding(dp(18), dp(15), dp(18), dp(15))
            isClickable = true
            setOnClickListener { editDialog(null) }
        }, lp(topMargin = dp(16)))

        val notes = NotesStore.all(this)
        if (notes.isEmpty()) {
            col.addView(text("No notes yet. Tap “New note” to start.", 14f, Ui.TEXT_MUTE)
                .apply { setPadding(dp(2), dp(24), 0, 0) })
        } else {
            for (i in notes.indices) col.addView(noteCard(i, notes[i]), lp(topMargin = dp(12)))
        }

        rootScroll.removeAllViews(); rootScroll.addView(col)
    }

    private fun noteCard(index: Int, content: String): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Ui.SURFACE, dp(16).toFloat(), Ui.STROKE, dp(1))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isClickable = true
            setOnClickListener { editDialog(index) }
        }
        val title = content.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() } ?: "(untitled)"
        val preview = content.lineSequence().drop(1).joinToString(" ").take(80)
        card.addView(text(title, 16f, Ui.TEXT, bold = true).apply { maxLines = 1 })
        if (preview.isNotBlank()) card.addView(text(preview, 13.5f, Ui.TEXT_DIM).apply {
            maxLines = 2; setPadding(0, dp(4), 0, 0)
        })
        return card
    }

    private fun editDialog(index: Int?) {
        val current = if (index != null) NotesStore.all(this).getOrNull(index) ?: "" else ""
        val input = EditText(this).apply {
            setText(current)
            setTextColor(Ui.TEXT); setHintTextColor(Ui.TEXT_MUTE)
            hint = "Write your note…"
            textSize = 16f
            gravity = Gravity.TOP or Gravity.START
            minLines = 6
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val b = AlertDialog.Builder(this)
            .setTitle(if (index == null) "New note" else "Edit note")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val t = input.text.toString().trim()
                if (t.isNotEmpty()) {
                    if (index == null) NotesStore.add(this, t) else NotesStore.update(this, index, t)
                }
                showNotes()
            }
            .setNegativeButton("Cancel", null)
        if (index != null) b.setNeutralButton("Delete") { _, _ ->
            NotesStore.delete(this, index); showNotes()
        }
        b.show()
    }
}
