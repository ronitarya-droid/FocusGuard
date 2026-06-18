package com.focus.guard

import android.app.AlertDialog
import android.app.DatePickerDialog
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
import java.util.Calendar

class StudyLogActivity : ComponentActivity() {

    private lateinit var credits: CreditManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        credits = CreditManager(this)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(dp(20), dp(28), dp(20), dp(32))
        }

        content.addView(text("Study & Rewards", 24f, Ui.TEXT, bold = true))
        content.addView(text("Log study, track exam target, and earn Minecraft credit.", 13.5f, Ui.TEXT_DIM),
            lp(topMargin = dp(4)))

        content.addView(examCard(), lp(topMargin = dp(18)))
        content.addView(creditCard(), lp(topMargin = dp(14)))
        content.addView(chartCard(), lp(topMargin = dp(14)))
        content.addView(buildLogCard(), lp(topMargin = dp(18)))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Ui.BG); isFillViewport = true; addView(content)
        })
    }

    private fun examCard(): LinearLayout {
        val c = card()
        c.addView(text("EXAM COUNTDOWN", 12f, Ui.ACCENT, bold = true))
        val days = ExamManager.daysLeft(this)
        val target = ExamManager.targetPerDay(this)
        val line = if (ExamManager.hasExam(this)) {
            "${ExamManager.dateLabel(ExamManager.examDay(this))} · $days days left · target ${target.toInt()}% / day"
        } else {
            "No exam date set · set date to calculate daily syllabus target"
        }
        c.addView(text(line, 15f, Ui.TEXT, bold = true), lp(topMargin = dp(6)))
        c.addView(text("Syllabus done: ${ExamManager.syllabusPercent(this)}%", 12.5f, Ui.TEXT_MUTE), lp(topMargin = dp(5)))
        c.addView(text("Set exam date", 13.5f, Ui.TEXT, bold = true).apply {
            background = rounded(Ui.SURFACE_HI, dp(10).toFloat())
            setPadding(dp(12), dp(9), dp(12), dp(9))
            isClickable = true
            setOnClickListener { chooseExamDate() }
        }, lp(topMargin = dp(12)))
        c.addView(text("Syllabus %", 13.5f, Ui.TEXT_DIM, bold = true).apply {
            background = rounded(Ui.SURFACE_HI, dp(10).toFloat())
            setPadding(dp(12), dp(9), dp(12), dp(9))
            isClickable = true
            setOnClickListener { chooseSyllabusPercent() }
        }, lp(topMargin = dp(8)))
        return c
    }

    private fun creditCard(): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = gradient(0xFF0E2A1E.toInt(), 0xFF0A2030.toInt(), dp(22).toFloat()).apply {
                setStroke(dp(1), 0x4434D399)
            }
            setPadding(dp(22), dp(20), dp(22), dp(20))
        }
        c.addView(text("MINECRAFT CREDIT", 12f, Ui.GREEN, bold = true))
        c.addView(text("${credits.creditMinutes()} min", 40f, Ui.TEXT, bold = true), lp(topMargin = dp(6)))
        c.addView(text("(${credits.creditSeconds()} seconds banked)", 12.5f, Ui.TEXT_DIM))
        return c
    }

    private fun chartCard(): LinearLayout {
        val c = card()
        c.addView(text("LAST 7 DAYS STUDY", 12f, Ui.TEXT_DIM, bold = true))
        val rows = credits.last7Days().reversed()
        val max = rows.maxOfOrNull { it.second } ?: 60
        rows.forEach { (day, minutes) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(7), 0, dp(7))
            }
            row.addView(text(StudyPlannerStore.dayLabel(day).substring(0, 3), 11f, Ui.TEXT_MUTE).apply {
                layoutParams = LinearLayout.LayoutParams(dp(34), ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            val bar = TextView(this).apply {
                val w = if (max <= 0) dp(4) else dp(4) + ((dp(160) - dp(4)) * minutes / max)
                layoutParams = LinearLayout.LayoutParams(w.coerceAtLeast(dp(4)), dp(12))
                background = rounded(if (minutes > 0) Ui.GREEN else Ui.STROKE, dp(6).toFloat())
            }
            row.addView(bar)
            row.addView(text("$minutes min", 11.5f, Ui.TEXT_DIM).apply {
                setPadding(dp(10), 0, 0, 0)
            })
            c.addView(row)
        }
        return c
    }

    private fun buildLogCard(): LinearLayout {
        val card = card()

        when {
            credits.isLoggedToday() -> {
                card.addView(text("✅ Logged for today", 16f, Ui.GREEN, bold = true))
                card.addView(text("Come back tomorrow between 10 PM and 12 AM to log again.",
                    13.5f, Ui.TEXT_DIM).apply { setPadding(0, dp(8), 0, 0) })
            }
            !credits.isLogWindowOpen() -> {
                card.addView(text("🔒 Logging is closed", 16f, Ui.AMBER, bold = true))
                card.addView(text("You can only log study hours between 10:00 PM and 12:00 AM. " +
                    "This keeps you honest — log right before bed.",
                    13.5f, Ui.TEXT_DIM).apply { setPadding(0, dp(8), 0, 0) })
            }
            else -> {
                card.addView(text("How many hours did you study today?", 15f, Ui.TEXT, bold = true))
                val input = EditText(this).apply {
                    hint = "e.g. 6.5"
                    setHintTextColor(Ui.TEXT_MUTE); setTextColor(Ui.TEXT); textSize = 18f
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    background = rounded(Ui.SURFACE_HI, dp(14).toFloat(), Ui.STROKE, dp(1))
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                }
                card.addView(input, lp(topMargin = dp(14)))

                val btn = text("Bank my credit", 15.5f, Ui.TEXT, bold = true).apply {
                    gravity = Gravity.CENTER
                    background = gradient(Ui.ACCENT, Ui.ACCENT_2, dp(16).toFloat())
                    setPadding(dp(18), dp(16), dp(18), dp(16))
                    isClickable = true
                }
                btn.setOnClickListener {
                    val hours = input.text.toString().toDoubleOrNull()
                    if (hours == null || hours <= 0.0) {
                        input.error = "Enter a number of hours"
                        return@setOnClickListener
                    }
                    if (hours > 24) { input.error = "Max 24 hours in a day"; return@setOnClickListener }
                    val added = credits.logStudyHours(hours)
                    if (added >= 0) {
                        val mins = added / 60; val secs = added % 60
                        toast("Earned ${mins}m ${secs}s of Minecraft credit!")
                        recreate()
                    } else {
                        toast("Can't log right now.")
                    }
                }
                card.addView(btn, lp(topMargin = dp(16)))
            }
        }
        return card
    }

    private fun chooseExamDate() {
        val c = if (ExamManager.hasExam(this)) ExamManager.calendarForDay(ExamManager.examDay(this)) else Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            ExamManager.setExamDay(this@StudyLogActivity, ExamManager.dayFromCalendar(y, m, d))
            recreate()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun chooseSyllabusPercent() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(ExamManager.syllabusPercent(this@StudyLogActivity).toString())
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.TEXT_MUTE)
        }
        AlertDialog.Builder(this@StudyLogActivity)
            .setTitle("Syllabus completed %")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val p = input.text.toString().toIntOrNull()
                if (p != null) {
                    ExamManager.setSyllabusPercent(this@StudyLogActivity, p)
                    recreate()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(Ui.SURFACE, dp(20).toFloat(), Ui.STROKE, dp(1))
        setPadding(dp(20), dp(20), dp(20), dp(20))
    }

    private fun toast(m: String) =
        android.widget.Toast.makeText(this, m, android.widget.Toast.LENGTH_LONG).show()
}
