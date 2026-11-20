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
        }, now = { fakeClockMs })

        assertFalse(detector.isRegistered())
        detector.start()
        assertTrue(detector.isRegistered())

        detector.stop()
        assertFalse(detector.isRegistered())
    }

    private fun createDebouncedDetector(onFlip: () -> Unit): PhoneFlipDetector {
        return PhoneFlipDetector(application, onFlip = onFlip, now = { fakeClockMs }).also { it.start() }
    }

    @Test
    fun testFlipTriggersCallbackOncePerFlip() {
        var flipCount = 0
        val detector = createDebouncedDetector { flipCount++ }

        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val faceUpEvent = createAccelerometerEvent(accelerometer, 0.0f, 0.0f, 9.81f)
        val faceDownEvent = createAccelerometerEvent(accelerometer, 0.0f, 0.0f, -9.81f)

        // Settle in the starting orientation: no flip
        fakeClockMs = 1_000L
        detector.onSensorChanged(faceUpEvent)
        assertEquals(0, flipCount)

        // Transition to face down: pending until the hold elapses
        fakeClockMs = 1_100L
        detector.onSensorChanged(faceDownEvent)
        assertEquals(0, flipCount)

        fakeClockMs = 1_300L
        detector.onSensorChanged(faceDownEvent)
        assertEquals(0, flipCount)

        fakeClockMs = 1_600L
        detector.onSensorChanged(faceDownEvent)
        assertEquals(1, flipCount)

        // Staying in the new orientation must not fire again
        fakeClockMs = 2_100L
        detector.onSensorChanged(faceDownEvent)
        assertEquals(1, flipCount)
    }

    @Test
    fun testFlipWorksInAnyDirection() {
        var flipCount = 0
        val detector = createDebouncedDetector { flipCount++ }

        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val faceDownEvent = createAccelerometerEvent(accelerometer, 0.0f, 0.0f, -9.81f)
        val onLeftSideEvent = createAccelerometerEvent(accelerometer, -9.81f, 0.0f, 0.0f)

        fakeClockMs = 1_000L
        detector.onSensorChanged(faceDownEvent)
        assertEquals(0, flipCount)

        fakeClockMs = 1_100L
        detector.onSensorChanged(onLeftSideEvent)
        assertEquals(0, flipCount)

        fakeClockMs = 1_600L
        detector.onSensorChanged(onLeftSideEvent)
        assertEquals(1, flipCount)

        // Flipping back also counts as a flip
        fakeClockMs = 1_700L
        detector.onSensorChanged(faceDownEvent)
        assertEquals(1, flipCount)

        fakeClockMs = 2_200L
        detector.onSensorChanged(faceDownEvent)
        assertEquals(2, flipCount)
    }

    @Test
    fun testUnstableSamplesDoNotTriggerCallback() {
        var flipCount = 0
        val detector = createDebouncedDetector { flipCount++ }

        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val faceUpEvent = createAccelerometerEvent(accelerometer, 0.0f, 0.0f, 9.81f)
        val inMotionEvent = createAccelerometerEvent(accelerometer, 12.0f, -4.0f, 6.0f)
        val faceDownEvent = createAccelerometerEvent(accelerometer, 0.0f, 0.0f, -9.81f)

        fakeClockMs = 1_000L
        detector.onSensorChanged(faceUpEvent)
        assertEquals(0, flipCount)

        fakeClockMs = 1_100L
        detector.onSensorChanged(inMotionEvent)
        assertEquals(0, flipCount)

        fakeClockMs = 1_200L
        detector.onSensorChanged(faceDownEvent)
        assertEquals(0, flipCount)

        fakeClockMs = 1_500L
        detector.onSensorChanged(faceDownEvent)
        assertEquals(0, flipCount)

        fakeClockMs = 1_700L
        detector.onSensorChanged(faceDownEvent)
        assertEquals(1, flipCount)
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

        // Simulate the phone resting face up being flipped face down and held
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        detector.onSensorChanged(createAccelerometerEvent(accelerometer, 0.0f, 0.0f, 9.81f))
        detector.onSensorChanged(createAccelerometerEvent(accelerometer, 0.0f, 0.0f, -9.81f))
        Thread.sleep(500)
        detector.onSensorChanged(createAccelerometerEvent(accelerometer, 0.0f, 0.0f, -9.81f))

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

        // The phone lies face down when the alarm starts, the user lifts it and flips it over
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        detector.onSensorChanged(createAccelerometerEvent(accelerometer, 0.0f, 0.0f, -9.81f))
        detector.onSensorChanged(createAccelerometerEvent(accelerometer, 0.0f, 0.0f, 9.81f))
        detector.onSensorChanged(createAccelerometerEvent(accelerometer, 0.0f, 0.0f, -9.81f))
        Thread.sleep(500)
        detector.onSensorChanged(createAccelerometerEvent(accelerometer, 0.0f, 0.0f, -9.81f))

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
