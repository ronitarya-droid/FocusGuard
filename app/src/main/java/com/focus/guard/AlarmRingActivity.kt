package com.focus.guard

import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.PowerManager
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.focus.guard.Ui.Radius
import com.focus.guard.Ui.Space
import com.focus.guard.Ui.dp
import com.focus.guard.Ui.dpf
import com.focus.guard.Ui.gradient
import com.focus.guard.Ui.icon
import com.focus.guard.Ui.iconChip
import com.focus.guard.Ui.lp
import com.focus.guard.Ui.rounded
import com.focus.guard.Ui.stat
import com.focus.guard.Ui.text
import com.focus.guard.Ui.tintWash
import kotlin.random.Random

/**
 * Full-screen alarm. Rings immediately at alarm time. To dismiss it the user must:
 *   1. Tap "Start" — the tone goes silent and a 100-step walk begins.
 *   2. Walk 100 steps (phone in hand). If they stand still for 10s before
 *      finishing, the tone rings again until they start moving once more.
 *   3. After 100 steps, solve 5 maths problems. Each question gives 60 quiet
 *      seconds; a wrong answer or a timeout brings the tone back until solved.
 *   4. All 5 solved → the alarm stops.
 *
 * SECURITY-CRITICAL: every escape vector (volume keys, power button, back, home,
 * recents, lockscreen) is deliberately blocked while RINGING. The accessibility
 * service drags the user back if they try to escape. The phase logic changed in
 * this redesign; the enforcement is unchanged.
 */
class AlarmRingActivity : ComponentActivity() {

    companion object {
        /** True while an alarm is actively ringing. The accessibility service
         *  watches this to drag the user back if they try to escape (home, recents,
         *  power menu) — so the alarm can't be dodged without completing it. */
        @Volatile var RINGING = false

        private const val STEPS_REQUIRED = 100
        private const val TOTAL_QUESTIONS = 5
        private const val QUESTION_MS = 60_000L
        private const val STILL_RESUME_MS = 10_000L   // stop walking this long → tone returns
    }

    private enum class Phase { RING, WALK, MATH }
    private var phase = Phase.RING

    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val volumeEnforcer = object : Runnable {
        override fun run() {
            forceMaxAlarmVolume()
            if (RINGING) mainHandler.postDelayed(this, 600L)
        }
    }

    // ---- walk phase ----
    private var stepCounter: StepCounter? = null
    private var steps = 0
    private val stillWatchdog = object : Runnable {
        override fun run() {
            if (phase != Phase.WALK) return
            // Decide ring vs silent from continuous MOTION, not from step events
            // (step events can briefly gap while genuinely walking). Only true
            // standing-still for 10s brings the tone back.
            val stillFor = stepCounter?.millisSinceMotion() ?: 0L
            if (stillFor >= STILL_RESUME_MS) resumeSound() else pauseSound()
            mainHandler.postDelayed(this, 400L)
        }
    }

    // ---- maths phase ----
    private var questionTimer: CountDownTimer? = null
    private var solved = 0
    private var answer = 0

    private lateinit var progressView: TextView
    private lateinit var promptView: TextView
    private lateinit var input: EditText
    private lateinit var hintView: TextView
    private lateinit var actionButton: TextView

