---
title: Harden phone flip confirmation during alarm ringing and fade-out
status: open
priority: 4
issue-type: task
created-at: "2026-11-20T01:05:25.016192+00:00"
---

Implements improvements 1, 4, and 5 from `.did/refine/app-improve-flip-detection-99888eae/` (chosen by @bas080). Improvement 3 was rejected for battery reasons; the detector stays off while the alarm is snoozed.

## Changes

- `PhoneFlipDetector` no longer fires on a single face-down accelerometer sample. It now requires a sustained hold: the device must remain face-down and stationary (gravity magnitude within tolerance of STANDARD_GRAVITY) for 400 ms before the callback fires; any unstable or non-face-down sample cancels the pending flip. State re-arms only after the device is back above face-up threshold, so one flip fires at most once.
- Tolerance to tilted placements improved: the face-down threshold changed from z < -7.0 (about 45 degrees from vertical) to z < -STANDARD_GRAVITY * 0.5 (60 degrees from vertical), so a phone resting on a mild slope still qualifies.
- `MainService.handlePhoneFlip()` fade-reset branch now triggers haptic feedback via `onTriggerVibration()`, matching the alarm-snooze branch.
- Tests: injectable clock in `PhoneFlipDetectorTest` covers single-sample rejection, sustained-fire, once-per-flip, re-arm, zero-gravity and magnitude-outlier rejection, shallow tilt rejection; the service-level tests simulate sustained flips.

## Verification

- `./gradlew :app:testDebugUnitTest :app:detekt` passes.
- Physical verification pending: flip to snooze while alarm rings and flip to reset during fade must still work, now requiring the phone to be held face-down briefly instead of a passing tilt.
