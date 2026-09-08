package com.bas080.autosleepdroid;

public class AppDefaults {
    public static final int DURATION_MINUTES = 20;
    public static final int MINUTES_MIN = 1;
    public static final int MINUTES_MAX = 24 * 60; // 1440

    public static final int WAKE_UP_GOAL_HOUR = 6;
    public static final int WAKE_UP_GOAL_MINUTE = 30;

    public static final int MIN_SLEEP_DURATION_MINUTES = 450; // 7h 30m
    public static final int HC_MIN_DURATION_MINUTES = 15;
    public static final int NAP_DURATION_MINUTES = 20;

    public static final long FADE_DURATION_MS = 30_000L;
    public static final long FADE_STEP_INTERVAL_MS = 1_000L;
    public static final int TOTAL_FADE_STEPS = (int) (FADE_DURATION_MS / FADE_STEP_INTERVAL_MS);

    public static final long SNOOZE_DURATION_MS = 9 * 60_000L;
    public static final long ALARM_CRESCENDO_DURATION_MS = 3 * 60_000L;
    public static final long ALARM_CRESCENDO_INTERVAL_MS = 500L;
}
