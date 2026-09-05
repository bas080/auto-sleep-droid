# Event-State-Redraw Architecture Design

## Overview

This document specifies a minimal, composable architecture for Auto Sleep Droid's event processing, state management, background listener lifecycle, and UI synchronization loop.

Instead of framework overhead or complex command queues, the system is built around three simple primitives:

1. **`State`**: An immutable snapshot of the application state with composable, pure getter projections.
2. **`Store<S, E>`**: A lightweight state container that holds `State`, accepts events `E`, computes the next state via a reducer function `(S, E) -> S`, and notifies observers.
3. **`Observer<S>`**: Composable functional listeners that observe state updates to handle notification redrawing, UI rendering, listener lifecycle synchronization, and side effects.

---

## The Loop

Every system input (user touch, hardware gesture, broadcast, or alarm expiry) enters the exact same unidirectional loop:

```text
Event  --->  Reducer  --->  New State  --->  Observers
                                                ├── Redraw Notification
                                                ├── Redraw Main UI
                                                ├── Sync Active Listeners
                                                └── Persist / Side Effects
```

Whenever an event is dispatched, the `Store` guarantees an immediate notify phase where all subscribers receive the updated state and redraw or synchronize themselves accordingly.

---

## Core Primitives

### 1. The Generic Store (`Store.java`)

A thread-safe, 30-line state store primitive:

```java
package com.bas080.autosleepdroid.arch;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Store<S, E> {

    public interface Reducer<S, E> {
        S reduce(S currentState, E event);
    }

    public interface Observer<S> {
        void onStateChanged(S newState);
    }

    private volatile S state;
    private final Reducer<S, E> reducer;
    private final List<Observer<S>> observers = new CopyOnWriteArrayList<>();

    public Store(S initialState, Reducer<S, E> reducer) {
        this.state = initialState;
        this.reducer = reducer;
    }

    public S getState() {
        return state;
    }

    public void dispatch(E event) {
        synchronized (this) {
            state = reducer.reduce(state, event);
        }
        for (Observer<S> observer : observers) {
            observer.onStateChanged(state);
        }
    }

    public void subscribe(Observer<S> observer) {
        observers.add(observer);
        observer.onStateChanged(state); // Immediate initial update
    }

    public void unsubscribe(Observer<S> observer) {
        observers.remove(observer);
    }
}
```

---

### 2. Immutable Application State (`State.java`)

The `State` class holds raw immutable data fields alongside pure computed projections.

```java
package com.bas080.autosleepdroid.arch;

import java.util.EnumSet;

public final class State {
    public enum Phase { OFF, WAITING, ACTIVE, FADING }
    public enum Listener { AUDIO_PLAYBACK, ACCELEROMETER, VOLUME_RECEIVER, DND_RECEIVER }

    public final Phase phase;
    public final int durationMinutes;
    public final long timerEndsAt;
    public final boolean autoTimerEnabled;
    public final boolean wakeAlarmEnabled;
    public final int wakeGoalHour;
    public final int wakeGoalMinute;
    public final int currentWakeHour;
    public final int currentWakeMinute;
    public final int minSleepMinutes;
    public final boolean isWakeRinging;
    public final boolean isWakeSnoozed;
    public final long napEndsAt;

    public State(Phase phase, int durationMinutes, long timerEndsAt,
                 boolean autoTimerEnabled, boolean wakeAlarmEnabled,
                 int wakeGoalHour, int wakeGoalMinute,
                 int currentWakeHour, int currentWakeMinute,
                 int minSleepMinutes, boolean isWakeRinging, boolean isWakeSnoozed,
                 long napEndsAt) {
        this.phase = phase;
        this.durationMinutes = durationMinutes;
        this.timerEndsAt = timerEndsAt;
        this.autoTimerEnabled = autoTimerEnabled;
        this.wakeAlarmEnabled = wakeAlarmEnabled;
        this.wakeGoalHour = wakeGoalHour;
        this.wakeGoalMinute = wakeGoalMinute;
        this.currentWakeHour = currentWakeHour;
        this.currentWakeMinute = currentWakeMinute;
        this.minSleepMinutes = minSleepMinutes;
        this.isWakeRinging = isWakeRinging;
        this.isWakeSnoozed = isWakeSnoozed;
        this.napEndsAt = napEndsAt;
    }

    // --- Composable Computed Projections ---

    public boolean isEnabled() {
        return phase != Phase.OFF;
    }

    public boolean isNapActive(long now) {
        return napEndsAt > now;
    }

    /**
     * Declarative set of listeners required for this state snapshot.
     */
    public EnumSet<Listener> requiredListeners() {
        EnumSet<Listener> set = EnumSet.noneOf(Listener.class);
        if (phase == Phase.WAITING) {
            set.add(Listener.AUDIO_PLAYBACK);
        }
        if (phase == Phase.ACTIVE || phase == Phase.FADING || isWakeRinging) {
            set.add(Listener.ACCELEROMETER);
        }
        if (phase == Phase.ACTIVE || phase == Phase.FADING || isWakeRinging || isWakeSnoozed) {
            set.add(Listener.VOLUME_RECEIVER);
        }
        if (autoTimerEnabled) {
            set.add(Listener.DND_RECEIVER);
        }
        return set;
    }
}
```