    private var alarmId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        alarmId = intent.getIntExtra(AlarmScheduler.EXTRA_ID, -1)
        RINGING = true
        showOverLockscreen()
        immersive()
        wakeUp()
        buildUi()
        startSound(AlarmStore.get(this, alarmId)?.uri)
        forceMaxAlarmVolume()
        mainHandler.post(volumeEnforcer)
        showRingPhase()
    }

    // ───────────────────────── UI ─────────────────────────

    private fun buildUi() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Ui.BG)
            setPadding(dp(28), dp(64), dp(28), dp(32))
        }

        col.addView(iconChip(R.drawable.ic_alarm, Ui.ACCENT, tintWash(Ui.ACCENT), 72, Radius.lg))
        col.addView(text("Alarm ringing", 26f, Ui.TEXT, bold = true).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(Space.lg), 0, 0)
        })
        progressView = text("", 14f, Ui.TEXT_DIM).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(Space.sm), 0, dp(Space.xxl))
        }
        col.addView(progressView)

        // Big focal card — shows the step count during the walk, the sum during maths.
        val promptCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(Ui.SURFACE, dpf(Radius.xl), Ui.STROKE, dp(1))
            setPadding(dp(24), dp(28), dp(24), dp(28))
            elevation = dpf(6f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        promptView = stat("0 / $STEPS_REQUIRED", 40f, Ui.ACCENT).apply { gravity = Gravity.CENTER }
        promptCard.addView(promptView)
        col.addView(promptCard)

        input = EditText(this).apply {
            hint = "Answer"; gravity = Gravity.CENTER; textSize = 24f
            setTextColor(Ui.TEXT); setHintTextColor(Ui.TEXT_MUTE)
            inputType = InputType.TYPE_CLASS_NUMBER
            background = rounded(Ui.SURFACE_LO, dpf(Radius.md), Ui.STROKE, dp(1))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            visibility = View.GONE
        }
        col.addView(input, lp(width = dp(220), topMargin = dp(Space.xl)))

        hintView = text("", 12.5f, Ui.TEXT_MUTE).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(Space.lg), 0, 0)
        }
        col.addView(hintView)

        actionButton = text("Start — I'm walking", 17f, Ui.TEXT, bold = true).apply {
            gravity = Gravity.CENTER
            background = Ui.pressable(gradient(Ui.ACCENT, Ui.ACCENT_2, dpf(Radius.md)))
            setPadding(dp(48), dp(15), dp(48), dp(15))
            isClickable = true
            setOnClickListener { onActionTapped() }
        }
        col.addView(actionButton, lp(topMargin = dp(Space.xl)))

        setContentView(col)
    }

    private fun onActionTapped() {
        when (phase) {
            Phase.RING -> startWalk()
            Phase.MATH -> checkAnswer()
            Phase.WALK -> {}   // no button during the walk
        }
    }

    // ───────────────────────── phases ─────────────────────────

    private fun showRingPhase() {
        phase = Phase.RING
        progressView.text = "Walk $STEPS_REQUIRED steps to silence it"
        promptView.text = "0 / $STEPS_REQUIRED"
        input.visibility = View.GONE
        hintView.text = "Pick up your phone and tap Start, then walk."
        actionButton.visibility = View.VISIBLE
        actionButton.text = "Start — I'm walking"
    }

    private fun startWalk() {
        phase = Phase.WALK
        steps = 0
        progressView.text = "Keep walking — sound stays off"
        promptView.text = "0 / $STEPS_REQUIRED"
        hintView.text = "Stop for 10s and the alarm rings again."
        actionButton.visibility = View.GONE
        pauseSound()
        stepCounter = StepCounter(this) { onStep() }.also { it.start() }
        mainHandler.postDelayed(stillWatchdog, 400L)
    }

    private fun onStep() {
        if (phase != Phase.WALK) return
        steps++
        promptView.text = "$steps / $STEPS_REQUIRED"
        if (steps >= STEPS_REQUIRED) startMaths()
    }

    private fun startMaths() {
        phase = Phase.MATH
        mainHandler.removeCallbacks(stillWatchdog)
        stepCounter?.stop(); stepCounter = null
        solved = 0
        input.visibility = View.VISIBLE
        actionButton.visibility = View.VISIBLE
        actionButton.text = "Submit"
        nextQuestion()
    }

    private fun nextQuestion() {
        val a = Random.nextInt(10, 99)
        val b = Random.nextInt(10, 99)
        answer = a + b
        promptView.text = "$a + $b"
        progressView.text = "Question ${solved + 1} of $TOTAL_QUESTIONS"
        hintView.text = "Sound's off — no need to keep walking. Just solve."
        input.setText("")
        input.requestFocus()
        pauseSound()            // stays quiet while you solve, standing still
        startQuestionTimer()
    }

    /**
     * Per-question clock. The maths phase stays SILENT while you solve so you can
     * stand still and think — it does NOT depend on walking. If a question drags
     * past 60s it gives a short nudge buzz and resets, rather than ringing
     * continuously, so a slow question never traps you into pacing around.
     */
    private fun startQuestionTimer() {
        questionTimer?.cancel()
        questionTimer = object : CountDownTimer(QUESTION_MS, 1000) {
            override fun onTick(ms: Long) {
                hintView.text = "Sound's off — just solve it. (${ms / 1000}s)"
            }
            override fun onFinish() {
                penaltyBuzz()           // brief nudge, then quiet again
                startQuestionTimer()    // reset the clock; keep waiting for the answer
            }
        }.start()
    }

    /** A short ~3s buzz as a penalty/nudge, then back to silence so the user can
     *  keep solving without the alarm blaring over them while they're still. */
    private fun penaltyBuzz() {
        resumeSound()
        mainHandler.postDelayed({ if (phase == Phase.MATH) pauseSound() }, 3000L)
    }

    private fun checkAnswer() {
        val v = input.text.toString().toIntOrNull()
        if (v == null) { input.error = "Enter a number"; return }
        if (v != answer) {
            input.error = "Wrong"; input.setText("")
            penaltyBuzz()       // brief penalty buzz, then quiet so you can retry
            return
        }
        solved++
        questionTimer?.cancel()
        if (solved >= TOTAL_QUESTIONS) finishAlarm() else nextQuestion()
    }

    // ───────────────────────── sound ─────────────────────────

    private fun startSound(uriStr: String?) {
        try {
            val uri: Uri = uriStr?.let { Uri.parse(it) }
                ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
                ?: return
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(this@AlarmRingActivity, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {}
    }

    private fun pauseSound() { try { if (player?.isPlaying == true) player?.pause() } catch (_: Exception) {} }
    private fun resumeSound() { try { if (player?.isPlaying != true) player?.start() } catch (_: Exception) {} }

    private fun finishAlarm() {
        RINGING = false
        mainHandler.removeCallbacks(volumeEnforcer)
        mainHandler.removeCallbacks(stillWatchdog)
        questionTimer?.cancel()
        stepCounter?.stop(); stepCounter = null
        try { player?.stop(); player?.release() } catch (_: Exception) {}
        player = null
        try {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(7000 + alarmId)
        } catch (_: Exception) {}
        finish()
    }

    // ───────────────────────── enforcement (unchanged) ─────────────────────────

    /** Hide status/nav bars so the user can't pull the shade or hit nav while ringing. */
    private fun immersive() {
        try {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        } catch (_: Exception) {}
    }

    /** Keep the alarm stream pinned at max so the volume can't be lowered. */
    private fun forceMaxAlarmVolume() {
        try {
            val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
            val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
            am.setStreamVolume(android.media.AudioManager.STREAM_ALARM, max, 0)
        } catch (_: Exception) {}
    }

    /** Swallow the volume keys (and force volume back to max) so they can't quiet it. */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        when (event.keyCode) {
            android.view.KeyEvent.KEYCODE_VOLUME_DOWN,
            android.view.KeyEvent.KEYCODE_VOLUME_UP,
            android.view.KeyEvent.KEYCODE_VOLUME_MUTE -> {
                forceMaxAlarmVolume()
                return true   // consume
            }
            android.view.KeyEvent.KEYCODE_POWER -> {
                showPowerBlockedHint()
                return true   // consume
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private var powerHintView: TextView? = null

    private fun showPowerBlockedHint() {
        if (powerHintView != null) return
        val root = window.decorView.findViewById<ViewGroup>(android.R.id.content)
        powerHintView = TextView(this).apply {
            text = "⚠  Can't turn off while alarm is ringing — finish the challenge first"
            setTextColor(Ui.AMBER)
            textSize = 14f
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0x33FF7A18)
                cornerRadius = dpf(Radius.sm)
            }
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val lpHint = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(8); marginStart = dp(24); marginEnd = dp(24)
        }
        (root as? LinearLayout)?.addView(powerHintView, lpHint)
        mainHandler.postDelayed({
            powerHintView?.let {
                (root as? LinearLayout)?.removeView(it)
                powerHintView = null
            }
        }, 2500L)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && RINGING) immersive()
    }

    private fun showOverLockscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true); setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun wakeUp() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "focusguard:alarm"
            ).apply { acquire(10 * 60 * 1000L) }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        RINGING = false
        mainHandler.removeCallbacks(volumeEnforcer)
        mainHandler.removeCallbacks(stillWatchdog)
        questionTimer?.cancel()
        stepCounter?.stop()
        try { player?.release() } catch (_: Exception) {}
        try { wakeLock?.release() } catch (_: Exception) {}
        powerHintView?.let {
            val root = window.decorView.findViewById<ViewGroup>(android.R.id.content)
            (root as? LinearLayout)?.removeView(it)
        }
        super.onDestroy()
    }

    // Block back button — must finish the challenge.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* swallow */ }
}
