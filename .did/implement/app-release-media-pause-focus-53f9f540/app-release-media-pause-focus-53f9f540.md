---
title: Release media pause audio focus when the user registers as awake
status: open
priority: 3
issue-type: task
created-at: "2026-11-20T01:05:13.722163+00:00"
---

Verification request: audio should resume playing after the wake-up alarm is cancelled (notification click or 3 snoozes via phone flips), when media is available. Finding: it could not resume, by design of the audio focus handling.

## Problem

`pauseMediaViaAudioFocus()` (MainService.kt) requests `AUDIOFOCUS_GAIN_TRANSIENT` with USAGE_MEDIA when media is paused at timer expiry, but the `AudioFocusRequest` was a local variable: never stored, never abandoned. Media apps that paused on that focus loss are never handed back `AUDIOFOCUS_GAIN`, so they stay paused after the alarm stops. `handleAwakeAction()` only abandoned the alarm-sound focus (`alarmAudioFocusRequest`). With no media available, nothing changes as expected.

## Changes

- `pauseMediaViaAudioFocus()` now stores `mediaPauseFocusRequest` on the service.
- New `abandonMediaPauseFocus()` releases it and logs that players may resume.
- `handleAwakeAction()` (single funnel for "I'm Awake" notification click, notification tap when awake, and the 3-snoozes threshold including phone flips) calls `abandonMediaPauseFocus()` right after the alarm teardown.
- `MainServiceTest.testAwakeActionReleasesMediaPauseFocus` verifies the focus is captured at pause and abandoned (same request instance) when awake is registered.

## Verification

- `./gradlew :app:testDebugUnitTest :app:detekt` passes.
- Physical verification pending: nightly playback paused at expiry must resume when "I'm Awake" is clicked (app-dependent: players resume on gaining audio focus after responding to the transient loss).
