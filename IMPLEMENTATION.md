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
│       └── res/values/styles.xml
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

- Create the low-importance ongoing notification.
- Require notification access before creating the foreground notification or accepting timer actions.
- Expose notification actions for setting duration and disabling the timer.
- Parse and validate the inline notification reply.
- Store timer configuration and active state in `SharedPreferences`.
- Schedule timer expiry and notification refresh callbacks on the main looper.
- Poll the music volume and playback state once per minute.
- Start the timer from the saved duration when polling detects a volume change while inactive.
- Start the timer when polling detects music playback.
- Fade music volume from the captured current level to half that level over 15 seconds.
- Restore the volume captured before fading.
- Ask `MediaSessionAccessService` to pause active media sessions.

Important constants:

- Notification channel: `sleep_timer`
- Notification ID: `1001`
- Minimum duration: `1` minute
- Maximum duration: `1440` minutes
- Fade duration: `15_000` milliseconds

### `MainActivity`

File: `app/src/main/java/com/bas080/autosleepdroid/MainActivity.java`

The launcher activity exists only to start the foreground service and request `POST_NOTIFICATIONS` on Android 13 and newer. It finishes immediately and does not render a layout.

### `BootReceiver`

File: `app/src/main/java/com/bas080/autosleepdroid/BootReceiver.java`

Receives `BOOT_COMPLETED` and starts the foreground service. `SleepTimerService` then reads persisted state. An active timer is restarted at its full configured duration; an inactive timer remains inactive.

### `MediaSessionAccessService`

File: `app/src/main/java/com/bas080/autosleepdroid/MediaSessionAccessService.java`

Extends `NotificationListenerService` to provide the notification-access component required by `MediaSessionManager`. `pauseAll()` sends `pause()` to every active session. Automatic start detection is handled by `SleepTimerService` polling instead of this service.

Android notification access must be granted by the user. The service catches `SecurityException` when access has not been granted.


## State and persistence

State is stored in the `sleep_timer` `SharedPreferences` file:

| Key | Type | Meaning |
|---|---|---|
`active` | boolean | Whether a countdown was active when the service last persisted state |
| `duration_minutes` | integer | The configured duration used for every reset |
The remaining countdown is not persisted. On reboot or service recreation, an active countdown starts from the stored configured duration. An inactive timer is restored as the automatic waiting state; there is no user-controlled off mode.
The remaining countdown is not persisted. On reboot or service recreation, an active timer starts from the stored configured duration.
3. The service loads `active` and `duration_minutes` from preferences.
4. An active countdown starts at the full configured duration.
5. An inactive timer returns to the waiting state and starts automatically when polling detects playback or a volume change.
In-memory state in `SleepTimerService`:

- `active`: timer is counting down.
- `fading`: expiry fade is in progress.
- `timerEndsAt`: wall-clock timestamp used for the current countdown.
- `configuredDurationMinutes`: reset duration.
- `volumeBeforeFade`: music stream volume captured at fade start.
- `suppressVolumeReset`: prevents app-generated volume writes from restarting the timer.
- `fadeStep`: current one-second fade step.
- `lastObservedVolume`: music volume from the previous input poll.
- `lastObservedMediaActive`: playback state from the previous input poll.

Notification access is checked by `MainActivity` before starting `SleepTimerService`. The launcher opens Android's notification-listener settings when access is missing. The service repeats the check as a guard for boot or stale start requests.

## Runtime flows

### Initial launch

1. Android launches `MainActivity`.
2. The activity requests notification permission on Android 13+ if needed.
3. The activity starts `SleepTimerService` as a foreground service.
4. The service creates the notification channel and ongoing notification.
5. The service loads persisted state and duration before rendering the notification.
6. The service restarts the timer if `active` is true and the duration is valid.

### Set duration

1. The user taps `Set duration` in the notification.
2. Android displays the inline `RemoteInput` reply.
3. The service reads the reply under key `duration_minutes`.
4. The value is parsed as an integer.
5. Values from `1` through `1440` are accepted.
6. The duration is persisted, `active` is set to true, and a full countdown starts.
7. Invalid or empty input updates the notification with the accepted range.

The notification always provides only the duration reply action. Its title and text distinguish `Timer waiting`, `Sleep timer`, and `Fading volume`, and include the configured duration.

When the duration action is shown, the saved duration is provided as the notification reply choice and action label. Android exposes this as an editable suggestion; the user can replace it before sending.

### Volume reset

