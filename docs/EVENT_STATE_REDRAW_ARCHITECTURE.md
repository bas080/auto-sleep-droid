# Event-State-Redraw Architecture Design

## Overview

This document specifies the target architecture for Auto Sleep Droid's event processing, state management, background listener lifecycle, and UI synchronization loop.

The primary goal of this architecture is to make every state change deterministically flow through a unidirectional `Event -> State -> Redraw / IO / Listeners` execution loop. Whenever any event occurs, the state machine processes the event, updates the immutable state model, and computes pure derived models that drive notification rendering, main UI presentation, listener registrations, and I/O side effects.

---

## Architecture Objectives

1. **Unidirectional Data Flow**: State transitions are purely driven by explicit event dispatching (`AppEvent`). Direct mutations across individual state variables are eliminated.
2. **Guaranteed Redraw on Event**: Every handled `AppEvent` guarantees a redraw phase that refreshes both the ongoing status notification and the main UI controls.
3. **Computed View Models**: Notification content and main UI control states are computed as pure functions of the system state (`AppState`), ensuring UI consistency across app backgrounding, Activity recreation, and notification updates.
4. **Declarative Listener Synchronization**: Required background listeners (`AudioPlaybackCallback`, motion sensor `SensorEventListener`, volume `BroadcastReceiver`, DND receiver) are declared as computed requirements (`ListenerRequirements`). A listener sync subsystem differential compares previous vs. target listener requirements to register or unregister listeners dynamically without redundant lifecycle calls.
5. **Decoupled Side Effects & I/O**: Alarms, volume adjustments, haptic feedback, and `SharedPreferences` persistence are output commands produced alongside the new state, isolating state calculation from system frameworks for easy unit testing.

---

## Core Execution Loop

When any event occurs (user touch, hardware sensor, broadcast, or timer expiration), system execution follows a strict 5-stage pipeline:

1. **Event Dispatch**: System inputs (UI interactions, broadcasts, sensors, timers) construct an immutable `AppEvent` instance and dispatch it to the `StateStore`.
2. **State Reduction**: The `StateStore` evaluates `(CurrentState, AppEvent)` to compute the next `AppState` and a set of requested I/O commands.
3. **Value Computation**: The new `AppState` is passed to pure projector functions to produce computed projections:
   - `NotificationModel`: Text titles, body strings, and pending intent actions for `SleepTimerService`.
   - `MainUiModel`: Visibility, switch states, formatted strings, and enabled/disabled states for `MainActivity`.
   - `ListenerRequirements`: Boolean flags indicating which background listeners must be active.
   - `AlarmScheduleModel`: Required exact `AlarmManager` clock schedules (timer expiry, wake alarm, nap alarm).
4. **Redraw Execution**:
   - `SleepTimerService` updates the ongoing status notification using `NotificationModel`.
   - If `MainActivity` is in the foreground, `SleepTimerService` delivers the latest `MainUiModel` (via direct callback or broadcast) to update UI controls.
5. **Listener Sync & I/O Execution**:
   - The listener manager compares current active listeners against `ListenerRequirements` and registers or unregisters hardware/system listeners.
   - The I/O runner executes scheduled `AlarmManager` updates, stream volume changes, haptic feedback pulses, and async `SharedPreferences` persistence.

---

## Model Specifications

### 1. `AppState` (Immutable System State)

The `AppState` object contains the full snapshot of system state:

- **Timer State**: `TimerPhase` (`OFF`, `WAITING`, `ACTIVE`, `FADING`), `configuredDurationMinutes` (int), `timerEndsAt` (long wall-clock ms), `volumeBeforeFade` (int), `fadeStep` (int).
- **Auto Sleep (DND)**: `autoTimerEnabled` (boolean), `dndActive` (boolean).
- **Wake Alarm & Goal**: `wakeAlarmEnabled` (boolean), `wakeUpGoalHour` (int), `wakeUpGoalMinute` (int), `currentWakeHour` (int), `currentWakeMinute` (int), `minSleepDurationMinutes` (int), `isWakeUpAlarmRinging` (boolean), `isWakeUpAlarmSnoozed` (boolean).
- **Nap Alarm**: `napDurationMinutes` (int), `napAlarmEndsAt` (long wall-clock ms).
- **Environment State**: `lastObservedVolume` (int), `lastObservedMediaActive` (boolean).

### 2. `AppEvent` (Dispatched System Events)

`AppEvent` represents all system inputs:

