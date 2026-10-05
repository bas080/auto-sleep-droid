---
title: Implement phone flip gesture during alarm ringing and audio fading
status: open
parent: app-next-release-1855c1b5
priority: 3
issue-type: task
created-at: "2026-10-05T15:07:54.156325+00:00"
---

Support phone flip face-down gesture to snooze when alarm is ringing and to reset timer when audio is fading.

### Context & Implementation Details
- Sensor listener (`Sensor.TYPE_ACCELEROMETER` / `SensorManager`) must strictly be registered and active ONLY during active audio fading or active wake alarm ringing phases to conserve battery.
- When alarm is ringing: face-down flip gesture snoozes the alarm for 9 minutes (`ACTION_ALARM_EXPIRY` / snooze logic).
- When audio is fading: face-down flip gesture resets the sleep timer back to its configured duration.
- Immediately unregister sensor listeners as soon as fading stops or alarm is snoozed/dismissed.

### Acceptance Criteria
- Accelerometer sensor listener is inactive when timer is idle or running without fading/ringing.
- Flipping device face-down while alarm is ringing triggers 9-minute snooze.
- Flipping device face-down while sleep timer audio is fading resets the timer duration.
- Unit tests verify sensor lifecycle management and action triggers.
