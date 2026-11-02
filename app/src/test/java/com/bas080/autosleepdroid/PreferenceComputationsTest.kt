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

    @Test
    fun testSessionPhaseAndAwakeActionAcrossMidnightBoundary() {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.add(Calendar.MINUTE, -10)
        val wakeHour = cal.get(Calendar.HOUR_OF_DAY)
        val wakeMin = cal.get(Calendar.MINUTE)

        rawPreferences.edit()
            .putBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, true)
            .putInt(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR, wakeHour)
            .putInt(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE, wakeMin)
            .putInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, wakeHour)
            .putInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, wakeMin)
            .putInt(PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES, 480)
            .putLong(PreferenceKeys.KEY_SLEEP_START_TIME_MS, now - 8 * 3600_000L)
            .commit()

        assertEquals(SessionPhase.ALARM, preferenceManager.getComputed(PreferenceComputations.GET_SESSION_PHASE))
        assertTrue("Awake action should be shown when wake time passed 10 minutes ago across midnight boundary",
            preferenceManager.getComputed(PreferenceComputations.SHOULD_SHOW_AWAKE_ACTION)!!)
    }

    @Test
    fun testPreferenceComputationsSimpleGettersAndFormatters() {
        assertFalse(preferenceManager.getComputed(PreferenceComputations.IS_AUTO_TIMER_ENABLED)!!)
        rawPreferences.edit().putBoolean(PreferenceKeys.KEY_AUTO_TIMER_ENABLED, true).commit()
        assertTrue(preferenceManager.getComputed(PreferenceComputations.IS_AUTO_TIMER_ENABLED)!!)

        assertFalse(preferenceManager.getComputed(PreferenceComputations.IS_HEALTH_CONNECT_ENABLED)!!)
        rawPreferences.edit().putBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, true).commit()
        assertTrue(preferenceManager.getComputed(PreferenceComputations.IS_HEALTH_CONNECT_ENABLED)!!)

        val durationComp = PreferenceComputations.formatDuration("test_dur", 45)
        assertEquals("45m", preferenceManager.getComputed(durationComp))
        rawPreferences.edit().putInt("test_dur", 90).commit()
        assertEquals("1h 30m", preferenceManager.getComputed(durationComp))
    }

    @Test
    fun testGetSessionPhaseWithTimerEndsAtAndSleepStartTimePushesWakeTime() {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)
        val currentMin = cal.get(Calendar.MINUTE)

        // Case: timerEndsAt pushes wake time into future
        rawPreferences.edit()
            .putBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, true)
            .putInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, currentHour)
            .putInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, currentMin)
            .putInt(PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES, 480)
            .putInt(PreferenceKeys.KEY_DURATION_MINUTES, 30)
            .putLong(PreferenceKeys.KEY_TIMER_ENDS_AT, now + 1800_000L)
            .commit()

        val phase1 = preferenceManager.getComputed(PreferenceComputations.GET_SESSION_PHASE)
        assertTrue(phase1 != SessionPhase.IDLE)

        // Case: sleepStartTime pushes wake time into future
        rawPreferences.edit()
            .remove(PreferenceKeys.KEY_TIMER_ENDS_AT)
            .putLong(PreferenceKeys.KEY_SLEEP_START_TIME_MS, now - 3600_000L)
            .commit()

        val phase2 = preferenceManager.getComputed(PreferenceComputations.GET_SESSION_PHASE)
        assertTrue(phase2 != SessionPhase.IDLE)
    }

    @Test
    fun testMidnightBoundaryCalculationsInSessionPhaseAndAwakeAction() {
        val now = System.currentTimeMillis()
        val cal14hAhead = Calendar.getInstance()
        cal14hAhead.timeInMillis = now + 14 * 3600_000L
        val aheadHour = cal14hAhead.get(Calendar.HOUR_OF_DAY)
        val aheadMin = cal14hAhead.get(Calendar.MINUTE)

        rawPreferences.edit()
            .putBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, true)
            .putInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, aheadHour)
            .putInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, aheadMin)
            .commit()

        preferenceManager.getComputed(PreferenceComputations.GET_SESSION_PHASE)
        preferenceManager.getComputed(PreferenceComputations.SHOULD_SHOW_AWAKE_ACTION)

        val cal14hBehind = Calendar.getInstance()
        cal14hBehind.timeInMillis = now - 14 * 3600_000L
        val behindHour = cal14hBehind.get(Calendar.HOUR_OF_DAY)
        val behindMin = cal14hBehind.get(Calendar.MINUTE)

        rawPreferences.edit()
            .putInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, behindHour)
            .putInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, behindMin)
            .commit()

        preferenceManager.getComputed(PreferenceComputations.GET_SESSION_PHASE)
        preferenceManager.getComputed(PreferenceComputations.SHOULD_SHOW_AWAKE_ACTION)
    }

    @Test
    fun testCalculateElasticStepMinutes() {
        // 5-hour difference -> max step (30m)
        assertEquals(30, PreferenceComputations.calculateElasticStepMinutes(12, 0, 7, 0))

        // 3-hour difference (180 min) -> full max step (30m)
        assertEquals(30, PreferenceComputations.calculateElasticStepMinutes(10, 0, 7, 0))

        // Greater than 3 hours (e.g. 7 hours) -> clamped to max step (30m)
        assertEquals(30, PreferenceComputations.calculateElasticStepMinutes(14, 0, 7, 0))

        // 2-hour difference (120 min) -> tension = 120/180 = 0.67 -> rawStep = 5 + 0.67 * 25 = 21.67m -> 20m
        assertEquals(20, PreferenceComputations.calculateElasticStepMinutes(9, 0, 7, 0))

        // 45-minute difference -> tension = 45/180 = 0.25 -> rawStep = 5 + 0.25 * 25 = 11.25m -> rounded to 10m
        assertEquals(10, PreferenceComputations.calculateElasticStepMinutes(7, 45, 7, 0))

        // 5-minute difference -> rounded to min step (5m)
        assertEquals(5, PreferenceComputations.calculateElasticStepMinutes(7, 5, 7, 0))

        // Exactly at goal (0 diff) -> 0m
        assertEquals(0, PreferenceComputations.calculateElasticStepMinutes(7, 0, 7, 0))

        // Current earlier than goal -> 0m (no deduction)
        assertEquals(0, PreferenceComputations.calculateElasticStepMinutes(6, 30, 7, 0))

        // Midnight wrap: goal 23:30, current 01:30 (2h diff = 120m) -> 20m
        assertEquals(20, PreferenceComputations.calculateElasticStepMinutes(1, 30, 23, 30))
    }

    @Test
    fun testCalculateNextWakeTime() {
        // 12:00 -> 11:30 (30m step)
        val (h1, m1) = PreferenceComputations.calculateNextWakeTime(12, 0, 7, 0)
        assertEquals(11, h1)
        assertEquals(30, m1)

        // 07:45 -> 07:35 (10m step)
        val (h2, m2) = PreferenceComputations.calculateNextWakeTime(7, 45, 7, 0)
        assertEquals(7, h2)
        assertEquals(35, m2)

        // 07:05 -> 07:00 (5m step to goal)
        val (h3, m3) = PreferenceComputations.calculateNextWakeTime(7, 5, 7, 0)
        assertEquals(7, h3)
        assertEquals(0, m3)

        // 07:00 -> 07:00 (stays at goal)
        val (h4, m4) = PreferenceComputations.calculateNextWakeTime(7, 0, 7, 0)
        assertEquals(7, h4)
        assertEquals(0, m4)

        // Midnight wrap: 01:30 -> 01:10 towards 23:30 (20m step)
        val (h5, m5) = PreferenceComputations.calculateNextWakeTime(1, 30, 23, 30)
        assertEquals(1, h5)
        assertEquals(10, m5)
    }
}
