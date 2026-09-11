package com.bas080.autosleepdroid

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class HealthConnectManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    @Test
    fun testIsHealthConnectAvailable_DoesNotCrash() {
        val available = HealthConnectManager.isHealthConnectAvailable(context)
        assertFalse(available)
    }

    @Test
    fun testOpenHealthConnectPermissions_LaunchesManageHealthPermissionsIntentWithPackageName() {
        HealthConnectManager.setClientForTesting(null, true)
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()

        HealthConnectManager.openHealthConnectPermissions(activity)

        val startedIntent = Shadows.shadowOf(activity).nextStartedActivity
        assertNotNull(startedIntent)
        assertEquals("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS", startedIntent?.action)
        assertEquals(activity.packageName, startedIntent?.getStringExtra(Intent.EXTRA_PACKAGE_NAME))
        assertTrue("Intent must have FLAG_ACTIVITY_NEW_TASK set", (startedIntent!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)

        HealthConnectManager.setClientForTesting(null, null)
    }

    @Test
    fun testWriteSleepSession_InvalidTimestamps_FailsValidation() {
        val successRef = AtomicBoolean(true)
        val errorRef = AtomicReference<String>()

        HealthConnectManager.writeSleepSession(context, 0L, 1000L) { success, error ->
            successRef.set(success)
            errorRef.set(error)
        }

        assertFalse(successRef.get())
        assertNotNull(errorRef.get())
        assertTrue(errorRef.get()!!.contains("Invalid timestamps"))
    }

    @Test
    fun testWriteSleepSession_EndTimeBeforeStartTime_FailsValidation() {
        val successRef = AtomicBoolean(true)
        val errorRef = AtomicReference<String>()

        val now = System.currentTimeMillis()
        HealthConnectManager.writeSleepSession(context, now, now - 1000L) { success, error ->
            successRef.set(success)
            errorRef.set(error)
        }

        assertFalse(successRef.get())
        assertNotNull(errorRef.get())
        assertTrue(errorRef.get()!!.contains("Invalid timestamps"))
    }

    @Test
    fun testWriteSleepSession_DurationTooShort_FailsValidation() {
        val successRef = AtomicBoolean(true)
        val errorRef = AtomicReference<String>()

        val now = System.currentTimeMillis()
        HealthConnectManager.writeSleepSession(context, now, now + 30_000L) { success, error ->
            successRef.set(success)
            errorRef.set(error)
        }

        assertFalse(successRef.get())
        assertNotNull(errorRef.get())
        assertTrue(errorRef.get()!!.contains("Invalid sleep duration"))
    }

    @Test
    fun testWriteSleepSession_DurationTooLong_FailsValidation() {
        val successRef = AtomicBoolean(true)
        val errorRef = AtomicReference<String>()

        val now = System.currentTimeMillis()
        HealthConnectManager.writeSleepSession(context, now, now + 25 * 3600_000L) { success, error ->
            successRef.set(success)
            errorRef.set(error)
        }

        assertFalse(successRef.get())
        assertNotNull(errorRef.get())
        assertTrue(errorRef.get()!!.contains("Invalid sleep duration"))
    }

    @Test
    fun testHealthConnectPreferencePersistence() {
        val prefs = context.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE)
        assertFalse(prefs.getBoolean("health_connect_enabled", false))

        prefs.edit().putBoolean("health_connect_enabled", true).apply()
        assertTrue(prefs.getBoolean("health_connect_enabled", false))
    }

    @Test
    fun testNapAndSleepSessionStartKeyPersistence() {
        val prefs = context.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        prefs.edit().putLong("nap_start_time_ms", now).apply()
        assertEquals(now, prefs.getLong("nap_start_time_ms", 0L))

        prefs.edit().remove("nap_start_time_ms").apply()
        assertEquals(0L, prefs.getLong("nap_start_time_ms", 0L))
    }
}
