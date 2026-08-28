# Auto Sleep Droid Events and States

## Overview

Auto Sleep Droid is driven by an event-based state machine architecture (`SleepTimerStateMachine`) managed by `SleepTimerService`. The core state machine controls timer countdowns, audio volume fade-outs, media pausing, and background listener registrations, while communicating system side-effects back through a callback interface.

This document describes all possible system states, listener lifecycles, input and system events, transition rules, state-event matrix, and logged event messages.

---

## System States

The system operates in one of four mutually exclusive states defined in `SleepTimerStateMachine.State`:

### 1. `OFF`

* **Definition**: The sleep timer is manually disabled. The application does not monitor playback changes, volume adjustments, or gesture movements for timer purposes.
* **Background Resources & Listeners**:
  * `AudioPlaybackCallback`: Unregistered.
  * `SensorEventListener` (Accelerometer): Unregistered.
  * Volume Observer (`VOLUME_CHANGED_ACTION` BroadcastReceiver): Unregistered.
  * Alarm Clock & AlarmManager: No alarms scheduled; any active `"Auto Sleep"` wake-up alarm is cancelled.
* **Notification Presentation**:
  * Collapsed Text: `"Timer off"`
  * Expanded Text: `"Sleep timer is off"`
  * Action Buttons: `"Set Timer"` (inline reply input) and `"Turn On"`.
* **State Invariants**: `enabled = false`, `timerEndsAt = 0`.

### 2. `WAITING`

* **Definition**: The timer is enabled with a valid configured duration (default: 20 minutes), but no media is currently playing. The system sits passively waiting for active audio playback to begin.
* **Background Resources & Listeners**:
  * `AudioPlaybackCallback`: Registered (actively checks for music playback start).
  * `SensorEventListener` (Accelerometer): Unregistered.
  * Volume Observer (`VOLUME_CHANGED_ACTION` BroadcastReceiver): Unregistered.
  * Alarm Clock & AlarmManager: No timer alarms scheduled.
* **Notification Presentation**:
  * Collapsed Text: `"Waiting for playback"`
  * Expanded Text: `"Waiting for media playback (<X>m configured)"`
  * Action Buttons: `"Set Timer"` (inline reply input) and `"Turn Off"`.
* **State Invariants**: `enabled = true`, `timerEndsAt = 0`.

### 3. `ACTIVE`

* **Definition**: The timer is counting down towards expiration (`timerEndsAt`). Note that pausing media while the timer is active does not send the timer to `WAITING`—the active countdown remains running towards expiration and can be reset to the configured sleep duration via volume adjustments, phone flip gestures, or duration updates.
* **Background Resources & Listeners**:
  * `AudioPlaybackCallback`: Unregistered (timer countdown is already active).
  * `SensorEventListener` (Accelerometer): Registered on a dedicated `HandlerThread` with 300ms temporal throttling.
  * Volume Observer (`VOLUME_CHANGED_ACTION` BroadcastReceiver): Registered.
  * `AlarmManager`: Exact timer expiration alarm scheduled using `setExactAndAllowWhileIdle()` (or fallback if permission missing).
  * Smart Wake-Up Goal: If enabled and within 12 hours prior to goal time, schedules/updates `"Auto Sleep"` system clock alarm via `AlarmClock.ACTION_SET_ALARM`.
* **Notification Presentation**:
  * Collapsed Text: `"Timer running (<target_time>)"` (e.g., `"Timer running (11:15 PM)"`)
  * Expanded Text: `"Fades out at <target_time> (<X>m configured)"` (appends `" • Alarm set for <alarm_time>"` when Smart Wake-Up Goal alarm is set).
  * Action Buttons: `"Set Timer"` (inline reply input) and `"Turn Off"`.
* **State Invariants**: `enabled = true`, `timerEndsAt > 0`.

### 4. `FADING`

* **Definition**: The countdown timer has expired. The app gradually decreases the media stream volume over 30 seconds along a quadratic ease-out curve down to zero before pausing active media playback.
* **Background Resources & Listeners**:
  * `AudioPlaybackCallback`: Unregistered.
  * `SensorEventListener` (Accelerometer): Registered (detects phone flip gestures to cancel fade, restore volume, and reset timer).
  * Volume Observer (`VOLUME_CHANGED_ACTION` BroadcastReceiver): Registered (detects user volume changes to cancel fade, preserve new volume, and reset timer).
  * Fade Handler: Executes 30 steps at 1-second intervals (`FADE_STEP_INTERVAL_MS = 1000ms`, `TOTAL_FADE_STEPS = 30`).
