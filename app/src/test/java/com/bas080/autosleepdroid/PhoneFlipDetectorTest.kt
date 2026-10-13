package com.bas080.autosleepdroid

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhoneFlipDetectorTest {

    private lateinit var application: Application
    private lateinit var sensorManager: SensorManager
    private lateinit var shadowSensorManager: ShadowSensorManager

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        shadowSensorManager = Shadows.shadowOf(sensorManager)
        val accelerometer = ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER)
        shadowSensorManager.addSensor(accelerometer)
    }

    @Test
    fun testStartAndStopRegistration() {
        var flipCount = 0
        val detector = PhoneFlipDetector(application) {
            flipCount++
        }

        assertFalse(detector.isRegistered())
        detector.start()
        assertTrue(detector.isRegistered())

        detector.stop()
        assertFalse(detector.isRegistered())
    }

    @Test
    fun testFaceDownFlipTriggersCallbackAndDebounces() {
        var flipCount = 0
        val detector = PhoneFlipDetector(application) {
            flipCount++
        }

        detector.start()

        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val event = createAccelerometerEvent(accelerometer, 0.0f, 0.0f, -9.81f)

        detector.onSensorChanged(event)
        assertEquals(1, flipCount)

        // Second face down event while already face down should not trigger callback
        detector.onSensorChanged(event)
        assertEquals(1, flipCount)

        // Face up event resets state
        val faceUpEvent = createAccelerometerEvent(accelerometer, 0.0f, 0.0f, 9.81f)
        detector.onSensorChanged(faceUpEvent)
        assertEquals(1, flipCount)

        // Second face down event triggers callback again
        detector.onSensorChanged(event)
        assertEquals(2, flipCount)
    }

    @Test
    fun testMainServiceSensorRegistrationAndFlipActions() {
        val serviceController = Robolectric.buildService(MainService::class.java)
        val service = serviceController.create().get()

        val detector = service.phoneFlipDetector
        assertTrue(detector != null)

        // When state is OFF or WAITING, sensor is not registered
        assertFalse(detector!!.isRegistered())

        // Begin fade out
        service.beginFadeOut(10)
        assertTrue(service.isFading)
        assertTrue(detector.isRegistered())

        // Simulate phone flip face down during fading
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val flipEvent = createAccelerometerEvent(accelerometer, 0.0f, 0.0f, -9.81f)
        detector.onSensorChanged(flipEvent)

        // Verify timer reset back to ACTIVE
        assertTrue(service.isActive)
        assertFalse(service.isFading)
        assertFalse(detector.isRegistered())
    }

    @Test
    fun testMainServiceWakeAlarmRingingFlipSnoozesAlarm() {
        val prefs = application.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("wake_up_goal_enabled", true).commit()

        val serviceController = Robolectric.buildService(MainService::class.java)
        val service = serviceController.create().get()

        val intent = android.content.Intent(application, MainService::class.java).apply {
            action = MainService.ACTION_WAKEUP_ALARM_EXPIRY
        }
        service.onStartCommand(intent, 0, 1)

        val detector = service.phoneFlipDetector
        assertTrue(detector != null)
        assertTrue(detector!!.isRegistered())
        assertTrue(prefs.getBoolean(PreferenceKeys.KEY_WAKEUP_ALARM_RINGING, false))

        // Trigger phone flip while alarm is ringing
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val flipEvent = createAccelerometerEvent(accelerometer, 0.0f, 0.0f, -9.81f)
        detector.onSensorChanged(flipEvent)

        // Alarm should now be snoozed and sensor listener unregistered
        assertFalse(prefs.getBoolean(PreferenceKeys.KEY_WAKEUP_ALARM_RINGING, false))
        assertTrue(prefs.getBoolean(PreferenceKeys.KEY_WAKEUP_ALARM_SNOOZED, false))
        assertFalse(detector.isRegistered())
    }

    private fun createAccelerometerEvent(sensor: Sensor?, x: Float, y: Float, z: Float): SensorEvent {
        val constructor = SensorEvent::class.java.getDeclaredConstructor(Int::class.java)
        constructor.isAccessible = true
        val event = constructor.newInstance(3)
        event.sensor = sensor
        event.values[0] = x
        event.values[1] = y
        event.values[2] = z
        return event
    }
}
