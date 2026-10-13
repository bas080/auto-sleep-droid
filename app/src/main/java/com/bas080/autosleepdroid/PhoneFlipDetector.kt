package com.bas080.autosleepdroid

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

private const val FACE_DOWN_THRESHOLD_Z = -7.0f
private const val FACE_UP_THRESHOLD_Z = 0.0f

class PhoneFlipDetector(
    context: Context,
    private val onFlip: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var isFaceDown = false
    private var isRegistered = false

    fun start() {
        if (isRegistered || sensorManager == null || accelerometer == null) return
        isFaceDown = false
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        isRegistered = true
    }

    fun stop() {
        if (!isRegistered || sensorManager == null) return
        sensorManager.unregisterListener(this)
        isRegistered = false
        isFaceDown = false
    }

    fun isRegistered(): Boolean = isRegistered

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val z = event.values[2]
        if (!isFaceDown && z < FACE_DOWN_THRESHOLD_Z) {
            isFaceDown = true
            onFlip()
        } else if (isFaceDown && z > FACE_UP_THRESHOLD_Z) {
            isFaceDown = false
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
