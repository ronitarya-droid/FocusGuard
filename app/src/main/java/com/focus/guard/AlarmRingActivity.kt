package com.focus.guard

import android.app.NotificationManager
import android.graphics.Bitmap
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
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.focus.guard.Ui.dp
import com.focus.guard.Ui.gradient
import com.focus.guard.Ui.lp
import com.focus.guard.Ui.rounded
import com.focus.guard.Ui.text
import kotlin.random.Random

/**
 * Full-screen alarm. Rings immediately at alarm time, then waits for you to tap
 * Solve before the maths challenge starts. After Solve is tapped, each question
 * pauses the sound for 60s; if time runs out or the answer is wrong, the sound
 * resumes until the question is solved.
 */
class AlarmRingActivity : ComponentActivity() {

    companion object {
        /** True while an alarm is actively ringing. The accessibility service
         *  watches this to drag the user back if they try to escape (home, recents,
         *  power menu) — so the alarm can't be dodged without solving the maths. */
        @Volatile var RINGING = false
    }

    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var questionTimer: CountDownTimer? = null
    private val volHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val volumeEnforcer = object : Runnable {
        override fun run() {
            forceMaxAlarmVolume()
            if (RINGING) volHandler.postDelayed(this, 600L)
        }
    }

    private val TOTAL = 5
    private var solved = 0
    private var answer = 0
    private var mathsStarted = false

    private lateinit var promptView: android.widget.TextView
    private lateinit var progressView: android.widget.TextView
    private lateinit var input: EditText
    private lateinit var hintView: android.widget.TextView

    private var alarmId = -1
    private var proofCaptured = false
    private lateinit var proofButton: TextView
    private lateinit var solveButton: TextView

