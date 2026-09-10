package com.bas080.autosleepdroid

object AppDefaults {
    const val DURATION_MINUTES: Int = 20
    const val MINUTES_MIN: Int = 1
    const val MINUTES_MAX: Int = 24 * 60 // 1440

    const val WAKE_UP_GOAL_HOUR: Int = 6
    const val WAKE_UP_GOAL_MINUTE: Int = 30

    const val MIN_SLEEP_DURATION_MINUTES: Int = 450 // 7h 30m
    const val HC_MIN_DURATION_MINUTES: Int = 15

    const val FADE_DURATION_MS: Long = 30_000L
    const val FADE_STEP_INTERVAL_MS: Long = 1_000L
    const val TOTAL_FADE_STEPS: Int = (FADE_DURATION_MS / FADE_STEP_INTERVAL_MS).toInt()

    const val SNOOZE_DURATION_MS: Long = 9 * 60_000L
    const val ALARM_CRESCENDO_DURATION_MS: Long = 3 * 60_000L
    const val ALARM_CRESCENDO_INTERVAL_MS: Long = 500L
}
