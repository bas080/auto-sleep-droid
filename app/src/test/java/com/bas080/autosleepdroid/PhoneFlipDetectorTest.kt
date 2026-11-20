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
    private var fakeClockMs = 0L

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
        val detector = PhoneFlipDetector(application, onFlip = {
            flipCount++
        })

        assertFalse(detector.isRegistered())
        detector.start()
        assertTrue(detector.isRegistered())

        detector.stop()
        assertFalse(detector.isRegistered())
    }

    private fun createDebouncedDetector(onFlip: () -> Unit, clockMillis: () -> Long): PhoneFlipDetector {
        return PhoneFlipDetector(application, onFlip, clockMillis).also { it.start() }
    }

    @Test
    fun testSingleFaceDownSampleDoesNotTriggerCallback() {
        var flipCount = 0
        val detector = createDebouncedDetector({ flipCount++ }, { fakeClockMs })

        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val event = createAccelerometerEvent(accelerometer, 0.0f, 0.0f, -9.81f)

        detector.onSensorChanged(event)
        assertEquals(0, flipCount)
    }

    @Test
    fun testSustainedFaceDownFlipTriggersCallbackOncePerFlip() {
        var flipCount = 0
        val detector = createDebouncedDetector({ flipCount++ }, { fakeClockMs })

        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val event = createAccelerometerEvent(accelerometer, 0.0f, 0.0f, -9.81f)

        fakeClockMs = 1_000L
        detector.onSensorChanged(event)
        assertEquals(0, flipCount)

        fakeClockMs = 1_100L
        detector.onSensorChanged(event)
        assertEquals(0, flipCount)

        fakeClockMs = 1_500L
        detector.onSensorChanged(event)
        assertEquals(1, flipCount)

        // Continued face down samples must not trigger additional callbacks
        fakeClockMs = 2_000L
        detector.onSensorChanged(event)
        assertEquals(1, flipCount)

        // Face up event re-arms the detector
        fakeClockMs = 2_100L
        val faceUpEvent = createAccelerometerEvent(accelerometer, 0.0f, 0.0f, 9.81f)
        detector.onSensorChanged(faceUpEvent)
        assertEquals(1, flipCount)

        // Second sustained face down flip triggers the callback again
        fakeClockMs = 2_200L
        detector.onSensorChanged(event)
        assertEquals(1, flipCount)

        fakeClockMs = 2_700L
        detector.onSensorChanged(event)
        assertEquals(2, flipCount)
    }

    @Test
    fun testUnstableAccelerometerSamplesDoNotTriggerCallback() {
        var flipCount = 0
        val detector = createDebouncedDetector({ flipCount++ }, { fakeClockMs })

        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        fakeClockMs = 1_000L
        detector.onSensorChanged(createAccelerometerEvent(accelerometer, 0.0f, 0.0f, -9.81f))

        fakeClockMs = 1_600L
        detector.onSensorChanged(createAccelerometerEvent(accelerometer, 0.0f, 0.0f, 0.0f))
        assertEquals(0, flipCount)

        fakeClockMs = 1_700L
        detector.onSensorChanged(createAccelerometerEvent(accelerometer, 0.0f, 0.0f, -9.81f))
        assertEquals(0, flipCount)

        fakeClockMs = 2_300L
        detector.onSensorChanged(createAccelerometerEvent(accelerometer, 20.0f, 0.0f, -9.81f))
        assertEquals(0, flipCount)

        fakeClockMs = 2_400L
        detector.onSensorChanged(createAccelerometerEvent(accelerometer, 0.0f, 0.0f, -9.81f))
        assertEquals(0, flipCount)

        fakeClockMs = 2_900L
        detector.onSensorChanged(createAccelerometerEvent(accelerometer, 0.0f, 0.0f, -4.0f))
        assertEquals(0, flipCount)
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

        // Simulate a sustained phone flip face down during fading
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val flipEvent = createAccelerometerEvent(accelerometer, 0.0f, 0.0f, -9.81f)
        detector.onSensorChanged(flipEvent)
        Thread.sleep(500)
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

        // Trigger a sustained phone flip while alarm is ringing
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val flipEvent = createAccelerometerEvent(accelerometer, 0.0f, 0.0f, -9.81f)
        detector.onSensorChanged(flipEvent)
        Thread.sleep(500)
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
