package com.bas080.autosleepdroid

import java.util.Calendar

private const val INIT_WINDOW_FACTOR = 1.2
private const val PRE_ALARM_WINDOW_FACTOR = 0.5
private const val MS_PER_MINUTE = 60_000L
private const val HOURS_12_IN_MS = 12 * 3600_000L
private const val HOURS_14_IN_MS = 14 * 3600_000L

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
    val windowStart = currentWakeTime - (minSleepDuration * INIT_WINDOW_FACTOR).toLong()
    val preAlarmStart = currentWakeTime - (minSleepDuration * PRE_ALARM_WINDOW_FACTOR).toLong()

    return when {
        isAlarmRingingOrSnoozed || (isSessionOngoing && now >= currentWakeTime) -> SessionPhase.ALARM
        now >= preAlarmStart && now < currentWakeTime -> SessionPhase.PRE_ALARM_WINDOW
        now >= windowStart && now < preAlarmStart -> SessionPhase.INITIATION_AND_ACTIVE_SLEEP
        else -> SessionPhase.IDLE
    }
}

object PreferenceComputations {

    val IS_WAKE_ALARM_ENABLED: PreferenceManager.ComputedValue<Boolean> =
        PreferenceManager.ComputedValue { getter ->
            getter.getBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, false)
        }

    val GET_SESSION_PHASE: PreferenceManager.ComputedValue<SessionPhase> =
        PreferenceManager.ComputedValue { getter ->
            val now = System.currentTimeMillis()
            val wakeAlarmEnabled = getter.getBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, false)

            val minSleepMin = getter.getInt(
                PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES,
                AppDefaults.MIN_SLEEP_DURATION_MINUTES
            )
            val minSleepDurationMs = minSleepMin * MS_PER_MINUTE

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
                if (now - calCurrent.timeInMillis > HOURS_12_IN_MS) {
                    calCurrent.add(Calendar.DAY_OF_YEAR, 1)
                } else if (calCurrent.timeInMillis - now > HOURS_12_IN_MS) {
                    calCurrent.add(Calendar.DAY_OF_YEAR, -1)
                }
                currentWakeTime = calCurrent.timeInMillis

                val timerDuration = getter.getInt(PreferenceKeys.KEY_DURATION_MINUTES, AppDefaults.DURATION_MINUTES)
                val timerEndsAt = getter.getLong(PreferenceKeys.KEY_TIMER_ENDS_AT, 0L)
                val sleepStartTime = getter.getLong(PreferenceKeys.KEY_SLEEP_START_TIME_MS, 0L)
                var minWakeTimeMillis = 0L
                if (timerEndsAt > 0L) {
                    val effectiveMinSleepMs = Math.max(0L, (minSleepMin - timerDuration) * MS_PER_MINUTE)
                    minWakeTimeMillis = timerEndsAt + effectiveMinSleepMs
                } else if (sleepStartTime > 0L && (now - sleepStartTime < HOURS_14_IN_MS)) {
                    val effectiveMinSleepMs = Math.max(0L, (minSleepMin - timerDuration) * MS_PER_MINUTE)
                    minWakeTimeMillis = sleepStartTime + effectiveMinSleepMs
                }
                if (minWakeTimeMillis > currentWakeTime) {
                    currentWakeTime = minWakeTimeMillis
                }
            }

            val sleepStartTime = getter.getLong(PreferenceKeys.KEY_SLEEP_START_TIME_MS, 0L)
            val timerStartTime = getter.getLong(PreferenceKeys.KEY_TIMER_START_TIME_MS, 0L)
            val timerEndsAt = getter.getLong(PreferenceKeys.KEY_TIMER_ENDS_AT, 0L)
            val isWakeupRinging = getter.getBoolean(PreferenceKeys.KEY_WAKEUP_ALARM_RINGING, false)
            val isWakeupSnoozed = getter.getBoolean(PreferenceKeys.KEY_WAKEUP_ALARM_SNOOZED, false)
            val isAlarmRingingOrSnoozed = isWakeupRinging || isWakeupSnoozed

            val isSessionOngoing = (sleepStartTime > 0L && (now - sleepStartTime < HOURS_14_IN_MS)) ||
                    (timerStartTime > 0L && (now - timerStartTime < HOURS_14_IN_MS)) ||
                    (timerEndsAt > 0L) ||
                    isAlarmRingingOrSnoozed

            getSessionPhase(now, currentWakeTime, minSleepDurationMs, isSessionOngoing, isAlarmRingingOrSnoozed)
        }

    val SHOULD_SHOW_AWAKE_ACTION: PreferenceManager.ComputedValue<Boolean> =
        PreferenceManager.ComputedValue { getter ->
            val wakeAlarmEnabled = getter.getBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, false)
            if (!wakeAlarmEnabled) return@ComputedValue false

            val isWakeupRinging = getter.getBoolean(PreferenceKeys.KEY_WAKEUP_ALARM_RINGING, false)
            val isWakeupSnoozed = getter.getBoolean(PreferenceKeys.KEY_WAKEUP_ALARM_SNOOZED, false)
            if (isWakeupRinging || isWakeupSnoozed) return@ComputedValue true

            val now = System.currentTimeMillis()
            val minSleepMin = getter.getInt(
                PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES,
                AppDefaults.MIN_SLEEP_DURATION_MINUTES
            )
            val minSleepDurationMs = minSleepMin * MS_PER_MINUTE

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
            if (now - calCurrent.timeInMillis > HOURS_12_IN_MS) {
                calCurrent.add(Calendar.DAY_OF_YEAR, 1)
            } else if (calCurrent.timeInMillis - now > HOURS_12_IN_MS) {
                calCurrent.add(Calendar.DAY_OF_YEAR, -1)
            }
            val currentWakeTime = calCurrent.timeInMillis

            val rangeStart = currentWakeTime - (minSleepDurationMs / 2)
            val rangeEnd = currentWakeTime + (minSleepDurationMs / 2)

            if (now !in rangeStart..rangeEnd) return@ComputedValue false

            val lastAwakeTime = getter.getLong(PreferenceKeys.KEY_LAST_AWAKE_TIME_MS, 0L)
            if (lastAwakeTime > 0L && (now - lastAwakeTime < HOURS_12_IN_MS || lastAwakeTime >= rangeStart)) {
                return@ComputedValue false
            }

            true
        }

    val IS_AUTO_TIMER_ENABLED: PreferenceManager.ComputedValue<Boolean> =
        PreferenceManager.ComputedValue { getter ->
            getter.getBoolean(PreferenceKeys.KEY_AUTO_TIMER_ENABLED, false)
        }

    val IS_HEALTH_CONNECT_ENABLED: PreferenceManager.ComputedValue<Boolean> =
        PreferenceManager.ComputedValue { getter ->
            getter.getBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, false)
        }

    fun formatDuration(key: String, defaultMinutes: Int): PreferenceManager.ComputedValue<String> {
        return PreferenceManager.ComputedValue { getter ->
            DurationUtils.formatDurationString(getter.getInt(key, defaultMinutes))
        }
    }
}