    private val takeProof: ActivityResultLauncher<Uri> =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (ok) {
                proofCaptured = true
                proofButton.text = "Proof scanned ✓"
                proofButton.background = rounded(0x2234D399, dp(12).toFloat(), 0x5534D399, dp(1))
                toast("Proof captured. Now solve maths to stop alarm.")
            }
        }

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
        volHandler.post(volumeEnforcer)
    }

    /** Hide status/nav bars so the user can't pull the shade or hit nav while ringing. */
    private fun immersive() {
        try {
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
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
            // Block power button — can't turn off the phone while alarm is ringing.
            android.view.KeyEvent.KEYCODE_POWER -> {
                showPowerBlockedHint()
                return true   // consume
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private var powerHintView: android.widget.TextView? = null

    private fun showPowerBlockedHint() {
        if (powerHintView != null) return
        val root = window.decorView.findViewById<android.view.ViewGroup>(android.R.id.content)
        powerHintView = android.widget.TextView(this).apply {
            text = "⚠  Can't turn off while alarm is ringing — solve the challenge first"
            setTextColor(Ui.AMBER)
            textSize = 14f
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0x33FF7A18)
                cornerRadius = dp(12).toFloat()
            }
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val lp = android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(8)
            marginStart = dp(24)
            marginEnd = dp(24)
        }
        (root as? android.widget.LinearLayout)?.addView(powerHintView, lp)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            powerHintView?.let {
                (root as? android.widget.LinearLayout)?.removeView(it)
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
            ).apply { acquire(5 * 60 * 1000L) }
        } catch (_: Exception) {}
    }

    private fun buildUi() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Ui.BG)
            setPadding(dp(28), dp(70), dp(28), dp(28))
        }
        col.addView(text("⏰", 52f, Ui.ACCENT).apply { gravity = Gravity.CENTER })
        col.addView(text("Alarm ringing", 24f, Ui.TEXT, bold = true).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0)
        })
        progressView = text("Tap Solve to start maths", 14f, Ui.TEXT_DIM).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(6), 0, dp(26))
        }
        col.addView(progressView)

        promptView = text("🔔", 48f, Ui.ACCENT).apply { gravity = Gravity.CENTER }
        col.addView(promptView)

        input = EditText(this).apply {
            hint = "Answer"; gravity = Gravity.CENTER; textSize = 24f
            setTextColor(Ui.TEXT); setHintTextColor(Ui.TEXT_MUTE)
            inputType = InputType.TYPE_CLASS_NUMBER
            background = rounded(Ui.SURFACE, dp(14).toFloat(), Ui.STROKE, dp(1))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        col.addView(input, lp(width = dp(220), topMargin = dp(24)))

        hintView = text("Alarm is ringing now. Scan proof, then tap Solve to start maths; sound pauses for 60s per question.", 12.5f, Ui.TEXT_MUTE).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(20), 0, 0)
        }
        col.addView(hintView)

        val proofRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, 0)
        }
        proofButton = text("Scan proof", 14f, Ui.TEXT, bold = true).apply {
            background = rounded(Ui.SURFACE_HI, dp(12).toFloat())
            setPadding(dp(16), dp(10), dp(16), dp(10))
            isClickable = true
            setOnClickListener {
                val photo = java.io.File(cacheDir, "wake_proof.jpg")
                takeProof.launch(android.net.Uri.fromFile(photo))
            }
        }
        solveButton = text("Solve", 17f, Ui.TEXT, bold = true).apply {
            gravity = Gravity.CENTER
            background = gradient(Ui.ACCENT, Ui.ACCENT_2, dp(16).toFloat())
            setPadding(dp(48), dp(15), dp(48), dp(15))
            isClickable = true
            setOnClickListener { checkAnswer() }
        }
        proofRow.addView(proofButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = dp(8)
        })
        proofRow.addView(solveButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = dp(8)
        })
        col.addView(proofRow)

        setContentView(col)
    }

    private fun nextQuestion() {
        val a = Random.nextInt(10, 99)
        val b = Random.nextInt(10, 99)
        answer = a + b
        mathsStarted = true
        promptView.text = "$a + $b"
        progressView.text = "Question ${solved + 1} of $TOTAL"
        input.setText("")
        input.requestFocus()
        // Pause sound and give 60 quiet seconds.
        pauseSound()
        startQuestionTimer()
    }

    private fun startQuestionTimer() {
        questionTimer?.cancel()
        hintView.text = "Sound paused — solve within 60s."
        questionTimer = object : CountDownTimer(60_000, 1000) {
            override fun onTick(ms: Long) {
                hintView.text = "Sound paused — ${ms / 1000}s left to solve."
            }
            override fun onFinish() {
                // Time up → resume sound as a nudge; keep the same question.
                hintView.text = "Time up! Sound resumed — answer to silence it."
                resumeSound()
            }
        }.start()
    }

    private fun checkAnswer() {
        if (!proofCaptured) {
            toast("Scan QR/book page proof first.")
            return
        }
        if (!mathsStarted) {
            nextQuestion()
            return
        }
        val v = input.text.toString().toIntOrNull()
        if (v == null) { input.error = "Enter a number"; return }
        if (v != answer) {
            input.error = "Wrong"; input.setText("")
            resumeSound()   // penalty: sound back on for a wrong answer
            return
        }
        solved++
        questionTimer?.cancel()
        if (solved >= TOTAL) finishAlarm() else nextQuestion()
    }

    // ---- sound ----
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
        volHandler.removeCallbacks(volumeEnforcer)
        questionTimer?.cancel()
        try { player?.stop(); player?.release() } catch (_: Exception) {}
        player = null
        try {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(7000 + alarmId)
        } catch (_: Exception) {}
        finish()
    }

    override fun onDestroy() {
        RINGING = false
        volHandler.removeCallbacks(volumeEnforcer)
        questionTimer?.cancel()
        try { player?.release() } catch (_: Exception) {}
        try { wakeLock?.release() } catch (_: Exception) {}
        powerHintView?.let {
            val root = window.decorView.findViewById<android.view.ViewGroup>(android.R.id.content)
            (root as? android.widget.LinearLayout)?.removeView(it)
        }
        super.onDestroy()
    }

    // Block back button — must solve the maths.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* swallow */ }

    private fun toast(m: String) =
        android.widget.Toast.makeText(this, m, android.widget.Toast.LENGTH_LONG).show()
}
