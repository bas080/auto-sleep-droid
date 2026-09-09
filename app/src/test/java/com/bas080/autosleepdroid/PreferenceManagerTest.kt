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
    fun testGetSessionPhasePhases() {
        val minSleepDurationMs = 8 * 3600_000L
        val wakeTime = 1_000_000_000_000L

        val windowStart = wakeTime - (minSleepDurationMs * 1.2).toLong()
        val preAlarmStart = wakeTime - (minSleepDurationMs * 0.5).toLong()

        assertEquals(SessionPhase.IDLE, getSessionPhase(windowStart - 1000L, wakeTime, minSleepDurationMs, false))
        assertEquals(SessionPhase.IDLE, getSessionPhase(wakeTime + 1000L, wakeTime, minSleepDurationMs, false))

        assertEquals(SessionPhase.INITIATION_AND_ACTIVE_SLEEP, getSessionPhase(windowStart, wakeTime, minSleepDurationMs, true))
        assertEquals(SessionPhase.INITIATION_AND_ACTIVE_SLEEP, getSessionPhase(windowStart + 1000L, wakeTime, minSleepDurationMs, true))

        assertEquals(SessionPhase.PRE_ALARM_WINDOW, getSessionPhase(preAlarmStart, wakeTime, minSleepDurationMs, true))
        assertEquals(SessionPhase.PRE_ALARM_WINDOW, getSessionPhase(preAlarmStart + 1000L, wakeTime, minSleepDurationMs, true))

        assertEquals(SessionPhase.ALARM, getSessionPhase(wakeTime, wakeTime, minSleepDurationMs, true))
        assertEquals(SessionPhase.ALARM, getSessionPhase(wakeTime + 5000L, wakeTime, minSleepDurationMs, true))
    }

    @Test
    fun testPreferenceComputationsGetSessionPhaseAndIsNapAllowed() {
        val phase = preferenceManager.getComputed(PreferenceComputations.GET_SESSION_PHASE)
        assertEquals(SessionPhase.IDLE, phase)

        val napAllowed = preferenceManager.getComputed(PreferenceComputations.IS_NAP_ALLOWED)
        assertTrue(napAllowed!!)
    }

    @Test
    fun testPreferenceComputationsSessionPhaseTransitionsAcrossPreferenceStates() {
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = now

        // Default state: no session ongoing, wake alarm disabled -> IDLE
        assertEquals(SessionPhase.IDLE, preferenceManager.getComputed(PreferenceComputations.GET_SESSION_PHASE))
        assertTrue("Nap should be allowed in IDLE phase", preferenceManager.getComputed(PreferenceComputations.IS_NAP_ALLOWED)!!)
        assertFalse("Awake action should be hidden in IDLE phase", preferenceManager.getComputed(PreferenceComputations.SHOULD_SHOW_AWAKE_ACTION)!!)

        // Enable wake alarm and set current wake time 4 hours in the future
        cal.add(java.util.Calendar.HOUR_OF_DAY, 4)
        val wakeHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val wakeMin = cal.get(java.util.Calendar.MINUTE)

        rawPreferences.edit()
            .putBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, true)
            .putInt(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR, wakeHour)
            .putInt(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE, wakeMin)
            .putInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, wakeHour)
            .putInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, wakeMin)
            .putInt(PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES, 480) // 8 hours min sleep -> 1.2x = 9.6h window, 0.5x = 4h pre-alarm
            .putLong(PreferenceKeys.KEY_SLEEP_START_TIME_MS, now - 3600_000L) // active session started 1h ago
            .commit()

        // 4 hours before wake time with 8h min sleep is inside INITIATION_AND_ACTIVE_SLEEP window (windowStart is now - 5.6h)
        assertEquals(SessionPhase.INITIATION_AND_ACTIVE_SLEEP, preferenceManager.getComputed(PreferenceComputations.GET_SESSION_PHASE))
        assertFalse("Nap should not be allowed during active sleep phase", preferenceManager.getComputed(PreferenceComputations.IS_NAP_ALLOWED)!!)
        assertFalse("Awake action should be hidden during active sleep phase", preferenceManager.getComputed(PreferenceComputations.SHOULD_SHOW_AWAKE_ACTION)!!)

        // Set sleepStartTime 6 hours ago so minimum sleep safeguard (7.6h) does not push wake time beyond 2h in future
        cal.timeInMillis = now
        cal.add(java.util.Calendar.HOUR_OF_DAY, 2)
        val preAlarmHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val preAlarmMin = cal.get(java.util.Calendar.MINUTE)

        rawPreferences.edit()
            .putLong(PreferenceKeys.KEY_SLEEP_START_TIME_MS, now - 6 * 3600_000L)
            .putInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, preAlarmHour)
            .putInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, preAlarmMin)
            .commit()

        assertEquals(SessionPhase.PRE_ALARM_WINDOW, preferenceManager.getComputed(PreferenceComputations.GET_SESSION_PHASE))
        assertFalse("Nap should not be allowed in pre-alarm window", preferenceManager.getComputed(PreferenceComputations.IS_NAP_ALLOWED)!!)
        assertTrue("Awake action should be shown in pre-alarm window", preferenceManager.getComputed(PreferenceComputations.SHOULD_SHOW_AWAKE_ACTION)!!)

        // Move wake time into past (alarm ringing/phase), with sleepStartTime 10h ago so min sleep safeguard is fully satisfied
        cal.timeInMillis = now
        cal.add(java.util.Calendar.MINUTE, -10)
        val alarmHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val alarmMin = cal.get(java.util.Calendar.MINUTE)

        rawPreferences.edit()
            .putLong(PreferenceKeys.KEY_SLEEP_START_TIME_MS, now - 10 * 3600_000L)
            .putInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, alarmHour)
            .putInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, alarmMin)
            .commit()

        val actualPhase3 = preferenceManager.getComputed(PreferenceComputations.GET_SESSION_PHASE)
        assertEquals(SessionPhase.ALARM, actualPhase3)
        assertFalse("Nap should not be allowed during alarm phase", preferenceManager.getComputed(PreferenceComputations.IS_NAP_ALLOWED)!!)
        assertTrue("Awake action should be shown during alarm phase", preferenceManager.getComputed(PreferenceComputations.SHOULD_SHOW_AWAKE_ACTION)!!)

        // Clear ongoing sleep session -> returns to IDLE phase even after wake time
        rawPreferences.edit().remove(PreferenceKeys.KEY_SLEEP_START_TIME_MS).commit()

        assertEquals(SessionPhase.IDLE, preferenceManager.getComputed(PreferenceComputations.GET_SESSION_PHASE))
        assertTrue("Nap should be allowed in IDLE phase after clearing session", preferenceManager.getComputed(PreferenceComputations.IS_NAP_ALLOWED)!!)

        // Activate nap
        rawPreferences.edit().putLong(PreferenceKeys.KEY_NAP_ALARM_ENDS_AT, now + 1200_000L).commit()
        assertTrue("Nap should be active", preferenceManager.getComputed(PreferenceComputations.IS_NAP_ACTIVE)!!)
        assertTrue("Nap allowed should return true when nap is active", preferenceManager.getComputed(PreferenceComputations.IS_NAP_ALLOWED)!!)
        assertTrue("Awake action should return true when nap is active", preferenceManager.getComputed(PreferenceComputations.SHOULD_SHOW_AWAKE_ACTION)!!)
    }

    @Test
    fun testSessionPhaseIsAlarmWhenWakeupAlarmIsRingingOrSnoozed() {
        rawPreferences.edit().putBoolean(PreferenceKeys.KEY_WAKEUP_ALARM_RINGING, true).commit()
        assertEquals(SessionPhase.ALARM, preferenceManager.getComputed(PreferenceComputations.GET_SESSION_PHASE))
        assertTrue("Awake action should be shown when wakeup alarm is ringing", preferenceManager.getComputed(PreferenceComputations.SHOULD_SHOW_AWAKE_ACTION)!!)

        rawPreferences.edit().remove(PreferenceKeys.KEY_WAKEUP_ALARM_RINGING).putBoolean(PreferenceKeys.KEY_WAKEUP_ALARM_SNOOZED, true).commit()
        assertEquals(SessionPhase.ALARM, preferenceManager.getComputed(PreferenceComputations.GET_SESSION_PHASE))
        assertTrue("Awake action should be shown when wakeup alarm is snoozed", preferenceManager.getComputed(PreferenceComputations.SHOULD_SHOW_AWAKE_ACTION)!!)
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
