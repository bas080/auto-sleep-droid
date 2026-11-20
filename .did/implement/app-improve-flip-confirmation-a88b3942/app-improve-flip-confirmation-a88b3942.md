---
title: Harden phone flip confirmation during alarm ringing and fade-out
status: open
priority: 4
issue-type: task
created-at: "2026-11-20T01:05:25.016192+00:00"
---

Implements improvements 1, 4, and 5 from `.did/refine/app-improve-flip-detection-99888eae/` (chosen by @bas080). Improvement 3 was rejected for battery reasons; the detector stays off while the alarm is snoozed.

## Changes

- `PhoneFlipDetector` was redesigned around the gesture model defined by @bas080: a flip is the device transitioning from one resting position to another (side-to-side, face-up-to-face-down, or back), regardless of which way the screen faces. The act of flipping itself triggers the snooze or the play-time extension.
- The detector tracks the settled orientation (six quantized orientations from the dominant gravity axis). It fires when the device leaves its settled orientation and then re-settles (in any orientation, including the original one) and holds it for 400 ms.
- Stationarity gating: samples whose gravity magnitude deviates more than 1.5 m/s^2 from standard gravity are treated as in-motion and reset the pending flip, so shakes and drops do not fire.
- This also resolves refinement question 2: a phone already in any resting position when the alarm starts never snoozes by itself; the user must actually flip it.
- `MainService.handlePhoneFlip()` fade-reset branch now triggers haptic feedback via `onTriggerVibration()`, matching the alarm-snooze branch.
- Detector remains off while snoozed (rejected for battery life).
- Tests: `PhoneFlipDetectorTest` covers settle-first-then-flip, firing in any direction including flipping back, once-per-flip, in-motion sample rejection, and the two service-level gestures (fade reset and face-down-start lift-and-flip snooze).

## Verification

- `./gradlew :app:testDebugUnitTest :app:detekt` passes.
- Physical verification pending: a brief deliberate flip should snooze the ringing alarm and extend play during fade, in either direction, with haptic feedback on the fade path.
