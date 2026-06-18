package com.focus.guard

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
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

class StudyBuddyActivity : ComponentActivity() {
    private lateinit var root: LinearLayout
    private var selectedDate = StudyPlannerStore.todayEpochDay()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(dp(20), dp(28), dp(20), dp(32))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Ui.BG)
            isFillViewport = true
            addView(root)
        })
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        root.removeAllViews()
        val allTasks = StudyData.load(this)
        val slots = StudyPlanStore.forDate(this, selectedDate)

        root.addView(text("🤖 Study Buddy — Jarvis", 24f, Ui.TEXT, bold = true))
        root.addView(text("Add your own today/tomorrow slots, then choose the exact book task for each slot.",
            13.5f, Ui.TEXT_DIM), lp(topMargin = dp(4)))

        root.addView(summaryCard(allTasks.size, slots.size), lp(topMargin = dp(18)))
        root.addView(dateButtons(), lp(topMargin = dp(16)))
        root.addView(text("Plan for ${StudyPlanStore.dateLabel(selectedDate)} · ${StudyPlannerStore.dayLabel(selectedDate)}",
            13f, Ui.TEXT_DIM, bold = true), lp(topMargin = dp(18)))

        if (slots.isEmpty()) {
            root.addView(emptyCard("No slots yet. Tap “Add slot” and choose a study task from your loaded data."))
        } else {
            slots.forEachIndexed { index, slot -> root.addView(slotCard(index, slot, allTasks), lp(topMargin = dp(10))) }
        }

        root.addView(text("LOADED STUDY DATA", 13f, Ui.TEXT_DIM, bold = true), lp(topMargin = dp(22)))
        root.addView(emptyCard("${allTasks.size} digital tasks loaded from your books + lectures data."))
    }

    private fun summaryCard(totalTasks: Int, slotCount: Int): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = gradient(0xFF0E2A1E.toInt(), 0xFF0A2030.toInt(), dp(22).toFloat()).apply {
                setStroke(dp(1), 0x4434D399)
            }
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }
        c.addView(text("YOUR DIGITAL SYLLABUS", 12f, Ui.GREEN, bold = true))
        c.addView(text("$totalTasks tasks", 36f, Ui.TEXT, bold = true), lp(topMargin = dp(4)))
        c.addView(text("$slotCount slots planned for this date", 13f, Ui.TEXT_DIM), lp(topMargin = dp(6)))
        return c
    }

    private fun dateButtons(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(chip("Today") {
            selectedDate = StudyPlannerStore.todayEpochDay()
            render()
        })
        row.addView(chip("Tomorrow", marginStart = dp(8)) {
            selectedDate = StudyPlannerStore.todayEpochDay() + 1
            render()
        })
        row.addView(chip("Change date", marginStart = dp(8)) { chooseDate() })
        row.addView(chip("Add slot", marginStart = dp(8), accent = true) { addSlot() })
        return row
    }

    private fun chip(label: String, marginStart: Int = 0, accent: Boolean = false, onClick: () -> Unit): TextView =
        text(label, 13.5f, if (accent) Ui.TEXT else Ui.TEXT_DIM, bold = true).apply {
            background = if (accent) gradient(Ui.ACCENT, Ui.ACCENT_2, dp(12).toFloat()) else rounded(Ui.SURFACE_HI, dp(12).toFloat())
            setPadding(dp(14), dp(9), dp(14), dp(9))
            isClickable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = marginStart }
        }

    private fun chooseDate() {
        val c = StudyPlanStore.calendarForEpochDay(selectedDate)
        DatePickerDialog(this, { _, y, m, d ->
            selectedDate = StudyPlanStore.epochDayFromCalendar(y, m, d)
            render()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun addSlot() {
        val tasks = StudyData.load(this)
        if (tasks.isEmpty()) {
            toast("Study data not loaded yet.")
            return
        }

        val now = Calendar.getInstance()
        val startMinute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val endMinute = (startMinute + 90) % (24 * 60)
        var chosenTaskId: String? = null

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), 0)
        }
        val taskText = text("Tap to choose task from data", 14f, Ui.ACCENT, bold = true).apply {
            setPadding(0, dp(12), 0, dp(12))
            isClickable = true
            setOnClickListener { chooseTask(tasks) { chosenTaskId = it.id } }
        }
        box.addView(taskText)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Add study slot")
            .setView(box)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val taskId = chosenTaskId
                if (taskId == null) {
                    toast("Choose a task first.")
                    return@setOnClickListener
                }
                TimePickerDialog(this, { _, sh, sm ->
                    val start = sh * 60 + sm
                    TimePickerDialog(this, { _, eh, em ->
                        val end = eh * 60 + em
                        if (end <= start) {
                            toast("End time must be after start time.")
                            return@TimePickerDialog
                        }
                        StudyPlanStore.add(
                            this,
                            StudyPlanSlot(
                                id = "${selectedDate}_${start}_$taskId".sanitizeId(),
                                dateEpochDay = selectedDate,
                                startMinute = start,
                                endMinute = end,
                                taskId = taskId
                            )
                        )
                        dialog.dismiss()
                        render()
                    }, endMinute / 60, endMinute % 60, false).show()
                }, startMinute / 60, startMinute % 60, false).show()
            }
        }
        dialog.show()
    }

    private fun chooseTask(tasks: List<StudyTask>, onChoose: (StudyTask) -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), 0)
        }

        val filters = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        filters.addView(chip("All", accent = true) {})
        filters.addView(chip("Books", marginStart = dp(8)) {})
        filters.addView(chip("Lectures", marginStart = dp(8)) {})
        box.addView(filters)

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(list)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(360)))

        var mode = "All"
        var dialog: AlertDialog? = null
        fun renderList() {
            list.removeAllViews()
            val filtered = when (mode) {
                "Books" -> tasks.filter { it.kind != "Lectures" }
                "Lectures" -> tasks.filter { it.kind == "Lectures" }
                else -> tasks
            }
            if (filtered.isEmpty()) {
                list.addView(text("No tasks in this branch.", 13.5f, Ui.TEXT_MUTE).apply {
                    setPadding(dp(4), dp(16), dp(4), dp(16))
                })
                return
            }
            filtered.forEach { task ->
                list.addView(taskRow(task, mode) {
                    onChoose(task)
                    dialog?.dismiss()
                })
            }
        }

        dialog = AlertDialog.Builder(this)
            .setTitle("Choose study task")
            .setView(box)
            .setNegativeButton("Cancel", null)
            .create()

        (filters.getChildAt(0) as TextView).setOnClickListener {
            mode = "All"
            renderList()
        }
        (filters.getChildAt(1) as TextView).setOnClickListener {
            mode = "Books"
            renderList()
        }
        (filters.getChildAt(2) as TextView).setOnClickListener {
            mode = "Lectures"
            renderList()
        }

        dialog.setOnShowListener { renderList() }
        dialog.show()
    }

    private fun taskRow(task: StudyTask, mode: String, onChoose: (StudyTask) -> Unit): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Ui.SURFACE, dp(12).toFloat(), Ui.STROKE, dp(1))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            isClickable = true
            setOnClickListener { onChoose(task) }
        }
        c.addView(text(if (mode == "Books") "BOOK · ${task.subject}" else "LECTURE · ${task.subject}", 12f, Ui.ACCENT, bold = true))
        c.addView(text(task.book, 14f, Ui.TEXT, bold = true), lp(topMargin = dp(3)))
        c.addView(text(task.chapter, 13f, Ui.TEXT_DIM), lp(topMargin = dp(2)))
        c.addView(text(task.title, 12.5f, Ui.TEXT_DIM), lp(topMargin = dp(2)))
        c.addView(text("~${task.estimatedMinutes} min · ${task.questions} questions · ${task.lectures} lectures", 12f, Ui.TEXT_MUTE),
            lp(topMargin = dp(5)))
        return c
    }

    private fun slotCard(index: Int, slot: StudyPlanSlot, tasks: List<StudyTask>): LinearLayout {
        val task = tasks.firstOrNull { it.id == slot.taskId }
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Ui.SURFACE, dp(16).toFloat(), Ui.STROKE, dp(1))
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        c.addView(text("Slot ${index + 1} · ${StudyPlanStore.timeLabel(slot.startMinute)}–${StudyPlanStore.timeLabel(slot.endMinute)}",
            15f, Ui.TEXT, bold = true))
        if (task == null) {
            c.addView(text("Task data missing", 13f, Ui.RED), lp(topMargin = dp(6)))
        } else {
            c.addView(text("${task.subject} · ${task.book}", 12.5f, Ui.TEXT_MUTE), lp(topMargin = dp(5)))
            c.addView(text(task.chapter, 14f, Ui.TEXT_DIM), lp(topMargin = dp(2)))
            c.addView(text(task.title, 13.5f, Ui.TEXT_DIM), lp(topMargin = dp(2)))
            c.addView(text("~${task.estimatedMinutes} min · ${task.questions} questions · ${task.lectures} lectures", 12.5f, Ui.TEXT_MUTE),
                lp(topMargin = dp(5)))
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }
        row.addView(text("Start", 13f, Ui.TEXT, bold = true).apply {
            background = gradient(Ui.ACCENT, Ui.ACCENT_2, dp(12).toFloat())
            setPadding(dp(14), dp(9), dp(14), dp(9))
            isClickable = true
            setOnClickListener {
                if (task != null) {
                    StudySessionStore.start(this@StudyBuddyActivity, task)
                    toast("Started: ${task.title}")
                    render()
                }
            }
        })
        row.addView(text("Finish", 13f, Ui.TEXT, bold = true).apply {
            background = rounded(Ui.SURFACE_HI, dp(12).toFloat())
            setPadding(dp(14), dp(9), dp(14), dp(9))
            isClickable = true
            setOnClickListener {
                val session = StudySessionStore.finish(this@StudyBuddyActivity, CreditManager(this@StudyBuddyActivity))
                if (session != null) toast("Added ${session.minutes}m study and ${session.creditSeconds}s Minecraft credit.")
                render()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8) })
        row.addView(text("Delete", 13f, Ui.RED, bold = true).apply {
            background = rounded(0x22F87171, dp(12).toFloat())
            setPadding(dp(14), dp(9), dp(14), dp(9))
            isClickable = true
            setOnClickListener {
                StudyPlanStore.delete(this@StudyBuddyActivity, slot.id)
                render()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8) })
        c.addView(row)
        return c
    }

    private fun emptyCard(msg: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Ui.SURFACE, dp(16).toFloat(), Ui.STROKE, dp(1))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            addView(text(msg, 13.5f, Ui.TEXT_MUTE))
        }

    private fun toast(m: String) =
        android.widget.Toast.makeText(this, m, android.widget.Toast.LENGTH_LONG).show()

    private fun String.sanitizeId(): String =
        lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
}