- **User Actions**: `TurnOn`, `TurnOff`, `SetDuration(minutes)`, `ToggleAutoTimer(enabled)`, `ToggleWakeAlarm(enabled)`, `SetWakeGoal(hour, minute)`, `SetCurrentWake(hour, minute)`, `SetMinSleep(minutes)`, `StartNap(minutes)`, `CancelNap`, `ClearGoal`.
- **Media & System Broadcasts**: `PlaybackStateChanged(musicActive)`, `VolumeChanged(currentVolume)`, `DndFilterChanged(dndActive)`, `BootCompleted`.
- **Hardware Sensors**: `PhoneFlipped`.
- **Timer & Expirations**: `TimerExpired`, `FadeStepTick`, `FadeCompleted`, `WakeUpAlarmExpired`, `NapAlarmExpired`, `SnoozeWakeUpAlarm`, `DismissWakeUpAlarm`.

### 3. Computed Models

#### `NotificationModel`
- `title` (String)
- `contentText` (String)
- `toggleActionLabel` (String: `"Enable"` or `"Disable"`)
- `napActionLabel` (String: `"Nap"` or `"Cancel Nap"`)
- `isOngoing` (boolean)

#### `MainUiModel`
- `timerEnabled` (boolean)
- `durationText` (String)
- `autoTimerEnabled` (boolean)
- `wakeAlarmEnabled` (boolean)
- `targetTimeText` (String)
- `currentWakeTimeText` (String)
- `minSleepText` (String)
- `napStatusText` (String)
- `inputsEnabled` (boolean)
- `alarmControlsEnabled` (boolean)

#### `ListenerRequirements`
- `needsAudioPlaybackCallback` (boolean): `true` when in `WAITING` state.
- `needsAccelerometerListener` (boolean): `true` when in `ACTIVE` or `FADING` state, or when wake alarm is ringing.
- `needsVolumeReceiver` (boolean): `true` when in `ACTIVE` or `FADING` state, or when wake alarm is ringing or snoozed.
- `needsDndReceiver` (boolean): `true` when `autoTimerEnabled` is true.

#### `AlarmScheduleModel`
- `timerExpiryTimeMs` (long, 0 if none)
- `wakeUpAlarmTimeMs` (long, 0 if none)
- `napAlarmTimeMs` (long, 0 if none)

---

## Detailed Code Blueprints

### Blueprint 1: `AppState.java`

```java
package com.bas080.autosleepdroid.state;

public final class AppState {
    public enum TimerPhase { OFF, WAITING, ACTIVE, FADING }

    public final TimerPhase timerPhase;
    public final int configuredDurationMinutes;
    public final long timerEndsAt;
    public final int volumeBeforeFade;
    public final int fadeStep;

    public final boolean autoTimerEnabled;
    public final boolean dndActive;

    public final boolean wakeAlarmEnabled;
    public final int wakeUpGoalHour;
    public final int wakeUpGoalMinute;
    public final int currentWakeHour;
    public final int currentWakeMinute;
    public final int minSleepDurationMinutes;
    public final boolean isWakeUpAlarmRinging;
    public final boolean isWakeUpAlarmSnoozed;

    public final int napDurationMinutes;
    public final long napAlarmEndsAt;

    public final int lastObservedVolume;
    public final boolean lastObservedMediaActive;

    public AppState(TimerPhase timerPhase, int configuredDurationMinutes, long timerEndsAt,
                    int volumeBeforeFade, int fadeStep, boolean autoTimerEnabled, boolean dndActive,
                    boolean wakeAlarmEnabled, int wakeUpGoalHour, int wakeUpGoalMinute,
                    int currentWakeHour, int currentWakeMinute, int minSleepDurationMinutes,
                    boolean isWakeUpAlarmRinging, boolean isWakeUpAlarmSnoozed,
                    int napDurationMinutes, long napAlarmEndsAt,
                    int lastObservedVolume, boolean lastObservedMediaActive) {
        this.timerPhase = timerPhase;
        this.configuredDurationMinutes = configuredDurationMinutes;
        this.timerEndsAt = timerEndsAt;
        this.volumeBeforeFade = volumeBeforeFade;
        this.fadeStep = fadeStep;
        this.autoTimerEnabled = autoTimerEnabled;
        this.dndActive = dndActive;
        this.wakeAlarmEnabled = wakeAlarmEnabled;
        this.wakeUpGoalHour = wakeUpGoalHour;
        this.wakeUpGoalMinute = wakeUpGoalMinute;
        this.currentWakeHour = currentWakeHour;
        this.currentWakeMinute = currentWakeMinute;
        this.minSleepDurationMinutes = minSleepDurationMinutes;
        this.isWakeUpAlarmRinging = isWakeUpAlarmRinging;
        this.isWakeUpAlarmSnoozed = isWakeUpAlarmSnoozed;
        this.napDurationMinutes = napDurationMinutes;
        this.napAlarmEndsAt = napAlarmEndsAt;
        this.lastObservedVolume = lastObservedVolume;
        this.lastObservedMediaActive = lastObservedMediaActive;
    }

    public static AppState createDefault() {
        return new AppState(
                TimerPhase.OFF, 20, 0L,
                0, 0, false, false,
                false, 6, 30, 6, 30, 450, false, false,
                20, 0L, 0, false
        );
    }
}
```

