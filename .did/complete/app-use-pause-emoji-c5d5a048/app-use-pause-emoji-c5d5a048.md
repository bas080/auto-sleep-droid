---
title: Use pause emoji instead of music note for fade-out time in notification
status: resolved
priority: 3
issue-type: task
created-at: "2026-11-19T21:54:32.679980+00:00"
---

Replace the music note (U+266A) shown before the fade-out time in the ongoing notification with a pause sign (U+23F8 + U+FE0E text-presentation selector), because a pause better represents what happens at that time (media playback fades out and pauses).

## Changes

- `MainService.getNotificationStatusText()` now prefixes the fade-out time with `\u23F8\uFE0E` instead of `\u266A`, matching the text-presentation style already used by the wake alarm clock symbol.
- `manual.html` notification-controls section updated to document the pause symbol.

## Verification

- `./gradlew test` and `./gradlew detekt` pass.
- Visual confirmation of the notification on a physical device pending user verification.
