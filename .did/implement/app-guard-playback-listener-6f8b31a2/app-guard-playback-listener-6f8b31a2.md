---
title: Silently ignore audio playback events while the sleep timer is off
status: open
priority: 3
issue-type: task
---

Reported from a real-night log (v0.0.42) by @bas080:

- 01:17 fade-out starts, timer expires, volume restored -> service re-arms as WAITING (playback callback registered).
- 06:15 "DND inactive: turning OFF sleep timer" -> handleOffStateTransition unregisters the playback callback.
- 10:31 "Music playback stopped" appears while the timer has been off since 06:15: the only producer of that entry is the AudioPlaybackCallback, so a registration survived the OFF transition (leaked listener). The wake-up alarm tone also shows up as "Music playback" events.
- 14:02-14:37 repeated "Music playback started/stopped" entries while the user had the sleep timer off.

## Root cause

Every state transition on paper unregisters the playback callback, but the log proves a registration was alive while OFF. Possible leak paths: silent no-op when audioManager is null, a transition that did not reach the unregistration (exception mid-transition), or delayed callback deliveries racing the transition.

## Fix

- Guard at the top of onPlaybackStateChanged: when state == OFF, unregister the playback callback and return. Being OFF becomes self-healing: the first stray event tears the listener down instead of logging; the wake-up alarm tone can no longer surface as "Music playback" entries.
- Regression tests: (1) turning the timer off from WAITING unregisters the callback; (2) a playback event in OFF state produces no log entries and leaves the callback unregistered; (3) state stays OFF.

## Verification

- Robolectric unit tests on CI (no local JDK).
