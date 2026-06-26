package com.focus.guard

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.focus.guard.Ui.Radius
import com.focus.guard.Ui.Space
import com.focus.guard.Ui.dp
import com.focus.guard.Ui.dpf
import com.focus.guard.Ui.icon
import com.focus.guard.Ui.iconChip
import com.focus.guard.Ui.lp
import com.focus.guard.Ui.primaryButton
import com.focus.guard.Ui.rounded
import com.focus.guard.Ui.row
import com.focus.guard.Ui.scaffold
import com.focus.guard.Ui.stat
import com.focus.guard.Ui.switch
import com.focus.guard.Ui.text
import com.focus.guard.Ui.tintWash
import java.util.Calendar

/** Manage alarms: add (time + custom mp3), toggle, delete. Dismiss = solve maths. */
class AlarmListActivity : ComponentActivity() {

    private lateinit var listCol: LinearLayout
    private var pickForId = -1

    private val pickSound = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && pickForId >= 0) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            AlarmStore.get(this, pickForId)?.let {
                AlarmStore.put(this, it.copy(uri = uri.toString()))
            }
            render()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(scaffold(
            title = "Alarms",
            subtitle = "Walk 100 steps, then solve 5 maths",
            onBack = { finish() }
        ) { content ->
            content.addView(primaryButton("Add alarm", R.drawable.ic_plus) { addAlarm() },
                lp(topMargin = dp(Space.sm)))
            content.addView(text("When it rings: tap Start, walk 100 steps (stop for 10s and it " +
                "rings again), then solve 5 maths — it stays quiet while you solve, standing still.",
                12.5f, Ui.TEXT_MUTE).apply { setPadding(dp(Space.xs), dp(Space.md), 0, 0) })
            listCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            content.addView(listCol, lp(topMargin = dp(Space.md)))
        })
        render()
    }

    private fun addAlarm() {
        val now = Calendar.getInstance()
        TimePickerDialog(this, { _, h, m ->
            val a = FgAlarm(AlarmStore.nextId(this), h, m, null, true)
            AlarmStore.put(this, a)
            AlarmScheduler.schedule(this, a)
            render()
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show()
    }

    private fun render() {
        listCol.removeAllViews()
        val alarms = AlarmStore.all(this).sortedWith(compareBy({ it.hour }, { it.minute }))
        if (alarms.isEmpty()) {
            listCol.addView(emptyState())
            return
        }
        for (a in alarms) listCol.addView(row(a), lp(topMargin = dp(Space.md)))
    }

    private fun row(a: FgAlarm): LinearLayout {
        val cardV = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Ui.SURFACE, dpf(Radius.lg), Ui.STROKE, dp(1))
            setPadding(dp(18), dp(16), dp(18), dp(16))
            elevation = dpf(3f)
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(iconChip(R.drawable.ic_alarm, Ui.AMBER, tintWash(Ui.AMBER), 40, Radius.md).also {
            (it.layoutParams as LinearLayout.LayoutParams).rightMargin = dp(Space.md)
        })
        val timeStr = String.format("%02d:%02d", a.hour, a.minute)
        top.addView(stat(timeStr, 30f, Ui.TEXT).apply {
            layoutParams = row(0, 1f, Gravity.CENTER_VERTICAL)
        })
        top.addView(switch(a.enabled) { on ->
            val u = a.copy(enabled = on)
            AlarmStore.put(this@AlarmListActivity, u)
            if (on) AlarmScheduler.schedule(this@AlarmListActivity, u)
            else AlarmScheduler.cancel(this@AlarmListActivity, a.id)
        })
        cardV.addView(top)

        val soundName = a.uri?.let { displayName(Uri.parse(it)) } ?: "Default alarm tone"
        val soundRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(Space.md), 0, 0)
        }
        soundRow.addView(icon(R.drawable.ic_music, Ui.TEXT_MUTE, 15).apply {
            (layoutParams as LinearLayout.LayoutParams).rightMargin = dp(7)
        })
        soundRow.addView(text(soundName, 13f, Ui.TEXT_DIM).apply { maxLines = 1 })
        cardV.addView(soundRow)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(Space.md), 0, 0)
        }
        actions.addView(actionChip(R.drawable.ic_music, "Choose mp3", Ui.TEXT) {
            pickForId = a.id
            pickSound.launch(arrayOf("audio/*"))
        })
        actions.addView(actionChip(R.drawable.ic_trash, "Delete", Ui.RED, dp(Space.sm)) {
            AlarmScheduler.cancel(this@AlarmListActivity, a.id)
            AlarmStore.delete(this@AlarmListActivity, a.id)
            render()
        })
        cardV.addView(actions)
        return cardV
    }

    private fun actionChip(iconRes: Int, label: String, color: Int, leftMargin: Int = 0,
                           onClick: () -> Unit): LinearLayout {
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = Ui.pressable(rounded(
                if (color == Ui.RED) Ui.RED_SOFT else Ui.SURFACE_HI, dpf(Radius.sm)))
            setPadding(dp(14), dp(9), dp(14), dp(9))
            isClickable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                this.leftMargin = leftMargin
            }
        }
        v.addView(icon(iconRes, color, 16).apply {
            (layoutParams as LinearLayout.LayoutParams).rightMargin = dp(7)
        })
        v.addView(text(label, 13.5f, color, bold = true))
        return v
    }

    private fun displayName(uri: Uri): String {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else uri.lastPathSegment ?: "Custom"
            } ?: (uri.lastPathSegment ?: "Custom")
        } catch (_: Exception) { "Custom sound" }
    }

    private fun emptyState(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        background = rounded(Ui.SURFACE, dpf(Radius.lg), Ui.STROKE, dp(1))
        setPadding(dp(Space.xl), dp(Space.xxl), dp(Space.xl), dp(Space.xxl))
        addView(icon(R.drawable.ic_alarm, Ui.TEXT_MUTE, 30))
        addView(text("No alarms yet", 16f, Ui.TEXT, bold = true).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(Space.md), 0, 0)
        })
        addView(text("Tap “Add alarm” to create one.", 13.5f, Ui.TEXT_MUTE).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(Space.xs), 0, 0)
        })
    }
}
