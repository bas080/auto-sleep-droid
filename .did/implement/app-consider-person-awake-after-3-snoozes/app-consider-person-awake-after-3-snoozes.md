---
title: Consider person awake after 3 alarm snoozes
status: open
priority: 2
issue-type: feature
created-at: "2026-10-25T18:35:00.000000+00:00"
---

Automatically consider the user awake after snoozing the wake-up goal alarm 3 times.

### Context & Implementation Details
- Track the number of consecutive snoozes for the current wake-up goal alarm session.
- When the wake-up alarm is snoozed (via volume key or phone flip gesture), increment the snooze counter.
- On the 3rd snooze attempt (or after 3 snoozes), automatically register the awake action (`processAwake()`), log the event, stop the alarm sound, and notify the user.
- Reset the snooze count when an awake action is recorded or when starting/resetting the alarm state.

### Acceptance Criteria
- Snoozing the wake-up alarm increments snooze count.
- On the 3rd snooze attempt, the app considers the user awake (`processAwake()`), stops alarm playback, and logs the event.
- Snooze count resets after awake state is registered or session ends.
- Unit tests verify snooze counting and transition to awake after 3 snoozes.
