package com.bas080.autosleepdroid;

public class PreferenceComputations {

    public static final PreferenceManager.ComputedValue<Boolean> IS_WAKE_ALARM_ENABLED =
            getter -> getter.getBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, false);

    public static final PreferenceManager.ComputedValue<Boolean> IS_NAP_ACTIVE =
            getter -> getter.getLong(PreferenceKeys.KEY_NAP_ALARM_ENDS_AT, 0L) > System.currentTimeMillis();

    public static final PreferenceManager.ComputedValue<Boolean> SHOULD_SHOW_AWAKE_ACTION = getter -> {
        long sleepStartTime = getter.getLong(PreferenceKeys.KEY_SLEEP_START_TIME_MS, 0L);
        long timerStartTime = getter.getLong(PreferenceKeys.KEY_TIMER_START_TIME_MS, 0L);
        long now = System.currentTimeMillis();
        return (sleepStartTime > 0L && (now - sleepStartTime < 14 * 3600_000L))
                || (timerStartTime > 0L && (now - timerStartTime < 14 * 3600_000L));
    };

    public static PreferenceManager.ComputedValue<String> formatDuration(String key, int defaultMinutes) {
        return getter -> DurationUtils.formatDurationString(getter.getInt(key, defaultMinutes));
    }
}
