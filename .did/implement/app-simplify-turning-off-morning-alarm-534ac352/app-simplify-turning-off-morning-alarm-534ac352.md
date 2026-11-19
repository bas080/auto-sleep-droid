---
title: Simplify the code for turning off the wake-up alarm in the morning
status: open
priority: 3
issue-type: task
created-at: "2026-11-19T20:04:39.923939+00:00"
---

Scope selected by @bas080: Option C (minimal refactor + cleanup + API shrink). Implemented in MainService.kt only; behavior preserved.

## Implemented changes

- Extracted `cancelServiceAlarm(requestCode, action)`; the four duplicated pending-intent cancel blocks in `onCancelAlarm()`, `cancelSnoozeAlarm()`, and `dismissAutoSleepAlarm()` now delegate to it.
- Extracted `stopAndResetWakeAlarm()` teardown (stop sound, release wake lock, cancel snooze alarm, reset alarm state) used by `handleAwakeAction()` and `handleOffStateTransition()`.
- Removed the duplicate `KEY_SNOOZE_COUNT` removal in `handleAwakeAction()` (already handled by `setWakeUpAlarmState(false, false)`); it now performs a single prefs edit for `KEY_LAST_AWAKE_TIME_MS`.
- Narrowed `dismissAutoSleepAlarm()` to only cancel the wake alarm and clear `KEY_WAKEUP_LAST_SCHEDULED_MS`; the awake-window notification-update cancel moved into the explicit `cancelNotificationUpdateAlarm()`, now invoked at the dismissal sites.

## Deliberately unchanged

- `onDestroy()` keeps its three-step teardown without `cancelSnoozeAlarm()` so an armed snooze alarm keeps surviving service destruction as before.
- Scheduling side (`snoozeWakeUpAlarm()` vs `scheduleAlarmClockInternal()`) left untouched; that belongs to the separate MainService modularization task.

## Verification

- `./gradlew test` passes (Robolectric suite, including tests reflecting on `cancelSnoozeAlarm`/`dismissAutoSleepAlarm` by name).
- `./gradlew detekt --rerun` passes with no new findings.

## Status

Code implementation and unit tests pass. Awaiting physical device verification of "I'm Awake" and snooze-to-threshold before moving to `.did/complete/`.
