package com.bas080.autosleepdroid

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreferenceManagerTest {

    private lateinit var context: Context
    private lateinit var rawPreferences: SharedPreferences
    private lateinit var preferenceManager: PreferenceManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        rawPreferences = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        rawPreferences.edit().clear().commit()
        preferenceManager = PreferenceManager(rawPreferences)
    }

    @Test
    fun testKeySpecificListenerFiresOnlyForTargetKey() {
        val targetKeyFired = AtomicBoolean(false)

        preferenceManager.registerListener("target_key") { targetKeyFired.set(true) }

        rawPreferences.edit().putBoolean("unrelated_key", true).commit()
        assertFalse("Listener should not fire for unrelated key", targetKeyFired.get())

        rawPreferences.edit().putBoolean("target_key", true).commit()
        assertTrue("Listener must fire when target_key changes", targetKeyFired.get())
    }

    @Test
    fun testUnregisterListenerStopsCallbacks() {
        val keyFired = AtomicBoolean(false)
        val listener = PreferenceManager.OnPreferenceChangeListener { keyFired.set(true) }

        preferenceManager.registerListener("test_key", listener)
        preferenceManager.unregisterListener("test_key", listener)

        rawPreferences.edit().putBoolean("test_key", true).commit()
        assertFalse("Unregistered listener must not receive callbacks", keyFired.get())
    }

    @Test
    fun testWriteOperationsAndGetters() {
        val listenerFired = AtomicBoolean(false)
        preferenceManager.registerListener("test_key") { listenerFired.set(true) }

        preferenceManager.edit().putBoolean("test_key", true).apply()

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertTrue("Write must trigger preference listener callback", listenerFired.get())
        assertTrue("Getter must return written preference value", preferenceManager.getBoolean("test_key", false))
    }

    @Test
    fun testAutoTrackingComputedValueMemoizationAndInvalidation() {
        val computeCount = AtomicInteger(0)

        rawPreferences.edit().putInt("dep_key_1", 10).commit()

        val result1 = preferenceManager.getComputed("testComp") { getter ->
            computeCount.incrementAndGet()
            getter.getInt("dep_key_1", 0) * 2
        }

        assertEquals(20, result1)
        assertEquals(1, computeCount.get())

        val result2 = preferenceManager.getComputed("testComp") { getter ->
            computeCount.incrementAndGet()
            getter.getInt("dep_key_1", 0) * 2
        }

        assertEquals(20, result2)
        assertEquals("Compute count should remain 1 due to memoization", 1, computeCount.get())

        rawPreferences.edit().putBoolean("unrelated_key", true).commit()

        val result3 = preferenceManager.getComputed("testComp") { getter ->
            computeCount.incrementAndGet()
            getter.getInt("dep_key_1", 0) * 2
        }

        assertEquals(20, result3)
        assertEquals("Compute count should remain 1 after unrelated key change", 1, computeCount.get())

        rawPreferences.edit().putInt("dep_key_1", 15).commit()

        val result4 = preferenceManager.getComputed("testComp") { getter ->
            computeCount.incrementAndGet()
            getter.getInt("dep_key_1", 0) * 2
        }

        assertEquals(30, result4)
        assertEquals("Compute count should increment to 2 after tracked preference update", 2, computeCount.get())
    }

    @Test
    fun testFunctionKeyedComputedValueMemoization() {
        val computeCount = AtomicInteger(0)
        rawPreferences.edit().putInt(PreferenceKeys.KEY_DURATION_MINUTES, 45).commit()

        val comp = PreferenceManager.ComputedValue { getter ->
            computeCount.incrementAndGet()
            DurationUtils.formatDurationString(getter.getInt(PreferenceKeys.KEY_DURATION_MINUTES, 0))
        }

        val val1 = preferenceManager.getComputed(comp)
        assertEquals("45m", val1)
        assertEquals(1, computeCount.get())

        val val2 = preferenceManager.getComputed(comp)
        assertEquals("45m", val2)
        assertEquals("Compute count should remain 1 when using function reference key", 1, computeCount.get())

        rawPreferences.edit().putInt(PreferenceKeys.KEY_DURATION_MINUTES, 60).commit()

        val val3 = preferenceManager.getComputed(comp)
        assertEquals("1h", val3)
        assertEquals("Compute count should increment to 2 after tracked preference update", 2, computeCount.get())
    }

    @Test
    fun testPreferenceComputationsIsWakeAlarmEnabled() {
        assertFalse(preferenceManager.getComputed(PreferenceComputations.IS_WAKE_ALARM_ENABLED)!!)

        rawPreferences.edit().putBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, true).commit()
        assertTrue(preferenceManager.getComputed(PreferenceComputations.IS_WAKE_ALARM_ENABLED)!!)
    }

    @Test
    fun testWatchEffectInitialAndReactiveExecutionAndDispose() {
        val runCount = AtomicInteger(0)
        val lastValue = AtomicInteger(0)

        rawPreferences.edit().putInt("watched_key", 5).commit()

        val handle = preferenceManager.watchEffect { getter ->
            runCount.incrementAndGet()
            lastValue.set(getter.getInt("watched_key", 0))
        }

        assertEquals("Effect must run once immediately upon watchEffect", 1, runCount.get())
        assertEquals(5, lastValue.get())

        rawPreferences.edit().putBoolean("unrelated_key", true).commit()
        assertEquals("Unrelated key change should not trigger watchEffect", 1, runCount.get())

        rawPreferences.edit().putInt("watched_key", 12).commit()
        assertEquals("Watched key change must re-trigger watchEffect", 2, runCount.get())
        assertEquals(12, lastValue.get())

        handle.dispose()
        rawPreferences.edit().putInt("watched_key", 20).commit()
        assertEquals("Disposed watchEffect must not re-run on preference change", 2, runCount.get())
    }
}
