# Sleep Session Concept & Workflow

## Overview

A **sleep session** in Auto Sleep Droid represents a tracked period of sleep bounded by a start timestamp and an end timestamp. Sleep is defined as the rest period that occurs either before a wake-up alarm (a nightly sleep session) or when a nap is started (a nap session).

Auto Sleep Droid uses these sessions to track rest intervals and automatically log completed sleep records to Android Health Connect.

## Session Phases Relative to Alarm Time

A sleep session progresses through four concise lifecycle phases evaluated by predicates on current time (`now`), `currentWakeTime`, and `minSleepDuration`:

### 1. Idle Phase

```text
!isSessionOngoing && (now < currentWakeTime - (minSleepDuration * 1.2) || now >= currentWakeTime)
```

- **Description**: Current time is outside the sleep session window (prior to bedtime or after wake time).
- **Nap Option**: Fully enabled on the main UI and notification shade.
- **"I'm Awake" Action**: Hidden.

### 2. Initiation & Active Sleep Phase

```text
now >= currentWakeTime - (minSleepDuration * 1.2) && now < currentWakeTime - (minSleepDuration * 0.5)
```

- **Description**: Sleep window when going to bed or actively sleeping.
- **Nap Option**: Disabled on main UI and omitted from notifications.
- **"I'm Awake" Action**: Hidden during early sleep.

### 3. Pre-Alarm Window Phase

```text
now >= currentWakeTime - (minSleepDuration * 0.5) && now < currentWakeTime
```

- **Description**: Current time enters the early wake window preceding `currentWakeTime`.
- **Nap Option**: Disabled.
- **"I'm Awake" Action**: Visible in notification shade. Tapping **I'm Awake** completes the session, logs to Health Connect, resets wake time to target goal time, and reschedules for tomorrow.

### 4. Alarm Phase

```text
isSessionOngoing && now >= currentWakeTime
```

- **Description**: Current time reaches or passes `currentWakeTime` during an ongoing session. Snoozing advances `currentWakeTime` to the snoozed alarm time.
- **Actions**: Notification shade offers **I'm Awake** (as the dismiss action) and **Snooze**. Tapping **I'm Awake** dismisses the alarm, completes and logs the session, resets wake time to target goal time, and reverts to the Idle Phase.

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
