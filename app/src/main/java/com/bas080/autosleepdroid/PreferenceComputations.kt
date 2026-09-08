package com.bas080.autosleepdroid

object PreferenceComputations {

    @JvmField
    val IS_WAKE_ALARM_ENABLED: PreferenceManager.ComputedValue<Boolean> =
        PreferenceManager.ComputedValue { getter ->
            getter.getBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, false)
        }

    @JvmField
    val IS_NAP_ACTIVE: PreferenceManager.ComputedValue<Boolean> =
        PreferenceManager.ComputedValue { getter ->
            getter.getLong(PreferenceKeys.KEY_NAP_ALARM_ENDS_AT, 0L) > System.currentTimeMillis()
        }

    @JvmField
    val SHOULD_SHOW_AWAKE_ACTION: PreferenceManager.ComputedValue<Boolean> =
        PreferenceManager.ComputedValue { getter ->
            val sleepStartTime = getter.getLong(PreferenceKeys.KEY_SLEEP_START_TIME_MS, 0L)
            val timerStartTime = getter.getLong(PreferenceKeys.KEY_TIMER_START_TIME_MS, 0L)
            val now = System.currentTimeMillis()
            (sleepStartTime > 0L && (now - sleepStartTime < 14 * 3600_000L)) ||
                    (timerStartTime > 0L && (now - timerStartTime < 14 * 3600_000L))
        }

    @JvmField
    val IS_AUTO_TIMER_ENABLED: PreferenceManager.ComputedValue<Boolean> =
        PreferenceManager.ComputedValue { getter ->
            getter.getBoolean(PreferenceKeys.KEY_AUTO_TIMER_ENABLED, false)
        }

    @JvmField
    val IS_HEALTH_CONNECT_ENABLED: PreferenceManager.ComputedValue<Boolean> =
        PreferenceManager.ComputedValue { getter ->
            getter.getBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, false)
        }

    @JvmField
    val IS_NAP_DND_ENABLED: PreferenceManager.ComputedValue<Boolean> =
        PreferenceManager.ComputedValue { getter ->
            getter.getBoolean(PreferenceKeys.KEY_NAP_DND_ENABLED, false)
        }

    @JvmStatic
    fun formatDuration(key: String, defaultMinutes: Int): PreferenceManager.ComputedValue<String> {
        return PreferenceManager.ComputedValue { getter ->
            DurationUtils.formatDurationString(getter.getInt(key, defaultMinutes))
        }
    }
}