### Blueprint 2: `StateStore.java`

```java
package com.bas080.autosleepdroid.state;

import android.content.Context;

public class StateStore {
    public interface StateChangeListener {
        void onStateUpdated(AppState oldState, AppState newState, SystemCommands commands);
    }

    private AppState currentState;
    private StateChangeListener listener;

    public StateStore(AppState initialState) {
        this.currentState = initialState;
    }

    public synchronized AppState getState() {
        return currentState;
    }

    public synchronized void setListener(StateChangeListener listener) {
        this.listener = listener;
    }

    public synchronized void dispatch(AppEvent event, long now) {
        AppState oldState = currentState;
        StateReductionResult result = StateReducer.reduce(currentState, event, now);
        this.currentState = result.nextState;

        if (listener != null) {
            listener.onStateUpdated(oldState, currentState, result.commands);
        }
    }
}
```

### Blueprint 3: `StateProjector.java` (Computed Outputs)

```java
package com.bas080.autosleepdroid.state;

import android.content.Context;
import com.bas080.autosleepdroid.DurationUtils;
import com.bas080.autosleepdroid.R;

public class StateProjector {

    public static ListenerRequirements computeListenerRequirements(AppState state) {
        boolean needsAudio = (state.timerPhase == AppState.TimerPhase.WAITING);
        boolean needsSensor = (state.timerPhase == AppState.TimerPhase.ACTIVE
                || state.timerPhase == AppState.TimerPhase.FADING
                || state.isWakeUpAlarmRinging);
        boolean needsVolume = (state.timerPhase == AppState.TimerPhase.ACTIVE
                || state.timerPhase == AppState.TimerPhase.FADING
                || state.isWakeUpAlarmRinging
                || state.isWakeUpAlarmSnoozed);
        boolean needsDnd = state.autoTimerEnabled;

        return new ListenerRequirements(needsAudio, needsSensor, needsVolume, needsDnd);
    }

    public static NotificationModel computeNotificationModel(Context context, AppState state) {
        String title;
        String contentText;
        String formattedDurationStr = DurationUtils.formatDurationString(state.configuredDurationMinutes);

        if (state.isWakeUpAlarmRinging) {
            title = context.getString(R.string.wakeup_alarm_title);
            contentText = context.getString(R.string.wakeup_alarm_text);
        } else if (state.isWakeUpAlarmSnoozed) {
            title = context.getString(R.string.wakeup_alarm_title);
            contentText = context.getString(R.string.toast_alarm_snoozed);
        } else if (state.timerPhase == AppState.TimerPhase.OFF) {
            title = context.getString(R.string.timer_off);
            contentText = context.getString(R.string.timer_off_collapsed, formattedDurationStr);
        } else if (state.timerPhase == AppState.TimerPhase.FADING) {
            title = context.getString(R.string.fading_title);
            contentText = context.getString(R.string.fading_collapsed);
        } else if (state.timerPhase == AppState.TimerPhase.ACTIVE) {
            title = context.getString(R.string.active_title);
            contentText = context.getString(R.string.active_collapsed, formatTime(context, state.timerEndsAt), formattedDurationStr);
        } else {
            title = context.getString(R.string.waiting_title);
            contentText = context.getString(R.string.waiting_collapsed, formattedDurationStr);
        }

        boolean enabled = (state.timerPhase != AppState.TimerPhase.OFF);
        String toggleLabel = context.getString(enabled ? R.string.action_turn_off : R.string.action_turn_on);
        boolean napActive = state.napAlarmEndsAt > System.currentTimeMillis();
        String napLabel = context.getString(napActive ? R.string.action_cancel_nap : R.string.action_nap);

        return new NotificationModel(title, contentText, toggleLabel, napLabel, enabled, napActive);
    }

    public static MainUiModel computeMainUiModel(Context context, AppState state) {
        boolean timerEnabled = (state.timerPhase != AppState.TimerPhase.OFF);
        String durationText = DurationUtils.formatDurationString(state.configuredDurationMinutes);
        String targetTimeText = formatClockTime(context, state.wakeUpGoalHour, state.wakeUpGoalMinute);
        String currentWakeTimeText = formatClockTime(context, state.currentWakeHour, state.currentWakeMinute);
        String minSleepText = DurationUtils.formatDurationString(state.minSleepDurationMinutes);
        boolean napActive = state.napAlarmEndsAt > System.currentTimeMillis();
        String napStatusText = napActive ? context.getString(R.string.action_cancel_nap) : DurationUtils.formatDurationString(state.napDurationMinutes);

        return new MainUiModel(
                timerEnabled,
                durationText,
                state.autoTimerEnabled,
                state.wakeAlarmEnabled,
                targetTimeText,
                currentWakeTimeText,
                minSleepText,
                napStatusText,
                true,
                state.wakeAlarmEnabled
        );
    }

    private static String formatClockTime(Context context, int hour, int minute) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour);
        cal.set(java.util.Calendar.MINUTE, minute);
        java.text.DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(context);
        return timeFormat.format(cal.getTime());
    }

    private static String formatTime(Context context, long timeMs) {
        java.text.DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(context);
        return timeFormat.format(new java.util.Date(timeMs));
    }
}
```

