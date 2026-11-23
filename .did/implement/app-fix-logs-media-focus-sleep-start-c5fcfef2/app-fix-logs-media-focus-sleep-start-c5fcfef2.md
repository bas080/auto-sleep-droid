---
title: Fix misleading audio focus logging, release media focus on turn-off, and stop sleep start churn
status: open
priority: 3
issue-type: task
created-at: "2026-11-23T13:56:54.793730+00:00"
---

Fixes the three findings from the real-night event log analysis (`.did/refine/app-check-screen-sleep-battery-857775e5/`). Stacked on PR #240 (needs its `mediaPauseFocusRequest` tracking); base is the #240 branch until it merges.

## Changes

1. **Log correctness:**
   - `abandonAlarmAudioFocus()` no longer logs "Abandoned audio focus for wake-up alarm" when no alarm focus request was actually held (the real-night log showed this false signal during a plain timer turn-off at 10:00:53).
   - `releaseWakeLock()` now logs only when a lock was actually held, and includes the hold duration (e.g. "Released screen wake lock (held for 3s)"), which makes screen-wake battery diagnosis possible from the Logs screen.
   - Routine churn logs demoted to LEVEL_LOW: "Music playback started/stopped", "Volume changed to N", and the new kept-existing sleep start note; LEVEL_HIGH is reserved for state-changing moments.

2. **Media focus release on every turn-off path:** `handleOffStateTransition()` now calls `abandonMediaPauseFocus()`, so the transient USAGE_MEDIA focus captured at the nightly pause is released whenever the timer session ends - manual Disable, DND inactive (manual toggle or the auto-DND feature), or the awake action. Paused players then receive AUDIOFOCUS_GAIN and can resume. The explicit release in `handleAwakeAction()` stays (idempotent).

3. **Sleep start churn:** playback stops during an ACTIVE timer re-recorded the sleep start on every media blip (the real-night log showed 7 skip-storm overwrites in 3 minutes, and a morning stop at 09:56 moved the bedtime anchor). Both rec (`onPlaybackStateChanged`, `onPauseMedia`) now only record when there is no sleep start yet or the existing one is older than 14 hours (first pause of a session wins). New tests cover the skip storm, the stale-session re-record, and focus release on turn-off.

## Verification

- Local JDK unavailable; `./gradlew :app:testDebugUnitTest :app:detekt` verified by CI ("Test, Build & Publish APK").
- Device checks post-merge: no false "Abandoned audio focus" line on timer disable; skip storms leave one sleep-start entry; wake lock releases carry duration.
