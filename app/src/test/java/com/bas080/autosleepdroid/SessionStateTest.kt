package com.bas080.autosleepdroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SessionStateTest {

    @Test
    fun testGetSessionPhaseIdleWhenGoalDisabled() {
        val now = System.currentTimeMillis()
        val phase = getSessionPhase(
            now = now,
            currentWakeTime = now + 12 * 3600 * 1000L,
            minSleepDuration = 450 * 60 * 1000L,
            isSessionOngoing = false,
            isAlarmRingingOrSnoozed = false
        )
        assertEquals(SessionPhase.IDLE, phase)
    }

    @Test
    fun testGetSessionPhaseAlarmWhenRingingOrSnoozed() {
        val now = System.currentTimeMillis()
        val phase = getSessionPhase(
            now = now,
            currentWakeTime = now + 8 * 3600 * 1000L,
            minSleepDuration = 450 * 60 * 1000L,
            isSessionOngoing = true,
            isAlarmRingingOrSnoozed = true
        )
        assertEquals(SessionPhase.ALARM, phase)
    }

    @Test
    fun testGetSessionPhaseInitiationAndActiveSleep() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val currentWakeTime = cal.timeInMillis
        val minSleepDurationMs = 450 * 60 * 1000L // 7.5 hours

        // now = 6 hours before wake time (within [wake - 1.2 * minSleep, wake - 0.5 * minSleep])
        val now = currentWakeTime - (6 * 3600 * 1000L)
        val phase = getSessionPhase(
            now = now,
            currentWakeTime = currentWakeTime,
            minSleepDuration = minSleepDurationMs,
            isSessionOngoing = true,
            isAlarmRingingOrSnoozed = false
        )
        assertEquals(SessionPhase.INITIATION_AND_ACTIVE_SLEEP, phase)
    }

    @Test
    fun testGetSessionPhasePreAlarmWindow() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val currentWakeTime = cal.timeInMillis
        val minSleepDurationMs = 450 * 60 * 1000L

        // now = 2 hours before wake time (within [wake - 0.5 * minSleep, wake])
        val now = currentWakeTime - (2 * 3600 * 1000L)
        val phase = getSessionPhase(
            now = now,
            currentWakeTime = currentWakeTime,
            minSleepDuration = minSleepDurationMs,
            isSessionOngoing = true,
            isAlarmRingingOrSnoozed = false
        )
        assertEquals(SessionPhase.PRE_ALARM_WINDOW, phase)
    }

    private fun createMockGetter(
        booleans: Map<String, Boolean> = emptyMap(),
        integers: Map<String, Int> = emptyMap(),
        longs: Map<String, Long> = emptyMap(),
        strings: Map<String, String> = emptyMap()
    ): PreferenceGetter {
        return object : PreferenceGetter {
            override fun getBoolean(key: String, defValue: Boolean): Boolean = booleans[key] ?: defValue
            override fun getInt(key: String, defValue: Int): Int = integers[key] ?: defValue
            override fun getLong(key: String, defValue: Long): Long = longs[key] ?: defValue
            override fun getString(key: String, defValue: String?): String? = strings[key] ?: defValue
            override fun contains(key: String): Boolean =
                booleans.containsKey(key) || integers.containsKey(key) || longs.containsKey(key) || strings.containsKey(key)
        }
    }

    @Test
    fun testComputedSessionPhaseValue() {
        val getter = createMockGetter(
            booleans = mapOf(
                PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED to true,
                PreferenceKeys.KEY_WAKEUP_ALARM_RINGING to true
            ),
            integers = mapOf(
                PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR to 6,
                PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE to 30,
                PreferenceKeys.KEY_CURRENT_WAKE_HOUR to 6,
                PreferenceKeys.KEY_CURRENT_WAKE_MINUTE to 30,
                PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES to 450
            )
        )
        val phase = PreferenceComputations.GET_SESSION_PHASE.compute(getter)
        assertEquals(SessionPhase.ALARM, phase)
    }

    @Test
    fun testComputedShouldShowAwakeActionValueWhenRinging() {
        val getter = createMockGetter(
            booleans = mapOf(
                PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED to true,
                PreferenceKeys.KEY_WAKEUP_ALARM_RINGING to true
            )
        )
        val showAwake = PreferenceComputations.SHOULD_SHOW_AWAKE_ACTION.compute(getter)
        assertTrue(showAwake)
    }

    @Test
    fun testComputedShouldShowAwakeActionValueWhenDisabled() {
        val getter = createMockGetter(
            booleans = mapOf(
                PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED to false
            )
        )
        val showAwake = PreferenceComputations.SHOULD_SHOW_AWAKE_ACTION.compute(getter)
        assertFalse(showAwake)
    }
}
