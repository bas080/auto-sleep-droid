package com.bas080.autosleepdroid

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.sqrt

private const val FACE_UP_THRESHOLD_Z = 0.0f
private const val FACE_DOWN_TILT_COS = 0.5f
private const val GRAVITY_TOLERANCE = 1.5f
private const val FACE_DOWN_HOLD_MS = 400L

class PhoneFlipDetector(
    context: Context,
    private val onFlip: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis
) : SensorEventListener {

    private enum class State { ARMED, PENDING, FIRED }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var state = State.ARMED
    private var faceDownStartedAtMs = 0L
    private var isRegistered = false

    fun start() {
        if (isRegistered || sensorManager == null || accelerometer == null) return
        state = State.ARMED
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        isRegistered = true
    }

    fun stop() {
        if (!isRegistered || sensorManager == null) return
        sensorManager.unregisterListener(this)
        isRegistered = false
        state = State.ARMED
    }

    fun isRegistered(): Boolean = isRegistered

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)
        val isStationary = abs(magnitude - SensorManager.STANDARD_GRAVITY) <= GRAVITY_TOLERANCE
        val isFaceDownNow = isStationary && z <= -SensorManager.STANDARD_GRAVITY * FACE_DOWN_TILT_COS

        if (z > FACE_UP_THRESHOLD_Z) {
            state = State.ARMED
        }

        when (state) {
            State.ARMED -> if (isFaceDownNow) {
                state = State.PENDING
                faceDownStartedAtMs = now()
            }
            State.PENDING -> {
                if (!isFaceDownNow) {
                    state = State.ARMED
                } else if (now() - faceDownStartedAtMs >= FACE_DOWN_HOLD_MS) {
                    state = State.FIRED
                    onFlip()
                }
            }
            State.FIRED -> Unit
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
