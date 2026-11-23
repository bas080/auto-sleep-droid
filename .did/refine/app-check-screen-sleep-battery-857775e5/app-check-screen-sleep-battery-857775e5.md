---
title: Verify screen-sleep battery report against v0.0.37 behavior
status: open
priority: 4
issue-type: task
created-at: "2026-11-23T13:20:58.435241Z"
---

v0.0.37 report: "sleep timer now doesn't allow the screen to sleep, so the phone stays on ... eating up all the battery".

## Finding (analysis, no code change)

- The sleep timer itself (ACTIVE / WAITING / FADING overnight) holds no wake lock and sets no keep-screen-on flag; the display sleeps on the normal system timeout. Nothing in v0.0.37, v0.0.38, or PR #240 holds the screen during the timer.
- The only screen-on mechanism is intentional: when the wake-up alarm fires, MainService acquires a FULL_WAKE_LOCK (ACQUIRE_CAUSES_WAKEUP) so volume-button snoozing works immediately; it is capped at 30 s and released on snooze/dismiss. Each snooze ring re-awakens the screen for up to 30 s, bounded by the 3-snoozes auto-dismiss.
- Wake-lock code is byte-identical between v0.0.37 and current master (the only diff is refactor extraction into stopAndResetWakeAlarm, same release points).

## Conclusion / open question for @bas080
- Not a regression and not fixed since v0.0.37; behavior is unchanged. If a real overnight drain was observed, likely causes worth checking on device: alarm ringing unattended overnight (screen on 30 s per cycle at the wake goal time), system-level charging/keep-awake, or a misattributed drain. Can add a diagnostic or cap screen wake behavior if the user reproduces it.

## Follow-up: could the media-pause mechanism keep the screen on?
- No. Pausing media uses a AUDIOFOCUS_GAIN_TRANSIENT request against AudioManager (MainService pauseMediaViaAudioFocus); audio focus only affects audio policy (players pause/duck). It has no path to the display and never touches PowerManager.
- Exhaustive check across app (Kotlin + XML): the only PowerManager interaction in the entire app is the alarm wake lock; no keepScreenOn attribute in any layout, no window flags in activities, and the ongoing notification is a plain silent CATEGORY_SERVICE notification.
- If anything the pause helps: media/video apps release their own keep-screen-on flags when playback stops.
