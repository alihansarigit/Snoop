package io.github.alihansarigit.snoop.internal

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Detects a deliberate device shake from accelerometer samples and invokes [onShake].
 *
 * Requires no runtime permission. Events are delivered on the main thread (the
 * default when no [android.os.Handler] is supplied to `registerListener`), so
 * [onShake] is safe to touch Compose state directly.
 *
 * A shake must cross [SHAKE_THRESHOLD_G] at least [REQUIRED_SHAKES] times, each peak
 * de-bounced by [MIN_INTERVAL_MS] and reset if they fall outside [RESET_WINDOW_MS] —
 * so a firm ~1 s wobble triggers, but taps, drops and jostling do not.
 */
internal class ShakeDetector(private val onShake: () -> Unit) : SensorEventListener {

    private var lastShakeAt = 0L
    private var shakeCount = 0

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val gForce = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        if (gForce < SHAKE_THRESHOLD_G) return

        val now = System.currentTimeMillis()
        if (now - lastShakeAt < MIN_INTERVAL_MS) return           // same peak — ignore
        if (now - lastShakeAt > RESET_WINDOW_MS) shakeCount = 0    // stale — start over
        lastShakeAt = now

        if (++shakeCount >= REQUIRED_SHAKES) {
            shakeCount = 0
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val SHAKE_THRESHOLD_G = 2.7f
        const val MIN_INTERVAL_MS = 400L
        const val RESET_WINDOW_MS = 3_000L
        const val REQUIRED_SHAKES = 2
    }
}