---

### 3. Events (`Event.java`)

Events are sealed value objects or an enum hierarchy:

```java
package com.bas080.autosleepdroid.arch;

public interface Event {
    enum SimpleEvent implements Event {
        TURN_ON,
        TURN_OFF,
        TIMER_EXPIRED,
        PHONE_FLIPPED,
        CLEAR_GOAL,
        CANCEL_NAP,
        DISMISS_WAKE_ALARM,
        SNOOZE_WAKE_ALARM
    }

    class SetDuration implements Event {
        public final int minutes;
        public SetDuration(int minutes) { this.minutes = minutes; }
    }

    class PlaybackChanged implements Event {
        public final boolean active;
        public PlaybackChanged(boolean active) { this.active = active; }
    }

    class VolumeChanged implements Event {
        public final int level;
        public VolumeChanged(int level) { this.level = level; }
    }
}
```

---

### 4. Composable Subscribers & Listener Manager

Subscribers observe state updates independently:

```java
// Notification Redraw Subscriber
store.subscribe(state -> {
    Notification notif = NotificationBuilder.build(context, state);
    notificationManager.notify(NOTIFICATION_ID, notif);
});

// Listener Lifecycle Sync Subscriber
store.subscribe(state -> {
    EnumSet<State.Listener> required = state.requiredListeners();
    audioCallback.setEnabled(required.contains(State.Listener.AUDIO_PLAYBACK));
    accelerometer.setEnabled(required.contains(State.Listener.ACCELEROMETER));
    volumeReceiver.setEnabled(required.contains(State.Listener.VOLUME_RECEIVER));
    dndReceiver.setEnabled(required.contains(State.Listener.DND_RECEIVER));
});

// Main UI Redraw Subscriber (in MainActivity)
store.subscribe(state -> {
    switchEnableTimer.setChecked(state.isEnabled());
    textDurationValue.setText(DurationUtils.formatDurationString(state.durationMinutes));
    switchAutoTimer.setChecked(state.autoTimerEnabled);
    switchEnableGoal.setChecked(state.wakeAlarmEnabled);
});

// Persistence Subscriber
store.subscribe(state -> {
    preferences.edit()
        .putBoolean("active", state.isEnabled())
        .putInt("duration_minutes", state.durationMinutes)
        .putLong("timer_ends_at", state.timerEndsAt)
        .apply();
});
```

---

## Migration Path

1. **Add `Store`, `State`, and `Event`**: Introduce these core classes into `com.bas080.autosleepdroid.arch`.
2. **Pure Reducer**: Move transition logic from `SleepTimerStateMachine` into `AppReducer.reduce(State, Event)`.
3. **Service Integration**: Wire `SleepTimerService` to hold a `Store<State, Event>` instance and subscribe notification, listener, and alarm observers.
4. **Activity Binding**: Subscribe `MainActivity` directly to state changes on `onResume()` and unsubscribe on `onPause()`.
