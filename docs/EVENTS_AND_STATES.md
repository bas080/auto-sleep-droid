# Events & System States

This document details event definitions, system state transitions, and event logging for Auto Sleep Droid.

## Events

- **`SET_DURATION`**: User updates the sleep timer duration.
- **`TURN_ON`**: User or DND automation turns on the sleep timer (`ACTION_TURN_ON`).
- **`TURN_OFF`**: User or DND automation turns off the sleep timer (`ACTION_TURN_OFF`).
- **`PLAYBACK_STARTED`**: Audio playback callback detects active media playback.
- **`PLAYBACK_STOPPED`**: Audio playback callback detects paused media playback while timer is active.
- **`VOLUME_CHANGED`**: Physical volume key pressed during Active or Fading state (resets timer or snoozes alarm).
- **`ALARM_EXPIRY`**: Sleep timer countdown reaches zero.
- **`WAKEUP_ALARM_EXPIRY`**: Wake-Up Goal alarm time reached (`ACTION_WAKEUP_ALARM_EXPIRY`).
- **`SNOOZE_WAKEUP_ALARM`**: User snoozes ringing wake alarm for 9m via volume key or notification button (`ACTION_SNOOZE_WAKEUP_ALARM`).
- **`DISMISS_WAKEUP_ALARM`**: User dismisses wake alarm or taps "I'm Awake".
- **`AWAKE`**: User taps `"I'm Awake"` on `MainActivity` or notification shade (`ACTION_AWAKE`).
- **`CLEAR_GOAL`**: User turns off Wake-Up Goal feature (`ACTION_CLEAR_GOAL`).

## System States

- **Off**: Sleep timer disabled.
- **Waiting**: Sleep timer enabled, sitting passively listening for media playback.
- **Active**: Countdown running towards expiration timestamp (`timer_ends_at`). Rescheduled by volume changes or duration updates.
- **Fading**: 30-second volume fade-out running before pausing media.

## Event Logging

All major lifecycle, timer, and alarm events are recorded in real-time by `EventLogger` and viewable in the Event Logs screen on `MainActivity`.