* **Notification Presentation**:
  * Collapsed Text: `"Fading volume"`
  * Expanded Text: `"Fading volume down to pause media"`
  * Action Buttons: `"Set Timer"` (inline reply input) and `"Turn Off"`.
* **State Invariants**: `enabled = true`.

---

## Listener Lifecycle & Registration Rules

To minimize battery consumption and avoid unnecessary CPU wakeups, listeners in Auto Sleep Droid follow strict registration and unregistration lifecycle rules:

```
+------------------------------------+-----------------------+------------------------+------------------------------------------+
| Listener Component                 | Registered On         | Active States          | Unregistered / Removed On                |
+------------------------------------+-----------------------+------------------------+------------------------------------------+
| AudioPlaybackCallback              | Transition to WAITING | WAITING only           | Transition to OFF, ACTIVE, FADING, or    |
| (AudioManager.AudioPlaybackCallback| state                 |                        | Service Destruction (onDestroy)          |
+------------------------------------+-----------------------+------------------------+------------------------------------------+
| Motion Accelerometer Listener      | Transition to ACTIVE  | ACTIVE, FADING only    | Transition to OFF or WAITING, or         |
| (SensorEventListener)              | or FADING state       |                        | Service Destruction (onDestroy)          |
+------------------------------------+-----------------------+------------------------+------------------------------------------+
| Volume Broadcast Receiver          | Transition to ACTIVE  | ACTIVE, FADING only    | Transition to OFF or WAITING, or         |
| (VOLUME_CHANGED_ACTION)            | or FADING state       |                        | Service Destruction (onDestroy)          |
+------------------------------------+-----------------------+------------------------+------------------------------------------+
| Live Event Log UI Listener         | MainActivity onResume | UI Foreground          | MainActivity onPause                     |
| (EventLogger.Listener)             |                       |                        |                                          |
+------------------------------------+-----------------------+------------------------+------------------------------------------+
```

### Detailed Registration Details

1. **`AudioManager.AudioPlaybackCallback` (API 26+)**:
   * **Registration Point**: Registered dynamically when entering `WAITING` state via `SleepTimerService.onStateChanged()`.
   * **Active Lifetime**: **`WAITING` state only**.
   * **Purpose**: Passively listens for active music playback changes (`isMusicActive()`). When in `WAITING` state, active playback triggers timer start (`ACTIVE`). Unnecessary in `OFF` (timer disabled), `ACTIVE` (countdown running towards expiration), and `FADING` states.
   * **Removal Point**: Unregistered immediately upon transition to `OFF`, `ACTIVE`, or `FADING` state, or when `SleepTimerService.onDestroy()` is called.

2. **Motion Sensor Listener (`TYPE_ACCELEROMETER`)**:
   * **Registration Point**: Dynamically registered when entering `ACTIVE` or `FADING` state via `SleepTimerService.onStateChanged()`.
   * **Background Threading**: Registered on a dedicated `HandlerThread` (`SensorThread`) with `SensorManager.SENSOR_DELAY_NORMAL` and 300ms temporal throttling to preserve battery.
   * **Active Lifetime**: **`ACTIVE` and `FADING` states only**.
   * **Purpose**: Detects phone flip gestures (face-up to face-down or face-down to face-up). Flips during `ACTIVE` reset countdown timer; flips during `FADING` cancel fade-out, restore pre-fade volume, and reset countdown timer.
   * **Removal Point**: Unregistered immediately upon transition to `OFF` or `WAITING` state, or when `SleepTimerService.onDestroy()` is invoked. The background `HandlerThread` is safely terminated (`quitSafely()`).