### Blueprint 4: Redraw & Listener Synchronization in `SleepTimerService.java`

```java
private void handleStateUpdated(AppState oldState, AppState newState, SystemCommands commands) {
    // 1. Compute outputs
    NotificationModel notifModel = StateProjector.computeNotificationModel(this, newState);
    MainUiModel uiModel = StateProjector.computeMainUiModel(this, newState);
    ListenerRequirements listenerReqs = StateProjector.computeListenerRequirements(newState);

    // 2. Redraw notification
    renderNotification(notifModel);

    // 3. Redraw main UI (if connected or via broadcast)
    notifyUiUpdated(uiModel);

    // 4. Synchronize listeners
    syncListeners(listenerReqs);

    // 5. Execute side-effect commands
    executeCommands(commands);
}

private void syncListeners(ListenerRequirements reqs) {
    if (reqs.needsAudioPlaybackCallback) registerAudioPlaybackCallback();
    else unregisterAudioPlaybackCallback();

    if (reqs.needsAccelerometerListener) registerSensorListener();
    else unregisterSensorListener();

    if (reqs.needsVolumeReceiver) registerVolumeObserver();
    else unregisterVolumeObserver();

    if (reqs.needsDndReceiver) registerDndReceiver();
    else unregisterDndReceiver();
}
```

---

## Step-by-Step Migration Plan

To implement this architecture in Auto Sleep Droid without regressions, complete the following stages:

### Stage 1: Extract `AppState`, `AppEvent`, and Value Objects
1. Create `com.bas080.autosleepdroid.state` package.
2. Implement immutable `AppState`, `AppEvent` hierarchy, `NotificationModel`, `MainUiModel`, `ListenerRequirements`, and `AlarmScheduleModel`.
3. Add full unit test coverage for `AppState` instantiation and defaults.

### Stage 2: Create Pure Reducer & StateStore
1. Implement `StateReducer.reduce(AppState, AppEvent, long now)` containing all timer phase transitions, volume flip reset rules, wake alarm ring/snooze/dismiss logic, and nap handling.
2. Implement `StateProjector` to compute `NotificationModel`, `MainUiModel`, and `ListenerRequirements`.
3. Add unit tests in `StateReducerTest` verifying every state transition in `docs/EVENTS_AND_STATES.md`.

### Stage 3: Refactor `SleepTimerService` to Use `StateStore`
1. Replace mutable field properties in `SleepTimerService` with `StateStore`.
2. Convert service Intent action handlers (`ACTION_TURN_ON`, `ACTION_TURN_OFF`, `ACTION_SET_DURATION`, `ACTION_ALARM_EXPIRY`, `ACTION_START_NAP`, etc.) into `StateStore.dispatch(AppEvent)`.
3. Connect `StateStore` listener callback to trigger `NotificationModel` redraw, listener sync, and I/O command execution.

### Stage 4: Refactor `MainActivity` UI Redraw
1. Expose `MainUiModel` updates to `MainActivity` via service binding or `BroadcastReceiver`.
2. Replace manual SharedPreferences reads during click listeners with `AppEvent` dispatching.
3. Ensure UI switches and text fields automatically refresh whenever `MainUiModel` is received.

### Stage 5: Verification & Cleanup
1. Run `./gradlew test` to confirm all unit tests pass.
2. Verify notification ongoing state across all 4 timer states (`OFF`, `WAITING`, `ACTIVE`, `FADING`).
3. Verify wake alarm ringing, snooze via flip gesture, and dismissal via volume key.
4. Verify nap alarm rescheduling and main UI auto-refresh.
