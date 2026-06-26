package com.focus.guard

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Real-time pedometer for the alarm's "walk 100 steps" challenge.
 *
 * Built on the raw accelerometer (~50 Hz, no special permission) instead of the
 * hardware step-detector: OEM builds (Oppo / ColorOS especially) batch and delay
 * the step-detector's events, which made the count feel laggy AND made the
 * "you stopped walking" check fire while the user was still moving. The
 * accelerometer is sampled continuously, so steps register the instant a footfall
 * happens and motion is always known.
 *
 * Two outputs:
 *   • [onStep] fires the moment a footfall is detected → the on-screen count
 *     updates with zero lag.
 *   • [millisSinceMotion] reports how long the phone has been essentially still,
 *     so the alarm can tell real standing-still apart from brief gaps and never
 *     rings while the user is genuinely walking.
 */
class StepCounter(
    private val context: Context,
    private val onStep: () -> Unit
) : SensorEventListener {

    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    @Volatile private var lastMotionMs = 0L

    private var gravity = 9.81f      // slow low-pass → tracks the gravity baseline
    private var filtered = 0f        // gravity-removed, lightly smoothed signal
    private var armed = true         // hysteresis flag: ready to accept the next peak
    private var lastStepMs = 0L

    fun start() {
        lastMotionMs = System.currentTimeMillis()
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            // maxReportLatencyUs = 0 → no batching, samples arrive immediately.
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME, 0)
        }
    }

    fun stop() { try { sm.unregisterListener(this) } catch (_: Exception) {} }

    /** Milliseconds since the phone last showed walking-scale movement. */
    fun millisSinceMotion(): Long = System.currentTimeMillis() - lastMotionMs

    override fun onSensorChanged(e: SensorEvent) {
        val mag = sqrt(
            e.values[0] * e.values[0] +
            e.values[1] * e.values[1] +
            e.values[2] * e.values[2]
        )
        gravity = gravity * 0.9f + mag * 0.1f
        val linear = mag - gravity                 // ~0 at rest, swings while walking
        filtered = filtered * 0.6f + linear * 0.4f

        val now = System.currentTimeMillis()
        if (abs(linear) > MOTION_DELTA) lastMotionMs = now

        // Peak detection with hysteresis + a refractory period so one bouncy
        // footfall can't double-count and sensor noise can't fake a step.
        if (filtered < LOWER_GATE) {
            armed = true
        } else if (filtered > UPPER_GATE && armed && now - lastStepMs > MIN_STEP_MS) {
            armed = false
            lastStepMs = now
            onStep()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private companion object {
        const val UPPER_GATE = 1.1f     // m/s² above gravity — a footfall peak
        const val LOWER_GATE = 0.3f     // must fall back near baseline between steps
        const val MIN_STEP_MS = 250L    // fastest plausible cadence (~4 steps/sec)
        const val MOTION_DELTA = 0.5f   // any swing this big counts as "moving"
    }
}
