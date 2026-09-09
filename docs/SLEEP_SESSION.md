# Sleep Session Concept & Workflow

## Overview

A **sleep session** in Auto Sleep Droid represents a tracked period of sleep bounded by a start timestamp and an end timestamp. Sleep is defined as the rest period that occurs either before a wake-up alarm (a nightly sleep session) or when a nap is started (a nap session).

Auto Sleep Droid uses these sessions to track rest intervals and automatically log completed sleep records to Android Health Connect.

## Phases Within a Sleep Session

A sleep session progresses through distinct lifecycle phases, which dictate available actions in the main UI and notification shade.

### 1. Initiation / Timer Running Phase
- **State**: The user starts the sleep timer or media playback begins. A sleep session is initiated (`timer_start_time_ms` recorded).
- **Nap Restriction**: Starting a nap is not permitted while a main sleep session is already in progress. The Nap option is disabled on the main UI, and no Nap action is offered in notifications.
- **"I'm Awake" Action**: Hidden during early countdown. Tapping **I'm Awake** while the countdown timer is running discards pending timer timestamps without stopping countdown or canceling alarms.

### 2. Active Sleep Phase
- **State**: The sleep timer expires, media playback pauses (`sleep_start_time_ms` recorded), and the user is asleep.
- **Nap Restriction**: Starting a nap remains disabled while the sleep session is actively in progress.
- **"I'm Awake" Action**: Remains hidden until the pre-alarm window is reached.

### 3. Pre-Alarm Window Phase ("I'm Awake")
- **State**: Current time enters the pre-alarm window (e.g. within `1.2 * min_sleep_duration` or several hours preceding the scheduled wake alarm).
- **"I'm Awake" Action**: The **I'm Awake** action becomes visible in the ongoing status notification shade.
- **Action Execution**: Tapping **I'm Awake** completes the sleep session, logs it to Health Connect (if enabled), cancels today's pending wake alarm, resets the wake time to target goal time, and reschedules for tomorrow.

### 4. Alarm Ringing / Snoozed Phase
- **State**: The scheduled wake-up alarm triggers and is actively ringing or snoozed.
- **Actions**: The notification shade offers **Dismiss** (or **I'm Awake**) and **Snooze**. Dismissing or marking awake concludes the sleep session.

### 5. Idle Phase
- **State**: No sleep session is in progress.
- **Nap Availability**: The Nap feature is fully enabled on the main UI button and offered as the secondary action in the notification shade.

## How Sleep Sessions Work

Auto Sleep Droid tracks two distinct types of sleep sessions: **Nightly Sleep Sessions** and **Nap Sessions**.

### Nightly Sleep Sessions

Nightly sleep sessions track the primary sleep period preceding a scheduled morning wake-up alarm or wake action.

- **Start Time Capture**:
  - **Timer Start Time**: When the sleep timer is activated or reset (via phone flip gesture, hardware volume button press, or duration adjustment), `timer_start_time_ms` is recorded.
  - **Timer Expiration**: When the sleep timer countdown completes and media playback is paused, `sleep_start_time_ms` is recorded. If media playback is resumed later during the night and the sleep timer expires again, `sleep_start_time_ms` updates to the latest expiration timestamp.
  - **Pre-Wake Reset Window**: If the sleep timer is reset within the window `[current_wake_time - 1.2 * min_sleep_duration, current_wake_time]` while no active session exists (or an existing session is over 14 hours old), `sleep_start_time_ms` updates to the current system timestamp.
  - **Fallback Calculation**: If no sleep timer was run prior to wake-up, the app estimates sleep start as `wake_time - minimum_sleep_duration` upon wake alarm trigger or explicit wake confirmation.

- **End Time Capture**:
  - Captured when the scheduled wake-up alarm is dismissed (via hardware volume button press, notification action button, or main UI) or when the user explicitly taps **I'm Awake** in the ongoing status notification shade.

### Nap Sessions

Nap sessions track short daytime rests initiated via the Nap feature.

- **Start Time Capture**: Recorded (`nap_start_time_ms`) when a nap alarm is started from the main screen or status notification shade when no night sleep session is in progress.
- **End Time Capture**: Captured when the nap alarm triggers, is dismissed, or when the user taps **I'm Awake** while a nap is active.
- **Independent Operation**: Nap sessions operate independently of nightly sleep sessions and do not alter or adjust scheduled nightly wake-up goal times.

### Session Processing & Health Connect Integration

When a sleep session or nap completes (upon alarm dismissal or tapping **I'm Awake**):

- **Health Connect Sync**: If Health Connect integration is enabled (`health_connect_enabled`), Health Connect SDK is present on the device, and write permission (`android.permission.health.WRITE_SLEEP`) is granted, completed sessions are written to Health Connect as a `SleepSessionRecord`.
- **Minimum Duration Safeguard**: Sessions shorter than the configured threshold (`hc_min_duration_minutes`, default 15 minutes) are filtered out and ignored to prevent logging accidental resets or brief naps.
- **Stale Session Drop Policy (14-Hour Rule)**: If 14 hours pass without explicit wake confirmation (**I'm Awake**) or alarm dismissal, unconfirmed sleep session timestamps are considered stale and dropped without being written to Health Connect.
- **Timestamp Cleanup**: Once processed or dropped, pending start timestamps (`sleep_start_time_ms`, `timer_start_time_ms`, `nap_start_time_ms`) are cleared to avoid duplicate session entries.

### Notification Integration

- **"I'm Awake" Action**: Displayed during the pre-alarm window, during active naps, or while alarms are ringing/snoozed.
- **Background Execution**: Tapping **I'm Awake** processes the sleep session directly in the background without launching UI dialogs, cancels today's pending wake alarm or active nap, resets current wake time to target goal time (for night sleep), reschedules tomorrow's alarm, and reverts the notification action back to **Nap**.
- **Active Countdown Protection**: Tapping **I'm Awake** while a sleep timer countdown is still running discards pending timer start timestamps without stopping the countdown timer or canceling scheduled alarms.
