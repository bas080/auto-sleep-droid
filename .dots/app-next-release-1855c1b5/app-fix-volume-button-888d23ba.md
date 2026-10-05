---
title: Fix volume button snooze detection when screen is locked
status: open
parent: app-next-release-1855c1b5
priority: 1
issue-type: task
created-at: "2026-10-05T15:31:48.987124+00:00"
---

Volume button presses are not detected when attempting to snooze the ringing wake alarm while the screen is locked.

### Context & Implementation Details
- `MainService` acquires a temporary 30-second screen `WakeLock` (`PowerManager.FULL_WAKE_LOCK` with `ACQUIRE_CAUSES_WAKEUP` and `ON_AFTER_RELEASE`) when `ACTION_WAKEUP_ALARM_EXPIRY` triggers to wake the screen.
- On locked keyguard screens, hardware volume button presses (`VOLUME_CHANGED_ACTION`) may be intercepted or suppressed by keyguard unless media session key dispatch or alarm audio stream focus is active.
- Need to ensure `VOLUME_CHANGED_ACTION` broadcast receiver reliably captures volume key presses while the wake alarm is ringing under keyguard lock.

### Acceptance Criteria
- Physical volume key presses snooze the wake alarm for 9 minutes when the screen is locked.
- Screen wakes reliably and keyguard state does not prevent volume change handling.
- Unit and integration tests verify snooze action triggers on volume change during ringing state.
