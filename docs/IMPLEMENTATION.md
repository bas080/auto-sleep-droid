# Auto Sleep Droid Implementation Guide

## Purpose

This document describes the implementation that currently exists in the repository. Use it as the code-oriented source of truth when modifying the app.

The app is an Android sleep timer controlled from the notification shade. The main activity UI prints a list of debug events, one per line.

## Project structure

```text
.
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/bas080/autosleepdroid/
│       │   ├── BootReceiver.java
│       │   ├── EventLogger.java
│       │   ├── MainActivity.java
│       │   └── SleepTimerService.java
│       └── res/
│           ├── layout/
│           │   └── activity_main.xml
│           ├── values/
│           │   ├── strings.xml
│           │   └── styles.xml
│           └── values-es/
│               └── strings.xml
├── .github/workflows/android-release.yml
├── README.md
├── docs/
│   ├── IMPLEMENTATION.md
│   ├── PERFORMANCE.md
│   └── SPEC.md
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

- Create the ongoing notification (`setOngoing(true)`) representing one of four system states: `Off`, `Waiting`, `Active`, or `Fading`, using notification channel importance `IMPORTANCE_HIGH` and `PRIORITY_HIGH` with `setOnlyAlertOnce(false)` on `Active` state to pop up a heads-up banner when media playback starts.
- Omit a content intent from the notification so tapping/clicking expands or collapses the notification rather than launching an activity.
- Expose notification actions for `Set Timer` (with numeric keypad `RemoteInput`), `Turn Off` (when enabled), and `Turn On` (when Off).
- Parse and validate the inline notification reply, gracefully defaulting to the previously configured duration or 20-minute default on invalid input.
- Store timer configuration (`duration_minutes`), enabled state (`active`), and wall-clock target expiration (`timer_ends_at`) in `SharedPreferences`.
- Schedule exact timer expiry using `AlarmManager.setExactAndAllowWhileIdle()` and handler callbacks on the main looper, falling back to `setAndAllowWhileIdle()` or foreground service callbacks if exact alarm permission is denied.
- Listen for media playback state changes using `AudioManager.AudioPlaybackCallback` (API 26+) instead of periodic polling.
- Register accelerometer sensor listener on a dedicated background `HandlerThread` (with 300ms temporal throttling) and `VOLUME_CHANGED_ACTION` broadcast receiver dynamically only during `Active` and `Fading` states.
- Transition from `Waiting` to `Active` when playback callback detects active music playback while enabled, and reset an `Active` or `Fading` countdown when volume changes or a phone flip gesture occurs.
- Fade music volume from the captured current level to zero over 30 seconds upon expiry using an ease-out quadratic curve (starting fast and slowing down).
- Request transient audio focus (`AudioManager.requestAudioFocus`) to pause active media playback, restore pre-fade volume after media is paused, and revert to the `Waiting` state.
- Trigger a short, faint haptic feedback pulse (`Vibrator`) upon duration replies, turning off, volume button resets, and flip gestures.
- Log lifecycle and state events to `EventLogger`.

Important constants:

- Notification channel: `sleep_timer`
- Notification ID: `1001`
- Minimum duration: `1` minute
- Maximum duration: `1440` minutes (24 hours)
- Fade duration: `30_000` milliseconds

### `MainActivity`

File: `app/src/main/java/com/bas080/autosleepdroid/MainActivity.java`

The launcher activity starts `SleepTimerService`, requests `POST_NOTIFICATIONS` on Android 13+, and displays a real-time event log UI (`activity_main.xml` with `ScrollView` and monospace `TextView`). It listens to `EventLogger` for live log updates and scrolls to the newest line.

### `EventLogger`

File: `app/src/main/java/com/bas080/autosleepdroid/EventLogger.java`

Centralized logging utility that formats event lines with timestamps (`yyyy-MM-dd HH:mm:ss - <message>`). Keeps logs bounded up to 500 lines in memory and `SharedPreferences`, notifying UI listeners of new events.

### `BootReceiver`

File: `app/src/main/java/com/bas080/autosleepdroid/BootReceiver.java`

Receives `BOOT_COMPLETED`, logs the reboot event, and starts the foreground service. `SleepTimerService` then reads persisted state. If previously in an enabled/running state (`Waiting`, `Active`, `Fading`), it restores to the `Waiting` state using the configured duration; if explicitly in `Off` state, it remains `Off`.

### `MediaSessionAccessService`

File: `app/src/main/java/com/bas080/autosleepdroid/MediaSessionAccessService.java`

Extends `NotificationListenerService` to provide the notification-access component required by `MediaSessionManager`. `pauseAll()` sends `pause()` to every active session. Logs pause operations and active session counts to `EventLogger`. Automatic start detection is handled by `SleepTimerService` polling instead of this service.

Android notification access must be granted by the user. The service catches `SecurityException` when access has not been granted.


## State and persistence

Timer state is stored in the `sleep_timer` `SharedPreferences` file:

| Key | Type | Meaning |
|---|---|---|
| `active` | boolean | Whether the timer is enabled (`Waiting`/`Active`/`Fading`) vs explicitly `Off` |
| `duration_minutes` | integer | The configured duration used for every reset |
| `timer_ends_at` | long | Wall-clock timestamp (millis) when active timer expires |

Event log history is stored in the `event_logger` `SharedPreferences` file:

| Key | Type | Meaning |
|---|---|---|
| `logs` | string | Newline-separated event log entries (capped at 500 lines) |

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
- `fadeStep`: current fade step (30 steps across 30 seconds).
- `restoreVolumeRunnable`: delayed runnable executing 500ms after pause to restore pre-fade volume.
- `lastObservedVolume`: music volume from the previous input poll.
- `lastObservedMediaActive`: playback state from the previous input poll.

## Runtime flows

### Initial launch

1. Android launches `MainActivity`.
2. `MainActivity` displays the event log UI and logs its creation.
3. The activity requests notification permission (`POST_NOTIFICATIONS`) on Android 13+ if needed.
4. Once notification permission is granted (or immediately on Android < 33), the activity starts `SleepTimerService` as a foreground service.

### Set Timer action

1. The user taps `Set Timer` in the notification (available in `Off`, `Waiting`, `Active`, and `Fading` states).
2. Android displays the inline `RemoteInput` reply with a numeric keyboard layout (`InputType.TYPE_CLASS_NUMBER`).
3. The service reads the reply under key `duration_minutes`.
4. If valid (integer 1 through 1440), the duration is persisted and `enabled` is set to true.
5. If invalid or empty, the duration falls back safely to the previously configured duration or the 20-minute default, and `enabled` is set to true.
6. If media is currently active, the timer transitions to `Active` and starts counting down; otherwise, it enters `Waiting`.
7. `EventLogger` logs the configuration action.

### Turn Off action

1. The user taps `Turn Off` in the notification (available in `Waiting`, `Active`, and `Fading` states).
2. Any active countdown or fade callbacks are cancelled.
3. `enabled` is set to false and persisted in `SharedPreferences`.
4. Current volume and media playback remain unchanged.
5. The notification updates to the `Off` state ("Sleep timer is off").
6. `EventLogger` logs the turn off action.

### Volume reset, Gesture flip & Media playback start

1. Android changes music stream volume normally, a phone flip gesture is detected, or playback starts.
2. Event listeners (`AudioPlaybackCallback`, `VOLUME_CHANGED_ACTION` receiver, accelerometer sensor) notify `SleepTimerStateMachine` immediately of events.
3. If enabled and active, a volume change or phone flip gesture resets the countdown to `configuredDurationMinutes`.
4. If enabled and waiting, active media playback (`isMusicActive()`) transitions the timer to `Active`.
5. If in `Fading` state, a volume change cancels fade and preserves current volume, while a phone flip gesture cancels fade, restores pre-fade volume, and resets the timer to `Active`.
6. Changes in volume, flip gesture detection, or media playback state are logged to `EventLogger`.

### Expiry

1. The expiry callback calls `beginFadeOut()`.
2. The service captures current music stream volume as `volumeBeforeFade`.
3. Thirty fade steps run at 1-second intervals using an ease-out quadratic curve.
4. User volume changes during fade cancel the fade and restart the timer.
5. After the final step, media is paused by requesting transient audio focus (`pauseMediaViaAudioFocus()`), pre-fade volume is restored after a short delay (500ms) to allow media to pause silently, and the timer returns to `Waiting`.
6. Expiry and fade steps are logged to `EventLogger`.

### Reboot

1. Android sends `BOOT_COMPLETED`.
2. `BootReceiver` logs the boot event and starts `SleepTimerService`.
3. The service loads `active` (`enabled`) and `duration_minutes` from preferences.
4. If `enabled` was true, the timer restores to `Waiting` state (or `Active` if media is playing).
5. If explicitly `Off`, the timer remains `Off`.

## Android manifest and permissions

Declared in `app/src/main/AndroidManifest.xml`:

- `FOREGROUND_SERVICE`: permits foreground-service operation.
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK`: required for the media playback foreground-service type on newer Android versions.
- `MODIFY_AUDIO_SETTINGS`: permits changing the music stream volume.
- `VIBRATE`: permits triggering haptic feedback vibration pulses.
- `SCHEDULE_EXACT_ALARM`: permits scheduling exact alarms with `AlarmManager`.
- `POST_NOTIFICATIONS`: required for notification delivery on Android 13+.
- `RECEIVE_BOOT_COMPLETED`: permits reboot restoration.

## Build and release

Run unit tests locally:

```sh
./gradlew test
```

Local debug build:

```sh
./gradlew assembleDebug
```

CI workflows (`.github/workflows/android-release.yml`) automatically run `./gradlew test` prior to building debug/release APK artifacts, utilizing Gradle dependency caching for fast workflow execution.

Install on a connected device or emulator:

```sh
./gradlew installDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Change guidance for AI agents

- Preserve the event log UI in `MainActivity` and `EventLogger`.
- Keep `configuredDurationMinutes` as the source for timer resets after volume changes and reboot.
- Do not treat app-generated fade/restore volume writes as user volume changes; keep `suppressVolumeReset` around those writes.
- Update `docs/SPEC.md` when product behavior changes and update this file when architecture or runtime behavior changes.
- Run `./gradlew assembleDebug` after Java, manifest, or Gradle changes.
