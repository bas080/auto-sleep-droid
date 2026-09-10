# Sleep Session Concept & Workflow

## Overview

A **sleep session** in Auto Sleep Droid represents a tracked period of sleep bounded by a start timestamp and an end timestamp. Sleep is defined as the rest period that occurs either before a wake-up alarm (a nightly sleep session) or when a nap is started (a nap session).

Auto Sleep Droid uses these sessions to track rest intervals and automatically log completed sleep records to Android Health Connect.

## Awake Window & Alarm Interactions

A sleep session is active during the rest window surrounding your scheduled wake time. The application provides a simple window for declaring wakefulness (**"I'm Awake"**):

### Awake Window Availability
- **Active Sleep Window**: When an active sleep session exists and current time is within the window around `currentWakeTime` (from `currentWakeTime - 1.2 * minSleepDuration` up to `currentWakeTime + 4 hours`).
- **Alarm / Snooze Phase**: Whenever a wake alarm or nap is ringing or snoozed, or when a nap is active.
- **Daytime Idle**: Outside this window, **"Nap"** is shown instead of **"I'm Awake"**.

### Alarm Controls & Gestures
- **Snoozing**: Both phone flip gestures and hardware volume button presses snooze a ringing or snoozed wake alarm for 9 minutes.
- **Stopping**: Only tapping **"I'm Awake"** stops and dismisses the alarm, updating **Current Wake-Up Time** to the moment it was pressed and logging the sleep session to Health Connect.

## Awake Window Calculation Pseudocode

The awake window evaluation in `PreferenceComputations.kt` calculates whether **"I'm Awake"** is displayed based on current time, scheduled wake time, and minimum sleep duration:

```kotlin
val windowStart = currentWakeTime - (1.2 * minSleepMs).toLong()
val windowEnd = currentWakeTime + 4 * 3600_000L

val isAwakeWindowActive = (now in windowStart..windowEnd) || isAlarmRingingOrSnoozed || isNapActive
```

## How Sleep Sessions Work

Auto Sleep Droid tracks two distinct types of sleep sessions: **Nightly Sleep Sessions** and **Nap Sessions**.

### Nightly Sleep Sessions

Nightly sleep sessions track the primary sleep period preceding `currentWakeTime` or a wake action.

- **Start Time Capture**:
  - **Timer Start Time**: Recorded (`timer_start_time_ms`) when sleep timer is activated or reset.
  - **Timer Expiration**: Recorded (`sleep_start_time_ms`) when timer expires and media pauses. Resuming and expiring media later updates `sleep_start_time_ms` to the latest expiration time.
  - **Pre-Wake Reset Window**: Resetting when `now >= currentWakeTime - (minSleepDuration * 1.2)` updates `sleep_start_time_ms` to current time if no active session exists.
  - **Fallback Calculation**: If no timer was run, estimated as `currentWakeTime - minSleepDuration` upon wake alarm trigger or confirmation.

- **End Time Capture**:
  - Captured when wake alarm is dismissed or when **I'm Awake** is tapped.

### Nap Sessions

Nap sessions track short daytime rests initiated via the Nap feature.

- **Start Time Capture**: Recorded (`nap_start_time_ms`) when a nap alarm is started during the Idle Phase.
- **End Time Capture**: Captured when nap alarm triggers, is dismissed, or when **I'm Awake** is tapped while a nap is active.
- **Independent Operation**: Nap sessions operate independently of nightly sleep sessions and do not alter `currentWakeTime`.

### Session Processing & Health Connect Integration

When a sleep session completes:

- **Health Connect Sync**: Valid sessions exceeding minimum session threshold (`hc_min_duration_minutes`, default 15m) are written to Health Connect as a `SleepSessionRecord`.
- **Minimum Duration Safeguard**: Sessions under the threshold are ignored.
- **Stale Session Drop Policy**: Unconfirmed sessions older than 14 hours are dropped without recording.
- **Timestamp Cleanup**: Pending timestamps are cleared after processing.
