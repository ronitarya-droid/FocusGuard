package com.focus.guard

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import com.focus.guard.Ui.Radius
import com.focus.guard.Ui.Space
import com.focus.guard.Ui.dp
import com.focus.guard.Ui.dpf
import com.focus.guard.Ui.icon
import com.focus.guard.Ui.lp
import com.focus.guard.Ui.primaryButton
import com.focus.guard.Ui.rounded
import com.focus.guard.Ui.scaffold
import com.focus.guard.Ui.text

/**
 * Password-protected notepad. Opens on the shared [PinGate] lock; after unlock it
 * shows a notes list with add / edit / delete. Notes persist via [NotesStore].
 */
class NotesActivity : ComponentActivity() {

    private var unlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(PinGate.lockView(this, "Private notes") {
            unlocked = true
            showNotes()
        })
    }

    private fun showNotes() {
        setContentView(scaffold(
            title = "Private notes",
            subtitle = "Only you can read these",
            onBack = { finish() }
        ) { content ->
            content.addView(primaryButton("New note", R.drawable.ic_plus) { editDialog(null) },
                lp(topMargin = dp(Space.sm)))

            val notes = NotesStore.all(this)
            if (notes.isEmpty()) {
                content.addView(emptyState(), lp(topMargin = dp(Space.xl)))
            } else {
                for (i in notes.indices) {
                    content.addView(noteCard(i, notes[i]), lp(topMargin = dp(Space.md)))
                }
            }
        })
    }

    private fun noteCard(index: Int, content: String): LinearLayout {
        val cardV = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Ui.SURFACE, dpf(Radius.md), Ui.STROKE, dp(1))
            setPadding(dp(16), dp(15), dp(16), dp(15))
            isClickable = true
            setOnClickListener { editDialog(index) }
            elevation = dpf(3f)
        }
        val title = content.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() } ?: "(untitled)"
        val preview = content.lineSequence().drop(1).joinToString(" ").take(100)
        cardV.addView(text(title, 16f, Ui.TEXT, bold = true).apply { maxLines = 1 })
        if (preview.isNotBlank()) cardV.addView(text(preview, 13.5f, Ui.TEXT_DIM).apply {
            maxLines = 2; setPadding(0, dp(Space.xs), 0, 0)
        })
        return cardV
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
            setPadding(dp(20), dp(16), dp(20), dp(16))
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

    private fun emptyState(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        background = rounded(Ui.SURFACE, dpf(Radius.lg), Ui.STROKE, dp(1))
        setPadding(dp(Space.xl), dp(Space.xxl), dp(Space.xl), dp(Space.xxl))
        addView(icon(R.drawable.ic_lock, Ui.TEXT_MUTE, 30))
        addView(text("No notes yet", 16f, Ui.TEXT, bold = true).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(Space.md), 0, 0)
        })
        addView(text("Tap “New note” to write your first one.", 13.5f, Ui.TEXT_MUTE).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(Space.xs), 0, 0)
        })
    }
}
