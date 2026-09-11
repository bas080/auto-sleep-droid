# Sleep Sessions & Lifecycle Workflow

This document details the concepts, predicates, start/end time capture, and Health Connect synchronization workflow for sleep sessions in Auto Sleep Droid.

## Core Concept
A **sleep session** in Auto Sleep Droid represents a tracked period of sleep bounded by a start timestamp and an end timestamp. Sleep is defined as the rest period that occurs before a wake-up alarm (a nightly sleep session).

## Session Lifecycle Phases

Sleep sessions progress through four concise lifecycle phases relative to scheduled wake alarm time:

1. **Idle Phase**: Outside the sleep window or when no wake alarm is scheduled.
2. **Initiation & Active Sleep Phase**: Within `[currentWakeTime - 1.2 * minSleepDuration, currentWakeTime - 0.5 * minSleepDuration]`.
3. **Pre-Alarm Window Phase**: Within `[currentWakeTime - 0.5 * minSleepDuration, currentWakeTime]`. The "I'm Awake" action is displayed on ongoing status notifications.
4. **Alarm Phase**: Wake alarm is ringing or snoozed, or `now >= currentWakeTime`.

## Session Start & End Capture

- **Start Time Capture**:
  - `timer_start_time_ms` is recorded when the sleep timer is activated or reset.
  - `sleep_start_time_ms` is recorded when media playback is paused while the timer is active or when the timer expires and media is paused.
- **End Time Capture**:
  - Captured when the wake alarm triggers, is dismissed, or when **I'm Awake** is tapped.
  - Tapping **I'm Awake** sets current wake-up time to `max(targetGoalTime, T - 15m)` where `T` is system time when pressed, logs the session to Health Connect (if enabled), and reschedules tomorrow's alarm for target goal time.
