package com.bas080.autosleepdroid

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.sqrt

private const val SETTLE_HOLD_MS = 400L
private const val GRAVITY_TOLERANCE = 1.5f

class PhoneFlipDetector(
    context: Context,
    private val onFlip: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis
) : SensorEventListener {

    private enum class Orientation { FACE_UP, FACE_DOWN, LEFT, RIGHT, TOP, BOTTOM }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var settledOrientation: Orientation? = null
    private var candidateOrientation: Orientation? = null
    private var candidateSinceMs = 0L
    private var leftSettled = false
    private var isRegistered = false

    fun start() {
        if (isRegistered || sensorManager == null || accelerometer == null) return
        settledOrientation = null
        candidateOrientation = null
        leftSettled = false
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        isRegistered = true
    }

    fun stop() {
        if (!isRegistered || sensorManager == null) return
        sensorManager.unregisterListener(this)
        isRegistered = false
        settledOrientation = null
        candidateOrientation = null
        leftSettled = false
    }

    fun isRegistered(): Boolean = isRegistered

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)
        val isStationary = abs(magnitude - SensorManager.STANDARD_GRAVITY) <= GRAVITY_TOLERANCE
        val orientation = if (isStationary) orientationFor(x, y, z) else null
        onSample(orientation)
    }

    private fun onSample(orientation: Orientation?) {
        when {
            orientation == null -> candidateOrientation = null
            settledOrientation == null -> {
                settledOrientation = orientation
                candidateOrientation = null
                leftSettled = false
            }
            else -> trackStable(orientation)
        }
    }

    private fun trackStable(orientation: Orientation) {
        if (orientation != settledOrientation) {
            leftSettled = true
        }
        if (leftSettled && candidateOrientation == orientation && heldLongEnough()) {
            settledOrientation = orientation
            candidateOrientation = null
            leftSettled = false
            onFlip()
        } else if (candidateOrientation != orientation) {
            candidateOrientation = orientation
            candidateSinceMs = now()
        }
    }

    private fun heldLongEnough(): Boolean = now() - candidateSinceMs >= SETTLE_HOLD_MS

    private fun orientationFor(x: Float, y: Float, z: Float): Orientation {
        val absX = abs(x)
        val absY = abs(y)
        val absZ = abs(z)
        return when {
            absZ >= absX && absZ >= absY -> if (z > 0) Orientation.FACE_UP else Orientation.FACE_DOWN
            absX >= absY -> if (x > 0) Orientation.RIGHT else Orientation.LEFT
            else -> if (y > 0) Orientation.TOP else Orientation.BOTTOM
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
