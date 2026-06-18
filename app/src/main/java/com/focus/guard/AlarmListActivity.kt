package com.focus.guard

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.focus.guard.Ui.dp
import com.focus.guard.Ui.gradient
import com.focus.guard.Ui.lp
import com.focus.guard.Ui.rounded
import com.focus.guard.Ui.text
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
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(dp(20), dp(28), dp(20), dp(32))
        }
        content.addView(text("Alarms", 24f, Ui.TEXT, bold = true))
        content.addView(text("Dismiss by solving 5 maths problems. Sound pauses 60s per question.",
            13.5f, Ui.TEXT_DIM), lp(topMargin = dp(4)))

        content.addView(text("➕  Add alarm", 15.5f, Ui.TEXT, bold = true).apply {
            gravity = Gravity.CENTER
            background = gradient(Ui.ACCENT, Ui.ACCENT_2, dp(16).toFloat())
            setPadding(dp(18), dp(15), dp(18), dp(15))
            isClickable = true
            setOnClickListener { addAlarm() }
        }, lp(topMargin = dp(16)))

        listCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(listCol, lp(topMargin = dp(8)))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Ui.BG); isFillViewport = true; addView(content)
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
            listCol.addView(text("No alarms yet.", 14f, Ui.TEXT_MUTE).apply {
                setPadding(dp(2), dp(20), 0, 0)
            })
            return
        }
        for (a in alarms) listCol.addView(row(a), lp(topMargin = dp(12)))
    }

    private fun row(a: FgAlarm): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Ui.SURFACE, dp(16).toFloat(), Ui.STROKE, dp(1))
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        val timeStr = String.format("%02d:%02d", a.hour, a.minute)
        top.addView(text(timeStr, 30f, Ui.TEXT, bold = true).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        top.addView(Switch(this).apply {
            isChecked = a.enabled
            setOnCheckedChangeListener { _, on ->
                val u = a.copy(enabled = on)
                AlarmStore.put(this@AlarmListActivity, u)
                if (on) AlarmScheduler.schedule(this@AlarmListActivity, u)
                else AlarmScheduler.cancel(this@AlarmListActivity, a.id)
            }
        })
        card.addView(top)

        val soundName = a.uri?.let { displayName(Uri.parse(it)) } ?: "Default alarm tone"
        card.addView(text("🎵 $soundName", 13f, Ui.TEXT_DIM).apply {
            maxLines = 1; setPadding(0, dp(6), 0, 0)
        })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, 0)
        }
        actions.addView(text("Choose mp3", 13.5f, Ui.TEXT, bold = true).apply {
            background = rounded(Ui.SURFACE_HI, dp(10).toFloat())
            setPadding(dp(14), dp(9), dp(14), dp(9))
            isClickable = true
            setOnClickListener {
                pickForId = a.id
                pickSound.launch(arrayOf("audio/*"))
            }
        })
        actions.addView(text("Delete", 13.5f, Ui.RED, bold = true).apply {
            background = rounded(0x22F87171, dp(10).toFloat())
            setPadding(dp(14), dp(9), dp(14), dp(9))
            isClickable = true
            setOnClickListener {
                AlarmScheduler.cancel(this@AlarmListActivity, a.id)
                AlarmStore.delete(this@AlarmListActivity, a.id)
                render()
            }
            (layoutParams as? LinearLayout.LayoutParams)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { leftMargin = dp(10) })
        card.addView(actions)
        return card
    }

    private fun displayName(uri: Uri): String {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else uri.lastPathSegment ?: "Custom"
            } ?: (uri.lastPathSegment ?: "Custom")
        } catch (_: Exception) { "Custom sound" }
    }
}
