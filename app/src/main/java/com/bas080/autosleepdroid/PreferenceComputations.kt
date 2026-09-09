package com.bas080.autosleepdroid

import java.util.Calendar

enum class SessionPhase {
    IDLE,
    INITIATION_AND_ACTIVE_SLEEP,
    PRE_ALARM_WINDOW,
    ALARM
}

fun getSessionPhase(
    now: Long,
    currentWakeTime: Long,
    minSleepDuration: Long,
    isSessionOngoing: Boolean,
    isAlarmRingingOrSnoozed: Boolean = false
): SessionPhase {
    if (isAlarmRingingOrSnoozed || (isSessionOngoing && now >= currentWakeTime)) {
        return SessionPhase.ALARM
    }

    val windowStart = currentWakeTime - (minSleepDuration * 1.2).toLong()
    val preAlarmStart = currentWakeTime - (minSleepDuration * 0.5).toLong()

    if (now >= preAlarmStart && now < currentWakeTime) {
        return SessionPhase.PRE_ALARM_WINDOW
    }

    if (now >= windowStart && now < preAlarmStart) {
        return SessionPhase.INITIATION_AND_ACTIVE_SLEEP
    }

    return SessionPhase.IDLE
}

object PreferenceComputations {

    val IS_WAKE_ALARM_ENABLED: PreferenceManager.ComputedValue<Boolean> =
        PreferenceManager.ComputedValue { getter ->
            getter.getBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, false)
        }

    val IS_NAP_ACTIVE: PreferenceManager.ComputedValue<Boolean> =
        PreferenceManager.ComputedValue { getter ->
            getter.getLong(PreferenceKeys.KEY_NAP_ALARM_ENDS_AT, 0L) > System.currentTimeMillis()
        }

    val GET_SESSION_PHASE: PreferenceManager.ComputedValue<SessionPhase> =
        PreferenceManager.ComputedValue { getter ->
            val now = System.currentTimeMillis()
            val wakeAlarmEnabled = getter.getBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, false)

            val minSleepMin = getter.getInt(PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES, AppDefaults.MIN_SLEEP_DURATION_MINUTES)
            val minSleepDurationMs = minSleepMin * 60_000L

            var currentWakeTime = 0L

            if (wakeAlarmEnabled) {
                val goalHour = getter.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR, AppDefaults.WAKE_UP_GOAL_HOUR)
                val goalMin = getter.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE, AppDefaults.WAKE_UP_GOAL_MINUTE)
                val currentHour = getter.getInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, goalHour)
                val currentMin = getter.getInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, goalMin)

                val calCurrent = Calendar.getInstance()
                calCurrent.timeInMillis = now
                calCurrent.set(Calendar.HOUR_OF_DAY, currentHour)
                calCurrent.set(Calendar.MINUTE, currentMin)
                calCurrent.set(Calendar.SECOND, 0)
                calCurrent.set(Calendar.MILLISECOND, 0)
                if (now - calCurrent.timeInMillis > 12 * 3600_000L) {
                    calCurrent.add(Calendar.DAY_OF_YEAR, 1)
                }
                currentWakeTime = calCurrent.timeInMillis

                val timerDuration = getter.getInt(PreferenceKeys.KEY_DURATION_MINUTES, AppDefaults.DURATION_MINUTES)
                val timerEndsAt = getter.getLong(PreferenceKeys.KEY_TIMER_ENDS_AT, 0L)
                val sleepStartTime = getter.getLong(PreferenceKeys.KEY_SLEEP_START_TIME_MS, 0L)
                var minWakeTimeMillis = 0L
                if (timerEndsAt > 0L) {
                    val effectiveMinSleepMs = Math.max(0L, (minSleepMin - timerDuration) * 60_000L)
                    minWakeTimeMillis = timerEndsAt + effectiveMinSleepMs
                } else if (sleepStartTime > 0L && (now - sleepStartTime < 14 * 3600_000L)) {
                    val effectiveMinSleepMs = Math.max(0L, (minSleepMin - timerDuration) * 60_000L)
                    minWakeTimeMillis = sleepStartTime + effectiveMinSleepMs
                }
                if (minWakeTimeMillis > currentWakeTime) {
                    currentWakeTime = minWakeTimeMillis
                }
            }

            val sleepStartTime = getter.getLong(PreferenceKeys.KEY_SLEEP_START_TIME_MS, 0L)
            val timerStartTime = getter.getLong(PreferenceKeys.KEY_TIMER_START_TIME_MS, 0L)
            val timerEndsAt = getter.getLong(PreferenceKeys.KEY_TIMER_ENDS_AT, 0L)
            val napEndsAt = getter.getLong(PreferenceKeys.KEY_NAP_ALARM_ENDS_AT, 0L)
            val isNapRinging = getter.getBoolean(PreferenceKeys.KEY_NAP_ALARM_RINGING, false)
            val isWakeupRinging = getter.getBoolean(PreferenceKeys.KEY_WAKEUP_ALARM_RINGING, false)
            val isWakeupSnoozed = getter.getBoolean(PreferenceKeys.KEY_WAKEUP_ALARM_SNOOZED, false)
            val isAlarmRingingOrSnoozed = isNapRinging || isWakeupRinging || isWakeupSnoozed

            val isSessionOngoing = (sleepStartTime > 0L && (now - sleepStartTime < 14 * 3600_000L)) ||
                    (timerStartTime > 0L && (now - timerStartTime < 14 * 3600_000L)) ||
                    (timerEndsAt > 0L) ||
                    (napEndsAt > now) ||
                    isAlarmRingingOrSnoozed

            getSessionPhase(now, currentWakeTime, minSleepDurationMs, isSessionOngoing, isAlarmRingingOrSnoozed)
        }

    val SHOULD_SHOW_AWAKE_ACTION: PreferenceManager.ComputedValue<Boolean> =
        PreferenceManager.ComputedValue { getter ->
            val napActive = getter.getLong(PreferenceKeys.KEY_NAP_ALARM_ENDS_AT, 0L) > System.currentTimeMillis()
            if (napActive) return@ComputedValue true

            val phase = GET_SESSION_PHASE.compute(getter)
            phase == SessionPhase.PRE_ALARM_WINDOW || phase == SessionPhase.ALARM
        }

    val IS_NAP_ALLOWED: PreferenceManager.ComputedValue<Boolean> =
        PreferenceManager.ComputedValue { getter ->
            val napActive = getter.getLong(PreferenceKeys.KEY_NAP_ALARM_ENDS_AT, 0L) > System.currentTimeMillis()
            if (napActive) return@ComputedValue true

            val phase = GET_SESSION_PHASE.compute(getter)
            phase == SessionPhase.IDLE
        }

    val IS_AUTO_TIMER_ENABLED: PreferenceManager.ComputedValue<Boolean> =
        PreferenceManager.ComputedValue { getter ->
            getter.getBoolean(PreferenceKeys.KEY_AUTO_TIMER_ENABLED, false)
        }

    val IS_HEALTH_CONNECT_ENABLED: PreferenceManager.ComputedValue<Boolean> =
        PreferenceManager.ComputedValue { getter ->
            getter.getBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, false)
        }

    val IS_NAP_DND_ENABLED: PreferenceManager.ComputedValue<Boolean> =
        PreferenceManager.ComputedValue { getter ->
            getter.getBoolean(PreferenceKeys.KEY_NAP_DND_ENABLED, false)
        }

    fun formatDuration(key: String, defaultMinutes: Int): PreferenceManager.ComputedValue<String> {
        return PreferenceManager.ComputedValue { getter ->
            DurationUtils.formatDurationString(getter.getInt(key, defaultMinutes))
        }
    }
}
