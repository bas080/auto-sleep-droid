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
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreferenceComputationsTest {

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
    fun testPreferenceComputationsGetSessionPhase() {
        val phase = preferenceManager.getComputed(PreferenceComputations.GET_SESSION_PHASE)
        assertEquals(SessionPhase.IDLE, phase)
    }

    @Test
    fun testPreferenceComputationsSessionPhaseTransitionsAcrossPreferenceStates() {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.timeInMillis = now

        assertEquals(SessionPhase.IDLE, preferenceManager.getComputed(PreferenceComputations.GET_SESSION_PHASE))
        assertFalse("Awake action should be hidden when alarm disabled", preferenceManager.getComputed(PreferenceComputations.SHOULD_SHOW_AWAKE_ACTION)!!)

        cal.add(Calendar.HOUR_OF_DAY, 4)
        val wakeHour = cal.get(Calendar.HOUR_OF_DAY)
        val wakeMin = cal.get(Calendar.MINUTE)

        rawPreferences.edit()
            .putBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, true)
            .putInt(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR, wakeHour)
            .putInt(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE, wakeMin)
            .putInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, wakeHour)
            .putInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, wakeMin)
            .putInt(PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES, 480)
            .putLong(PreferenceKeys.KEY_SLEEP_START_TIME_MS, now - 3600_000L)
            .commit()

        assertEquals(SessionPhase.INITIATION_AND_ACTIVE_SLEEP, preferenceManager.getComputed(PreferenceComputations.GET_SESSION_PHASE))
        assertTrue("Awake action should be shown within minSleep/2 range of wake time", preferenceManager.getComputed(PreferenceComputations.SHOULD_SHOW_AWAKE_ACTION)!!)

        cal.timeInMillis = now
        cal.add(Calendar.HOUR_OF_DAY, 2)
        val preAlarmHour = cal.get(Calendar.HOUR_OF_DAY)
        val preAlarmMin = cal.get(Calendar.MINUTE)

        rawPreferences.edit()
            .putLong(PreferenceKeys.KEY_SLEEP_START_TIME_MS, now - 6 * 3600_000L)
            .putInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, preAlarmHour)
            .putInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, preAlarmMin)
            .commit()

        assertEquals(SessionPhase.PRE_ALARM_WINDOW, preferenceManager.getComputed(PreferenceComputations.GET_SESSION_PHASE))
        assertTrue("Awake action should be shown in pre-alarm window", preferenceManager.getComputed(PreferenceComputations.SHOULD_SHOW_AWAKE_ACTION)!!)

        cal.timeInMillis = now
        cal.add(Calendar.MINUTE, -10)
        val alarmHour = cal.get(Calendar.HOUR_OF_DAY)
        val alarmMin = cal.get(Calendar.MINUTE)

        rawPreferences.edit()
            .putLong(PreferenceKeys.KEY_SLEEP_START_TIME_MS, now - 10 * 3600_000L)
            .putInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, alarmHour)
            .putInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, alarmMin)
            .commit()

        val actualPhase3 = preferenceManager.getComputed(PreferenceComputations.GET_SESSION_PHASE)
        assertEquals(SessionPhase.ALARM, actualPhase3)
        assertTrue("Awake action should be shown during alarm phase", preferenceManager.getComputed(PreferenceComputations.SHOULD_SHOW_AWAKE_ACTION)!!)

        rawPreferences.edit().remove(PreferenceKeys.KEY_SLEEP_START_TIME_MS).commit()

        assertEquals(SessionPhase.IDLE, preferenceManager.getComputed(PreferenceComputations.GET_SESSION_PHASE))
    }

    @Test
    fun testSessionPhaseIsAlarmWhenWakeupAlarmIsRingingOrSnoozed() {
        rawPreferences.edit()
            .putBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, true)
            .putBoolean(PreferenceKeys.KEY_WAKEUP_ALARM_RINGING, true)
            .commit()
        assertEquals(SessionPhase.ALARM, preferenceManager.getComputed(PreferenceComputations.GET_SESSION_PHASE))
        assertTrue("Awake action should be shown when wakeup alarm is ringing", preferenceManager.getComputed(PreferenceComputations.SHOULD_SHOW_AWAKE_ACTION)!!)

        rawPreferences.edit().remove(PreferenceKeys.KEY_WAKEUP_ALARM_RINGING).putBoolean(PreferenceKeys.KEY_WAKEUP_ALARM_SNOOZED, true).commit()
        assertEquals(SessionPhase.ALARM, preferenceManager.getComputed(PreferenceComputations.GET_SESSION_PHASE))
        assertTrue("Awake action should be shown when wakeup alarm is snoozed", preferenceManager.getComputed(PreferenceComputations.SHOULD_SHOW_AWAKE_ACTION)!!)
    }

    @Test
    fun testShouldShowAwakeActionReturnsFalseWhenLastAwakeTimeMsIsRecorded() {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        val wakeHour = cal.get(Calendar.HOUR_OF_DAY)
        val wakeMin = cal.get(Calendar.MINUTE)

        rawPreferences.edit()
            .putBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, true)
            .putInt(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR, wakeHour)
            .putInt(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE, wakeMin)
            .putInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, wakeHour)
            .putInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, wakeMin)
            .putInt(PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES, 480)
            .commit()

        assertTrue("Awake action should initially be shown during awake window",
            preferenceManager.getComputed(PreferenceComputations.SHOULD_SHOW_AWAKE_ACTION)!!)

        rawPreferences.edit()
            .putLong(PreferenceKeys.KEY_LAST_AWAKE_TIME_MS, now)
            .commit()

        assertFalse("Awake action should return false after awake action is registered",
            preferenceManager.getComputed(PreferenceComputations.SHOULD_SHOW_AWAKE_ACTION)!!)
    }
}