3. **Volume Observer (`VOLUME_CHANGED_ACTION` BroadcastReceiver)**:
   * **Registration Point**: Dynamically registered when entering `ACTIVE` or `FADING` state via `SleepTimerService.onStateChanged()`.
   * **Active Lifetime**: **`ACTIVE` and `FADING` states only**.
   * **Purpose**: Detects manual volume button presses on `STREAM_MUSIC`. Volume changes during `ACTIVE` reset countdown timer; volume changes during `FADING` cancel fade-out, preserve new volume, and reset countdown timer. Programmatic volume changes made by the app during fade/restore steps are suppressed via `suppressVolumeReset`.
   * **Removal Point**: Unregistered immediately upon transition to `OFF` or `WAITING` state, or when `SleepTimerService.onDestroy()` is invoked.

4. **Event Logger Listener (`EventLogger.Listener`)**:
   * **Registration Point**: Registered in `MainActivity.onResume()`.
   * **Active Lifetime**: Active only while `MainActivity` is in the foreground.
   * **Purpose**: Delivers live log lines directly to the main UI scrollable log view.
   * **Removal Point**: Removed (`EventLogger.setListener(null)`) in `MainActivity.onPause()` to prevent UI leaks when app is backgrounded.

---

## Input & System Events

Events in Auto Sleep Droid originate from user interactions, hardware sensors, system audio callbacks, alarm timers, and system broadcasts.

### User Input Events

* **`TURN_ON`**: User taps `"Turn On"` in notification (available when in `OFF` state).
* **`TURN_OFF`**: User taps `"Turn Off"` in notification (available in `WAITING`, `ACTIVE`, and `FADING` states).
* **`SET_DURATION`**: User submits a duration via the inline `"Set Timer"` notification reply (`RemoteInput`).
* **`SET_WAKE_UP_GOAL`**: User configures target wake-up goal time and minimum sleep duration safeguard in `MainActivity`.
* **`CLEAR_GOAL`**: User clears wake-up goal in `MainActivity` or taps clear action.

### System & Sensor Events

* **`PLAYBACK_STARTED`**: Audio playback becomes active (`isMusicActive() == true`) reported by `AudioPlaybackCallback`.
* **`PLAYBACK_STOPPED`**: Audio playback stops (`isMusicActive() == false`) reported by `AudioPlaybackCallback`.
* **`VOLUME_CHANGED`**: Music stream volume adjusted by user via hardware volume buttons (`VOLUME_CHANGED_ACTION` broadcast).
* **`PHONE_FLIPPED`**: Accelerometer detects phone flip gesture (face-up to face-down or face-down to face-up).

### Timer & Expiry Events

* **`TIMER_EXPIRED`**: Countdown reaches `timerEndsAt` or `AlarmManager` trigger fires `ACTION_ALARM_EXPIRY`.
* **`FADE_STEP`**: Periodic 1-second step executed during volume fade-out.
* **`FADE_COMPLETED`**: 30th fade step completed; triggers media pause via transient audio focus request.
* **`RESTORE_VOLUME`**: 500ms post-pause timer completes; restores volume to `volumeBeforeFade`.

### Lifecycle & Restoration Events

* **`SERVICE_INITIALIZE`**: Foreground service creates/restores state from `SharedPreferences`.
* **`BOOT_COMPLETED`**: Device reboot received by `BootReceiver`; re-starts foreground service to restore persisted state.

---

## State Transition Matrix

The table below maps each `(Current State, Event)` pair to its resulting `Next State` and side-effects / callbacks:

