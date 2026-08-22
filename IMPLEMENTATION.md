# Auto Sleep Droid Implementation Guide

## Purpose

This document describes the implementation that currently exists in the repository. Use it as the code-oriented source of truth when modifying the app.

The app is a notification-only Android sleep timer. There is no custom settings screen or timer screen.

## Project structure

```text
.
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/bas080/autosleepdroid/
│       │   ├── BootReceiver.java
│       │   ├── MainActivity.java
│       │   ├── MediaSessionAccessService.java
│       │   └── SleepTimerService.java
│       └── res/
│           ├── values/
│           │   ├── strings.xml
│           │   └── styles.xml
│           └── values-es/
│               └── strings.xml
├── .github/workflows/android-release.yml
├── README.md
├── SPEC.md
├── build.gradle
├── gradle.properties
├── gradlew
└── settings.gradle
```

## Main components

### `SleepTimerService`

File: `app/src/main/java/com/bas080/autosleepdroid/SleepTimerService.java`

This is the main application component. It is a foreground service with the `mediaPlayback` foreground-service type.

Responsibilities:

- Create the low-importance ongoing notification representing one of five system states: `Permissions Pending`, `Off`, `Waiting`, `Active`, or `Fading`.
- Omit a content intent from the non-pending notification so tapping/clicking expands or collapses the notification rather than launching an activity.
- Display a `Permissions Pending` notification prompting the user to grant notification access when permissions are missing.
- Expose notification actions for `Set Timer` (with numeric keypad `RemoteInput`) and `Turn Off` (when enabled).
- Parse and validate the inline notification reply, gracefully defaulting to the previously configured duration or 20-minute default on invalid input.
- Store timer configuration (`duration_minutes`) and enabled state (`active`) in `SharedPreferences`.
- Schedule timer expiry and notification refresh callbacks on the main looper.
- Poll the music volume and playback state once per minute.
- Transition from `Waiting` to `Active` when polling detects active music playback or volume changes while enabled.
- Fade music volume from the captured current level to half that level over 30 seconds upon expiry.
- Restore the volume captured before fading.
- Ask `MediaSessionAccessService` to pause active media sessions and revert to the `Waiting` state.

Important constants:

- Notification channel: `sleep_timer`
- Notification ID: `1001`
- Minimum duration: `1` minute
- Maximum duration: `1440` minutes (24 hours)
- Fade duration: `30_000` milliseconds

### `MainActivity`

File: `app/src/main/java/com/bas080/autosleepdroid/MainActivity.java`

The launcher activity exists only to start the foreground service and request `POST_NOTIFICATIONS` on Android 13 and newer. It finishes immediately and does not render a layout.

### `BootReceiver`

File: `app/src/main/java/com/bas080/autosleepdroid/BootReceiver.java`

Receives `BOOT_COMPLETED` and starts the foreground service. `SleepTimerService` then reads persisted state. If previously in an enabled/running state (`Waiting`, `Active`, `Fading`), it restores to the `Waiting` state using the configured duration; if explicitly in `Off` state, it remains `Off`.

### `MediaSessionAccessService`

File: `app/src/main/java/com/bas080/autosleepdroid/MediaSessionAccessService.java`

Extends `NotificationListenerService` to provide the notification-access component required by `MediaSessionManager`. `pauseAll()` sends `pause()` to every active session. Automatic start detection is handled by `SleepTimerService` polling instead of this service.

Android notification access must be granted by the user. The service catches `SecurityException` when access has not been granted.


## State and persistence

State is stored in the `sleep_timer` `SharedPreferences` file:

| Key | Type | Meaning |
|---|---|---|
| `active` | boolean | Whether the timer is enabled (`Waiting`/`Active`/`Fading`) vs explicitly `Off` |
| `duration_minutes` | integer | The configured duration used for every reset |

The remaining countdown is not persisted. On reboot or service recreation, an enabled timer restores to the `Waiting` state (or starts counting down if media is currently playing) using the stored configured duration.

When `duration_minutes` is absent, `SleepTimerService` uses the 20-minute default. A valid user reply replaces and persists this value.

In-memory state in `SleepTimerService`:

- `enabled`: timer is enabled (`Waiting`, `Active`, or `Fading`) vs `Off`.
- `active`: timer countdown is currently running (`Active` state).
- `fading`: expiry fade is in progress (`Fading` state).
- `timerEndsAt`: wall-clock timestamp used for the current countdown.
- `configuredDurationMinutes`: reset duration.
- `volumeBeforeFade`: music stream volume captured at fade start.
- `suppressVolumeReset`: prevents app-generated volume writes from restarting the timer.
- `fadeStep`: current one-second fade step.
- `lastObservedVolume`: music volume from the previous input poll.
- `lastObservedMediaActive`: playback state from the previous input poll.

