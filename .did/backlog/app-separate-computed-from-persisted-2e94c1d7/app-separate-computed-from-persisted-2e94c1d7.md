---
title: Make a clear distinction between computed and persisted state
status: open
priority: 3
issue-type: task
created-at: "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
---

Guideline from @bas080: if something needs to work across restarts or crashes, preferences is the preferred method over in-memory (only). Everything else may live in memory.

## Audit of the current state (v0.0.42)

Persisted keys today mix four different categories with no enforced distinction:

1. Configuration (user intent — correct to persist): duration_minutes, show_notification, auto_timer_enabled, wake_up_goal_enabled/hour/minute, min_sleep_duration_minutes, health_connect_enabled, hc_min_duration_minutes, donate_dialog_hidden.
2. Recorded observations (history — correct to persist): last_awake_time_ms, current_wake_hour/minute, sleep_start_time_ms, timer_start_time_ms.
3. Runtime machine state written back into prefs (the muddy zone): timer_ends_at (derivable from timer_start + duration), is_wakeup_alarm_ringing / is_wakeup_alarm_snoozed (1:1 mirrors of in-memory flags), snooze_count (session-scoped counter), wakeup_last_scheduled_ms (schedule de-dup cache).
4. KEY_ACTIVE does double duty: configuration (UI toggle) AND live machine state (onPersistState overwrites it on every transition). This overloading drives the self-echo loop (service writes KEY_ACTIVE -> watchEffect replays into reloadTimerSettings) and explains "timer off" vs "service armed" disagreements in real-night logs.

Computed values done right (the model to extend): PreferenceManager.ComputedValue — IS_WAKE_ALARM_ENABLED, IS_AUTO_TIMER_ENABLED, IS_HEALTH_CONNECT_ENABLED; derived, tracked, never persisted.

## Proposed tidy rule

Persist only: (1) configuration, (2) recorded observations, (3) a single deliberate "resurrection snapshot" key holding exactly what initializeTimerState needs to rebuild after process death (per the restart/crash guideline above), written in exactly one place. Then remove timer_ends_at, the ringing/snoozed mirrors, snooze_count and wakeup_last_scheduled_ms from prefs and keep them in memory.

## Related work

- Pairs with the state-management refactor: suppress self-echo on onPersistState, make transitionTo a no-op on same state, complete updateListenersRegistration() (playback callback). See PR #243 for the guard precedent.