| Current State | Event | Next State | Callbacks & Side-Effects |
|---|---|---|---|
| **OFF** | `TURN_ON` (Music Active) | `ACTIVE` | Vibrates, calculates `timerEndsAt`, schedules alarm, persists state (`active=true`), updates notification |
| **OFF** | `TURN_ON` (Music Inactive) | `WAITING` | Vibrates, persists state (`active=true`), updates notification |
| **OFF** | `SET_DURATION` (Music Active) | `ACTIVE` | Validates input (1-1440m), vibrates, sets duration, calculates `timerEndsAt`, schedules alarm, persists state, updates notification |
| **OFF** | `SET_DURATION` (Music Inactive) | `WAITING` | Validates input (1-1440m), vibrates, sets duration, persists state, updates notification |
| **OFF** | `PLAYBACK_STARTED` | `OFF` | No transition (timer disabled) |
| **OFF** | `VOLUME_CHANGED` / `PHONE_FLIPPED` | `OFF` | Ignored (listeners unregistered) |
| **WAITING** | `PLAYBACK_STARTED` | `ACTIVE` | Calculates `timerEndsAt`, schedules alarm, updates notification |
| **WAITING** | `TURN_OFF` | `OFF` | Vibrates, cancels alarms/goal alarms, persists state (`active=false`), updates notification |
| **WAITING** | `SET_DURATION` (Music Active) | `ACTIVE` | Validates input, vibrates, sets duration, calculates `timerEndsAt`, schedules alarm, persists state, updates notification |
| **WAITING** | `SET_DURATION` (Music Inactive) | `WAITING` | Validates input, vibrates, sets duration, persists state, updates notification |
| **WAITING** | `VOLUME_CHANGED` / `PHONE_FLIPPED` | `WAITING` | Ignored (listeners unregistered) |
| **ACTIVE** | `PLAYBACK_STOPPED` | `ACTIVE` | Logs playback stop event; active countdown remains running and can be reset to sleep duration via volume change, flip gesture, or duration set |
| **ACTIVE** | `VOLUME_CHANGED` | `ACTIVE` | Vibrates, reschedules timer countdown to configured duration, updates notification |
| **ACTIVE** | `PHONE_FLIPPED` | `ACTIVE` | Vibrates, reschedules timer countdown to configured duration, updates notification |
| **ACTIVE** | `SET_DURATION` | `ACTIVE` | Validates input, vibrates, sets new duration, reschedules timer countdown, updates notification |
| **ACTIVE** | `TIMER_EXPIRED` | `FADING` | Captures `volumeBeforeFade`, registers sensor & volume listeners, starts 30s fade runnable, updates notification |
| **ACTIVE** | `TURN_OFF` | `OFF` | Vibrates, cancels timer & wake-up alarms, persists state (`active=false`), updates notification |
| **FADING** | `VOLUME_CHANGED` | `ACTIVE` | Vibrates, cancels fade, keeps user-selected volume, reschedules timer countdown, updates notification |
| **FADING** | `PHONE_FLIPPED` | `ACTIVE` | Vibrates, cancels fade, restores pre-fade volume, reschedules timer countdown, updates notification |
| **FADING** | `FADE_STEP` (step < 30) | `FADING` | Calculates next step volume along ease-out curve, applies stream volume (with `suppressVolumeReset=true`) |
| **FADING** | `FADE_COMPLETED` (step == 30) | `WAITING` | Requests transient audio focus to pause media, schedules 500ms post-pause runnable to restore pre-fade volume, transitions to `WAITING` |
| **FADING** | `SET_DURATION` | `ACTIVE` | Validates input, vibrates, cancels fade, restores pre-fade volume, reschedules timer with new duration, updates notification |
| **FADING** | `TURN_OFF` | `OFF` | Vibrates, cancels fade runnable, cancels alarms, persists state (`active=false`), updates notification |
| **Any State** | `BOOT_COMPLETED` / Init | Restored State | Restores `WAITING` or `ACTIVE` if previously enabled; restores `OFF` if previously `OFF` |

---

## Logged Events Reference

All system state changes and input triggers are logged to `EventLogger` with a timestamp format (`M/d HH:mm:ss`). Below is a reference of standard log messages produced during operation:

| Event / Trigger | Log Message Format |
|---|---|
| Service Created | `SleepTimerService created` |
| State Initialized | `SleepTimerService state initialized (enabled: <bool>, duration: <X>m)` |
| Turn On Action | `Timer turned on` |
| Turn Off Action | `Timer turned off` |
| Set Duration Action | `Duration set to <X>m (input: '<raw_input>')` |
| Playback Started | `Music playback started` |
| Playback Stopped | `Music playback stopped` |
| Volume Adjusted | `Volume changed to <new_vol>` |
| Phone Flip Detected | `Phone flip gesture detected` |
| Timer Alarm Triggered | `AlarmManager trigger received` |
| Fade-Out Started | `Fade-out started` |
| Pre-Fade Vol Restored | `Restored pre-fade volume to <vol>` |
| Media Paused | `Timer expired: pausing media` |
| Wake-Up Goal Alarm Set | `Wake-Up Goal Alarm 'Auto Sleep' set in Clock app for <formatted_time>` |
| Wake-Up Alarm Triggered | `Auto Sleep wake-up alarm triggered` |
| Service Destroyed | `SleepTimerService destroyed` |
