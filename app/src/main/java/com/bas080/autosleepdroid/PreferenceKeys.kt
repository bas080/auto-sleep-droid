package com.bas080.autosleepdroid

object PreferenceKeys {
    const val PREFERENCES_NAME: String = "sleep_timer"

    const val KEY_ACTIVE: String = "active"
    const val KEY_DURATION_MINUTES: String = "duration_minutes"
    const val KEY_SHOW_NOTIFICATION: String = "show_notification"
    const val KEY_TIMER_ENDS_AT: String = "timer_ends_at"
    const val KEY_TIMER_START_TIME_MS: String = "timer_start_time_ms"
    const val KEY_SLEEP_START_TIME_MS: String = "sleep_start_time_ms"
    const val KEY_AUTO_TIMER_ENABLED: String = "auto_timer_enabled"
    const val KEY_WAKE_UP_GOAL_ENABLED: String = "wake_up_goal_enabled"
    const val KEY_WAKE_UP_GOAL_HOUR: String = "wake_up_goal_hour"
    const val KEY_WAKE_UP_GOAL_MINUTE: String = "wake_up_goal_minute"
    const val KEY_CURRENT_WAKE_HOUR: String = "current_wake_hour"
    const val KEY_CURRENT_WAKE_MINUTE: String = "current_wake_minute"
    const val KEY_MIN_SLEEP_DURATION_MINUTES: String = "min_sleep_duration_minutes"
    const val KEY_NAP_DND_ENABLED: String = "nap_dnd_enabled"
    const val KEY_NAP_DURATION_MINUTES: String = "nap_duration_minutes"
    const val KEY_NAP_ALARM_ENDS_AT: String = "nap_alarm_ends_at"
    const val KEY_NAP_START_TIME_MS: String = "nap_start_time_ms"
    const val KEY_NAP_ALARM_RINGING: String = "is_nap_alarm_ringing"
    const val KEY_WAKEUP_ALARM_RINGING: String = "is_wakeup_alarm_ringing"
    const val KEY_WAKEUP_ALARM_SNOOZED: String = "is_wakeup_alarm_snoozed"
    const val KEY_HEALTH_CONNECT_ENABLED: String = "health_connect_enabled"
    const val KEY_HC_MIN_DURATION_MINUTES: String = "hc_min_duration_minutes"
    const val KEY_WAKEUP_LAST_SCHEDULED_MS: String = "wakeup_last_scheduled_ms"
}
