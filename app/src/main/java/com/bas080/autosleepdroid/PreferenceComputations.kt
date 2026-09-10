package com.bas080.autosleepdroid

import java.util.Calendar

object PreferenceComputations {

    val IS_WAKE_ALARM_ENABLED: PreferenceManager.ComputedValue<Boolean> =
        PreferenceManager.ComputedValue { getter ->
            getter.getBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, false)
        }

    val SHOULD_SHOW_AWAKE_ACTION: PreferenceManager.ComputedValue<Boolean> =
        PreferenceManager.ComputedValue { getter ->
            val now = System.currentTimeMillis()

            val isWakeupRinging = getter.getBoolean(PreferenceKeys.KEY_WAKEUP_ALARM_RINGING, false)
            val isWakeupSnoozed = getter.getBoolean(PreferenceKeys.KEY_WAKEUP_ALARM_SNOOZED, false)
            if (isWakeupRinging || isWakeupSnoozed) return@ComputedValue true

            val wakeAlarmEnabled = getter.getBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, false)
            if (!wakeAlarmEnabled) return@ComputedValue false

            val lastScheduledMs = getter.getLong(PreferenceKeys.KEY_WAKEUP_LAST_SCHEDULED_MS, 0L)
            val currentWakeTime: Long

            if (lastScheduledMs > 0L) {
                currentWakeTime = lastScheduledMs
            } else {
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
                if (calCurrent.timeInMillis <= now) {
                    calCurrent.add(Calendar.DAY_OF_YEAR, 1)
                }
                currentWakeTime = calCurrent.timeInMillis
            }

            val minSleepMin = getter.getInt(PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES, AppDefaults.MIN_SLEEP_DURATION_MINUTES)
            val halfMinSleepMs = (minSleepMin * 60_000L) / 2

            val windowStart = currentWakeTime - halfMinSleepMs
            val windowEnd = currentWakeTime + halfMinSleepMs

            now in windowStart..windowEnd
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
