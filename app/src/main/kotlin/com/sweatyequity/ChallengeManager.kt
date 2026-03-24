package com.sweatyequity

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

/**
 * ChallengeManager — the Sweat Equity Engine.
 *
 * Manages sensor registration and counts physical exercise reps for one of
 * three challenge types. Callbacks are fired on the sensor thread; callers
 * must marshal to the UI thread themselves (e.g. via `runOnUiThread`).
 *
 * Sensors are registered in [register] and unregistered in [unregister].
 * Always pair these calls with Activity lifecycle (onResume / onPause).
 *
 * Challenge specs
 * ───────────────
 * SPRINT    – TYPE_STEP_DETECTOR  → 750 steps, 4-minute countdown.
 *             Countdown resets the counter to 0 when it expires.
 * PUSHUPS   – TYPE_PROXIMITY      → 100 NEAR-to-FAR state changes.
 * CURL_UPS  – TYPE_ACCELEROMETER  → 50 upward pitch transitions (phone held
 *             against chest, tilting up through PITCH_THRESHOLD degrees).
 */
class ChallengeManager(
    context: Context,
    val challengeType: ChallengeType,
    private val targetOverride: Int? = null,
    private val onProgressUpdate: (current: Int, target: Int) -> Unit,
    private val onCompleted: () -> Unit,
    private val onTimerTick: ((secondsRemaining: Long) -> Unit)? = null,
    private val onTimerExpired: (() -> Unit)? = null
) : SensorEventListener {

    // ─── Constants ────────────────────────────────────────────────────────────

    companion object {
        const val SPRINT_STEP_TARGET = 750
        const val SPRINT_TIMER_MS    = 4 * 60 * 1000L  // 4 minutes
        const val PUSHUP_TARGET      = 100
        const val CURL_UP_TARGET     = 50

        /** Minimum upward pitch (degrees) to register one curl-up. */
        private const val CURL_UP_PITCH_THRESHOLD = 20.0
        /** Baseline is captured only near neutral posture to avoid false baselines. */
        private const val CURL_UP_BASELINE_MAX_DEGREES = 15.0
        /** Requires meaningful torso movement above baseline to count a rep. */
        private const val CURL_UP_MIN_DELTA_DEGREES = 12.0
        private const val PUSHUP_MIN_INTERVAL_MS = 1_500L
        private const val CURL_UP_MIN_INTERVAL_MS = 1_500L
    }

    // ─── State ────────────────────────────────────────────────────────────────

    val target: Int
        get() = (targetOverride ?: when (challengeType) {
            ChallengeType.SPRINT   -> SPRINT_STEP_TARGET
            ChallengeType.PUSHUPS  -> PUSHUP_TARGET
            ChallengeType.CURL_UPS -> CURL_UP_TARGET
        }).coerceAtLeast(1)

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private var currentCount       = 0
    private var isRegistered       = false

    // Pushup state
    private var lastProximityNear  = false
    private var lastPushupCountAtMs = 0L

    // Curl-up state
    private var lastCurledUp       = false
    private var lastCurlCountAtMs  = 0L
    private var baselinePitchDeg   = 0.0
    private var hasBaselinePitch   = false
    private var pendingBaselineMinPitchDeg = Double.MAX_VALUE

    // Sprint timer — implemented with a simple Handler + Runnable loop so we
    // don't pull in coroutines or extra libraries.
    private var sprintTimerHandle: android.os.Handler? = null
    private var sprintTimerRunnable: Runnable? = null
    private var sprintDeadlineMs: Long = 0L

    // ─── Public API ───────────────────────────────────────────────────────────

    fun register() {
        if (isRegistered) return
        isRegistered = true
        currentCount = 0
        lastPushupCountAtMs = 0L
        lastCurlCountAtMs = 0L
        baselinePitchDeg = 0.0
        hasBaselinePitch = false
        pendingBaselineMinPitchDeg = Double.MAX_VALUE
        lastCurledUp = false
        lastProximityNear = false
        when (challengeType) {
            ChallengeType.SPRINT   -> registerSprint()
            ChallengeType.PUSHUPS  -> registerPushups()
            ChallengeType.CURL_UPS -> registerCurlUps()
        }
    }

    fun unregister() {
        if (!isRegistered) return
        isRegistered = false
        sensorManager.unregisterListener(this)
        cancelSprintTimer()
    }

    // ─── Sensor registration ──────────────────────────────────────────────────

    private fun registerSprint() {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        if (sensor != null) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        startSprintTimer()
    }

    private fun registerPushups() {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (sensor != null) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun registerCurlUps() {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor != null) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    // ─── Sprint timer ─────────────────────────────────────────────────────────

    private fun startSprintTimer() {
        cancelSprintTimer()
        sprintDeadlineMs = System.currentTimeMillis() + SPRINT_TIMER_MS
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        sprintTimerHandle = handler

        val runnable = object : Runnable {
            override fun run() {
                if (!isRegistered) return
                val remaining = sprintDeadlineMs - System.currentTimeMillis()
                if (remaining <= 0) {
                    // Timer expired — reset count and restart
                    currentCount = 0
                    onProgressUpdate(currentCount, target)
                    onTimerExpired?.invoke()
                    startSprintTimer()
                } else {
                    onTimerTick?.invoke(remaining / 1000)
                    handler.postDelayed(this, 1_000L)
                }
            }
        }
        sprintTimerRunnable = runnable
        handler.postDelayed(runnable, 1_000L)
    }

    private fun cancelSprintTimer() {
        sprintTimerHandle?.removeCallbacks(sprintTimerRunnable)
        sprintTimerHandle = null
        sprintTimerRunnable = null
    }

    // ─── SensorEventListener ──────────────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_DETECTOR  -> handleStep()
            Sensor.TYPE_PROXIMITY      -> handleProximity(event)
            Sensor.TYPE_ACCELEROMETER  -> handleAccelerometer(event)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Not used.
    }

    // ─── Per-challenge handlers ───────────────────────────────────────────────

    /** Each TYPE_STEP_DETECTOR event represents exactly one step. */
    private fun handleStep() {
        if (currentCount >= target) return
        currentCount++
        onProgressUpdate(currentCount, target)
        if (currentCount >= target) {
            cancelSprintTimer()
            onCompleted()
        }
    }

    /**
     * Count one pushup each time the sensor transitions NEAR → FAR.
     * This represents the phone (resting on the floor near the chest)
     * moving away as the user pushes up.
     */
    private fun handleProximity(event: SensorEvent) {
        val isNear = event.values[0] < event.sensor.maximumRange
        if (lastProximityNear && !isNear) {
            val now = System.currentTimeMillis()
            if (lastPushupCountAtMs == 0L || now - lastPushupCountAtMs >= PUSHUP_MIN_INTERVAL_MS) {
                currentCount++
                lastPushupCountAtMs = now
                onProgressUpdate(currentCount, target)
                if (currentCount >= target) onCompleted()
            }
        }
        lastProximityNear = isNear
    }

    /**
     * Count one curl-up each time the phone's pitch rises above
     * CURL_UP_PITCH_THRESHOLD degrees (phone held to chest, tilting upward).
     *
     * Pitch = atan2(ay, sqrt(ax² + az²))
     */
    private fun handleAccelerometer(event: SensorEvent) {
        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]

        val pitchDeg = Math.toDegrees(
            atan2(ay.toDouble(), sqrt((ax * ax + az * az).toDouble()))
        )
        if (!hasBaselinePitch) {
            pendingBaselineMinPitchDeg = min(pendingBaselineMinPitchDeg, pitchDeg)
            if (pitchDeg <= CURL_UP_BASELINE_MAX_DEGREES) {
                baselinePitchDeg = pendingBaselineMinPitchDeg
                hasBaselinePitch = true
            } else {
                lastCurledUp = pitchDeg > CURL_UP_PITCH_THRESHOLD
                return
            }
        }

        val isCurledUp = pitchDeg > CURL_UP_PITCH_THRESHOLD
        if (isCurledUp && !lastCurledUp) {
            val now = System.currentTimeMillis()
            val movementDelta = pitchDeg - baselinePitchDeg
            if ((lastCurlCountAtMs == 0L || now - lastCurlCountAtMs >= CURL_UP_MIN_INTERVAL_MS) &&
                movementDelta >= CURL_UP_MIN_DELTA_DEGREES
            ) {
                currentCount++
                lastCurlCountAtMs = now
                onProgressUpdate(currentCount, target)
                if (currentCount >= target) onCompleted()
            }
        }
        if (!isCurledUp) {
            baselinePitchDeg = pitchDeg
        }
        lastCurledUp = isCurledUp
    }
}
