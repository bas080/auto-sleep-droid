---
title: Improve flip detection for alarm ringing and fade-out moments
status: open
priority: 4
issue-type: task
created-at: "2026-11-20T00:50:04.778542+00:00"
---

`PhoneFlipDetector` (PhoneFlipDetector.kt) is a single-sample accelerometer detector: it fires when z < -7.0 m/s² (face down) and re-arms when z > 0. `MainService.updateListenersRegistration()` (MainService.kt:722-728) starts it only while `isFading || isWakeUpAlarmRinging`.

## Moments flip detection is used (complete list)

- **Wake-up alarm ringing:** flip face-down snoozes the alarm (`handlePhoneFlip` → `snoozeWakeUpAlarmWithThreshold("phone flip")`, counts toward the 3-snooze awake threshold).
- **Fade-out running:** flip face-down resets the sleep timer to full duration and cancels the fade (`cancelFadeForVolumeChange`).

Flip detection is NOT active in any other state: while snoozed, while timer is ACTIVE (not fading), WAITING, or OFF.

## Improvement candidates

1. **Debounce/confirmation:** single z-sample below -7.0 triggers instantly. Require two consecutive samples (or a ~300-500 ms hold) plus a gravity-magnitude check (|x²+y²+z² − g| tolerance) to reject shakes, pocket movement, and free-fall artifacts.
2. **Already face-down when alarm rings:** detector starts with `isFaceDown = false`, so a phone left face-down overnight never snoozes on its own and requires lift-then-flip-down. Decide if that is the wanted UX or if an already-face-down start should count (auto-snooze risk) after a grace period.
3. **Snooze state gap:** while snoozed, flip does nothing (only volume buttons work). Enabling the detector while snoozed would make flip consistent with the volume-key snooze path.
4. **Fade-path haptics:** alarm flip path vibrates (`onTriggerVibration`), the fade-reset path does not. Adding haptic feedback keeps physical-input feedback consistent.
5. **Tilt-robust threshold:** z-only fails for tilted placements (phone on a slope). Consider a tilt angle (atan2 of z vs horizontal plane) with a slightly stricter angle than 45°, or use the gravity sensor where available.

### Decisions from @bas080
- Improvements 1, 4, and 5 moved to implementation: sample confirmation with hold time, fade-path haptics, tilt-robust threshold. See `.did/implement/app-improve-flip-confirmation-a88b3942`. Implemented 2026-11-20 on branch vibe/improve-flip-detection-f96666.
- Improvement 3 (detector active while snoozed) rejected: battery life is more important.
- Improvement 2 still needs user input after clarification.

### Questions for @bas080
- Improvement 2: should a phone that is already face-down when the alarm starts ringing snooze by itself (optionally after a grace period), or keep the current lift-then-flip-down requirement?