## Runtime flows

### Initial launch & Permissions Pending

1. Android launches `MainActivity`.
2. The activity requests notification permission (`POST_NOTIFICATIONS`) on Android 13+ if needed.
3. Once notification permission is granted (or immediately on Android < 33), the activity starts `SleepTimerService` as a foreground service.
4. The service checks for notification listener access (`hasNotificationAccess()`).
5. If missing, the service immediately displays a `Permissions Pending` notification ("Setup required Tap to grant permissions"). Tapping it opens Android notification listener settings.
6. Upon granting permissions, the service transitions to the `Waiting` state using the default or saved duration.

### Set Timer action

1. The user taps `Set Timer` in the notification (available in `Off`, `Waiting`, `Active`, and `Fading` states).
2. Android displays the inline `RemoteInput` reply with a numeric keyboard layout (`InputType.TYPE_CLASS_NUMBER`).
3. The service reads the reply under key `duration_minutes`.
4. If valid (integer 1 through 1440), the duration is persisted and `enabled` is set to true.
5. If invalid or empty, the duration falls back safely to the previously configured duration or the 20-minute default, and `enabled` is set to true.
6. If media is currently active, the timer transitions to `Active` and starts counting down; otherwise, it enters `Waiting`.

### Turn Off action

1. The user taps `Turn Off` in the notification (available in `Waiting`, `Active`, and `Fading` states).
2. Any active countdown or fade callbacks are cancelled.
3. `enabled` is set to false and persisted in `SharedPreferences`.
4. Current volume and media playback remain unchanged.
5. The notification updates to the `Off` state ("Sleep timer is off").

### Volume reset & Media playback start

1. Android changes music stream volume normally or playback starts.
2. The one-minute input poll compares volume and playback status with previous samples.
3. If enabled and active, a volume change resets the countdown to `configuredDurationMinutes`.
4. If enabled and waiting, active media playback (`isMusicActive()`) transitions the timer to `Active`.
5. If in `Fading` state, a volume change cancels the fade, preserves the new volume, and resets the timer to `Active`.

### Expiry

1. The expiry callback calls `beginFadeOut()`.
2. The service captures current music stream volume as `volumeBeforeFade`.
3. Fifteen fade steps run at 1-second intervals.
4. User volume changes during fade cancel the fade and restart the timer.
5. After the final step, active media sessions are paused (`MediaSessionAccessService.pauseAll()`), pre-fade volume is restored, and the timer returns to `Waiting`.

### Reboot

1. Android sends `BOOT_COMPLETED`.
2. `BootReceiver` starts `SleepTimerService`.
3. The service loads `active` (`enabled`) and `duration_minutes` from preferences.
4. If `enabled` was true, the timer restores to `Waiting` state (or `Active` if media is playing).
5. If explicitly `Off`, the timer remains `Off`.

## Android manifest and permissions

Declared in `app/src/main/AndroidManifest.xml`:

- `FOREGROUND_SERVICE`: permits foreground-service operation.
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK`: required for the media playback foreground-service type on newer Android versions.
- `MODIFY_AUDIO_SETTINGS`: permits changing the music stream volume.
- `POST_NOTIFICATIONS`: required for notification delivery on Android 13+.
- `RECEIVE_BOOT_COMPLETED`: permits reboot restoration.
- `BIND_NOTIFICATION_LISTENER_SERVICE`: binds Android to the media-control notification listener. The user must still grant notification access in system settings.

## Build and release

Run unit tests locally:

```sh
./gradlew test
```

Local debug build:

```sh
./gradlew assembleDebug
```

CI workflows (`.github/workflows/android-release.yml`) automatically run `./gradlew test` prior to building debug/release APK artifacts.

Install on a connected device or emulator:

```sh
./gradlew installDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Change guidance for AI agents

- Preserve the notification-only UX; do not add a custom app screen unless the product specification changes.
- Keep `configuredDurationMinutes` as the source for timer resets after volume changes and reboot.
- Do not treat app-generated fade/restore volume writes as user volume changes; keep `suppressVolumeReset` around those writes.
- Update `SPEC.md` when product behavior changes and update this file when architecture or runtime behavior changes.
- Run `./gradlew assembleDebug` after Java, manifest, or Gradle changes.