1. Android changes the music stream volume normally.
2. The next one-minute input poll compares the current music volume with the previous sample.
3. If the volume changed and the timer is active but not fading, the service restarts the timer at `configuredDurationMinutes`.
4. If no timer is active but a valid duration is configured, the same path starts a new timer. The normal system volume behavior is preserved; the app does not consume volume-button events.

### Media playback start

1. The one-minute input poll reads `AudioManager.isMusicActive()`.
2. A transition from inactive to active playback starts or resets the timer from the configured duration.
3. The service initializes the previous playback sample as inactive, so playback already active when the service starts is detected on the first poll.
4. After expiry requests a media pause, the service samples the current playback state. A later inactive-to-active transition is then detected as a new start without restarting solely because the pause request failed or is asynchronous.

### Expiry

1. The expiry callback calls `beginFadeOut()`.
2. The service captures the current music stream volume as `volumeBeforeFade`.
3. Fifteen fade steps run at approximately one-second intervals.
4. Before each step, the service compares the current music volume with `lastFadeVolume`. A mismatch means the user changed the volume.
5. On a user volume change, the fade callbacks are cancelled, the old volume is not restored, and the timer restarts at the configured duration.
6. Otherwise, each step interpolates from `volumeBeforeFade` to `volumeBeforeFade / 2` while `suppressVolumeReset` is true. The first step cannot increase the volume.
7. After the final step, all active media sessions are sent `pause()`.
8. The captured pre-fade volume is restored.
9. The timer is marked inactive and the notification reports that media was paused.

The per-step check handles volume changes during the fade immediately; the one-minute poll remains responsible for changes outside the fade.

### Inactive waiting state

1. Expiry marks the timer inactive after pausing media.
2. The notification remains ongoing and reports that it is waiting for playback or a volume change.
3. The configured duration remains visible and is available through the single duration reply action.

### Reboot

1. Android sends `BOOT_COMPLETED`.
2. `BootReceiver` starts `SleepTimerService`.
3. The service loads `active` and `duration_minutes` from preferences.
4. An active timer starts at the full configured duration.
5. An inactive timer remains off.

## Android manifest and permissions

Declared in `app/src/main/AndroidManifest.xml`:

- `FOREGROUND_SERVICE`: permits foreground-service operation.
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK`: required for the media playback foreground-service type on newer Android versions.
- `MODIFY_AUDIO_SETTINGS`: permits changing the music stream volume.
- `POST_NOTIFICATIONS`: required for notification delivery on Android 13+.
- `RECEIVE_BOOT_COMPLETED`: permits reboot restoration.
- `BIND_NOTIFICATION_LISTENER_SERVICE`: binds Android to the media-control notification listener. The user must still grant notification access in system settings.

## Build and release

Local debug build:

```sh
./gradlew assembleDebug
```

Install on a connected device or emulator:

```sh
./gradlew installDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

The GitHub Actions workflow in `.github/workflows/android-release.yml`:

- Builds a rolling prerelease named `latest` for pushes to `master`.
- Uploads `auto-sleep-droid-latest.apk` as both a workflow artifact and release asset.
- Builds a versioned release for tags matching `v*`, for example `v1.0.0`.
- Uploads the versioned asset as `auto-sleep-droid-v1.0.0.apk`.
- Uses Java 17, compile SDK 35, and build tools 35.0.0.

## Known limitations and risks

- There are no automated unit or instrumentation tests yet.
- This environment has no attached emulator or physical Android device, so runtime notification and media-session behavior needs device testing.
- Android lint may require a JDK version supported by the installed Android lint tooling.
- Input polling runs once per minute, so volume changes and playback starts outside the fade may take up to one minute to be detected.
- The fade target is integer music-stream volume, so rounding can make adjacent fade steps equal on devices with a small volume range. The final restore still uses the captured pre-fade volume.
- Notification access is required to pause all active media apps. Without it, the timer can still fade and restore volume, but media pausing may fail.
- The service returns `START_STICKY`, but Android battery-management policies can still stop or delay background work. Long-duration timer behavior should be tested on target devices.

## Change guidance for AI agents

- Preserve the notification-only UX; do not add a custom app screen unless the product specification changes.
- Keep `configuredDurationMinutes` as the source for timer resets after volume changes and reboot.
- Do not treat app-generated fade/restore volume writes as user volume changes; keep `suppressVolumeReset` around those writes.
- Update `SPEC.md` when product behavior changes and update this file when architecture or runtime behavior changes.
- Run `./gradlew assembleDebug` after Java, manifest, or Gradle changes.
- Do not commit `local.properties`, Gradle caches, or generated build output.
