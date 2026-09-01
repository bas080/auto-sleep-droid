# Auto Sleep Droid Implementation Guide

## Purpose

This document describes the implementation that currently exists in the repository. Use it as the code-oriented source of truth when modifying the app.

The app is an Android sleep timer controlled from the notification shade. The main activity UI displays a real-time event log for debugging.

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
│       │   ├── GoalSettingsDialogActivity.java
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
│   ├── EVENTS_AND_STATES.md
│   ├── IMPLEMENTATION.md
│   ├── IMPORT_EXPORT.md
│   ├── NOTIFICATION_GOAL_INPUT_OPTIONS.md
│   ├── NOTIFICATION_INPUT_OPTIONS.md
│   ├── PERFORMANCE.md
│   ├── SPEC.md
│   └── UPDATE_NOTIFICATIONS.md
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

- Create the low-importance ongoing notification (`setOngoing(true)`) representing one of four system states: `Off`, `Waiting`, `Active`, or `Fading`.
- Display compact/concise text when collapsed (`setContentText`) and detailed contextual information when expanded (`Notification.BigTextStyle.bigText`).
- Set content intent targeting `MainActivity` so tapping the notification opens `MainActivity`.
- Expose notification actions for `Sleep <duration>` (toggles timer off when enabled, opens `RemoteInput` when disabled) and `Set Goal` / `Alarm HH:MM` (disables goal when set, launches `GoalSettingsDialogActivity` when disabled).
- Parse and validate the inline notification reply using `parseDurationMinutes` to support natural duration inputs (`30`, `1h`, `2h15m`, ignoring seconds specifiers like `2h10m5s`), gracefully defaulting to the previously configured duration or 20-minute default on malformed/invalid input.
- Store timer configuration (`duration_minutes`), enabled state (`active`), wall-clock target expiration (`timer_ends_at`), wake-up goal settings, and last wake-up time (`last_wake_up_hour`, `last_wake_up_minute`) in `SharedPreferences`.
- Schedule exact timer expiry using `AlarmManager.setExactAndAllowWhileIdle()` and handler callbacks on the main looper, falling back to `setAndAllowWhileIdle()` or foreground service callbacks if exact alarm permission is denied.
- Listen for media playback state changes using `AudioManager.AudioPlaybackCallback` (API 26+) dynamically only during `Waiting` state instead of periodic polling.
- Register accelerometer sensor listener on a dedicated background `HandlerThread` (with 300ms temporal throttling) and `VOLUME_CHANGED_ACTION` broadcast receiver dynamically during `Active` and `Fading` states, or while the wake-up alarm is ringing.
- Transition from `Waiting` to `Active` when playback callback detects active music playback while enabled, and reset an `Active` or `Fading` countdown when volume changes or a phone flip gesture occurs.
- Fade music volume from the captured current level to zero over 30 seconds upon expiry using an ease-out quadratic curve (starting fast and slowing down).
- Request transient audio focus (`AudioManager.requestAudioFocus`) to pause active media playback, restore pre-fade volume after media is paused (after a short 500ms delay), and revert to the `Waiting` state.
- Schedule/update the `"Auto Sleep"` wake-up alarm via `AlarmManager.setAlarmClock` for the upcoming target wake time within 12 hours prior to the goal time if Smart Wake-Up Goal is enabled. Upon wake-up alarm dismissal (via notification button or volume key), record the last wake-up time (`last_wake_up_hour`, `last_wake_up_minute`) into `SharedPreferences` and schedule the next alarm at the target wake time so an alarm remains set even without active sleep timer activity. Pushing the alarm later to respect minimum sleep duration safeguard only occurs when sleep timer activity is detected within the 12-hour window. When triggered (`ACTION_WAKEUP_ALARM_EXPIRY`), `SleepTimerService` ensures `STREAM_ALARM` is set to an audible baseline level, plays the default system alarm tone using `RingtoneManager` with a 3-minute gentle volume crescendo (ramping volume along a quadratic $progress^2$ exponential curve matching human psychoacoustic loudness perception and sleep cycle arousal transitions within the scientifically optimal 2–5 minute window for startle-free awakening and sleep inertia reduction), and posts a high-priority notification with "Dismiss" and "Snooze" (9 minutes) action buttons. Flipping the phone while the wake-up alarm is ringing or tapping Snooze snoozes the alarm for 9 minutes while keeping the notification open in the notification shade, allowing users to dismiss the alarm when desired via the "Dismiss" action button or hardware volume button press.
- Cancel/dismiss the `"Auto Sleep"` wake-up alarm via `AlarmManager.cancel` on stop or smart alarm cancel in the background.
- Trigger a short, faint haptic feedback pulse (`Vibrator`) upon duration replies, turning off, volume button resets, and flip gestures.
- Log lifecycle and state events to `EventLogger`.

Important constants:

- Notification channel: `sleep_timer`
- Notification ID: `1001`
- Minimum duration: `1` minute
- Maximum duration: `1440` minutes (24 hours)
- Fade duration: `30_000` milliseconds

### `GoalSettingsDialogActivity`

File: `app/src/main/java/com/bas080/autosleepdroid/GoalSettingsDialogActivity.java`

A dialog-themed activity (`@android:style/Theme.Material.Light.Dialog`) launched from Action Slot 3 in the notification shade ("Set Goal" / "Goal HH:MM").

Responsibilities:

- Displays a modal dialog overlay over the foreground app with a `TimePicker` widget for setting target goal time and an `EditText` for configuring minimum sleep duration safeguard in hours (default 7.5h / 450 minutes).
- Includes "OK" and "Cancel" buttons.
- On "OK": saves `wake_up_goal_enabled = true`, `wake_up_goal_hour`, `wake_up_goal_minute`, and `min_sleep_duration_minutes` into `SharedPreferences`, sends `ACTION_REDRAW_NOTIFICATION` intent to `SleepTimerService`, logs the event to `EventLogger`, and calls `finish()`.
- On "Cancel": calls `finish()` without modifying settings.

### `MainActivity`

File: `app/src/main/java/com/bas080/autosleepdroid/MainActivity.java`

The launcher activity starts `SleepTimerService`, requests `POST_NOTIFICATIONS` on Android 13+, prompts for exact alarm permissions on Android 12+, and displays the live event log UI (`activity_main.xml`).

Main Event Log & Import/Export Controls:

- A scrollable `ScrollView` with monospace `TextView` listening to `EventLogger` for live log updates, scrolling automatically to the newest line.
- Main UI action links ("Export" and "Import") rendered in the header's scrollable action link list alongside Releases, GitHub, Issues, and Donate.
- Export Settings Action: Serializes current preferences from `sleep_timer` `SharedPreferences` into a Schema Version 1 JSON string (`JSONObject`), launches Android system share action (`Intent.ACTION_SEND` / `Intent.createChooser` with `text/plain` MIME type and `Intent.EXTRA_TEXT`), and logs the export event to `EventLogger`.
- Import Settings Action: Prompts the user with an instructional `AlertDialog` titled "Import Settings" containing clear instructions and an `EditText` (auto-pasting clipboard string if valid JSON), validates JSON syntax and numeric ranges (`duration_minutes` 1–1440, `wake_up_goal_hour` 0–23, `wake_up_goal_minute` 0–59, `min_sleep_duration_minutes` 1–1440), writes valid values to `SharedPreferences`, sends `ACTION_REDRAW_NOTIFICATION` intent to `SleepTimerService` to recalculate alarms and update notifications, displays a Toast confirmation ("Settings imported successfully"), and logs the event to `EventLogger`. Invalid inputs leave existing preferences unchanged and present an error Toast ("Invalid settings format").

### `EventLogger`

File: `app/src/main/java/com/bas080/autosleepdroid/EventLogger.java`

Centralized logging utility that formats event lines with timestamps (`yyyy-MM-dd HH:mm:ss - <message>`). Keeps logs bounded up to 500 lines in memory and `SharedPreferences`, notifying UI listeners of new events.

### `BootReceiver`

File: `app/src/main/java/com/bas080/autosleepdroid/BootReceiver.java`

Receives `BOOT_COMPLETED`, logs the reboot event, and starts the foreground service. `SleepTimerService` then reads persisted state. If previously in an enabled/running state (`Waiting`, `Active`, `Fading`), it restores to the `Waiting` state using the configured duration; if explicitly in `Off` state, it remains `Off`.

## State and persistence

Timer and Wake-Up Goal state is stored in the `sleep_timer` `SharedPreferences` file:

| Key | Type | Meaning |
|---|---|---|
| `active` | boolean | Whether the timer is enabled (`Waiting`/`Active`/`Fading`) vs explicitly `Off` |
| `duration_minutes` | integer | The configured duration used for every reset |
| `timer_ends_at` | long | Wall-clock timestamp (millis) when active timer expires |
| `wake_up_goal_enabled` | boolean | Whether Smart Wake-Up Goal is enabled |
| `wake_up_goal_hour` | integer | Target goal hour of day (0-23) |
| `wake_up_goal_minute` | integer | Target goal minute (0-59) |
| `min_sleep_duration_minutes` | integer | Safeguard minimum sleep duration in minutes (default 450 = 7.5h) |
| `last_wake_up_hour` | integer | Last scheduled/triggered wake-up hour (0-23) saved upon alarm dismissal |
| `last_wake_up_minute` | integer | Last scheduled/triggered wake-up minute (0-59) saved upon alarm dismissal |

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
- `isWakeUpAlarmRinging`: tracks whether the wake-up alarm is currently ringing.
- `isWakeUpAlarmSnoozed`: tracks whether the wake-up alarm is currently snoozed.

## Runtime flows

### Initial launch

1. Android launches `MainActivity`.
2. `MainActivity` displays the live event log UI.
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

### Set Goal / Goal HH:MM action

1. The user taps `Set Goal` or `Goal HH:MM` in Action Slot 3 of the notification shade.
2. Android launches `GoalSettingsDialogActivity` as a compact modal dialog overlay over the foreground app.
3. The user configures target goal time and minimum sleep duration safeguard, then taps "OK" (or "Stop").
4. `GoalSettingsDialogActivity` saves settings to `SharedPreferences`, logs the event, sends an intent to `SleepTimerService` to recalculate alarms and redraw the notification, and finishes.

### Turn Off action

1. The user taps `Turn Off` in the notification (available in `Waiting`, `Active`, and `Fading` states).
2. Any active countdown or fade callbacks are cancelled.
3. `enabled` is set to false and persisted in `SharedPreferences`.
4. Dismisses any scheduled `"Auto Sleep"` alarm via `AlarmManager.cancel` in the background.
5. Current volume and media playback remain unchanged.
6. The notification updates to the `Off` state ("Timer off").
7. `EventLogger` logs the turn off action.

### Volume reset, Gesture flip & Media playback start

1. Android changes music stream volume normally, a phone flip gesture is detected, or playback starts.
2. Event listeners (`AudioPlaybackCallback`, `VOLUME_CHANGED_ACTION` receiver, accelerometer sensor) notify `SleepTimerStateMachine` immediately of events.
3. If enabled and active, a volume change or phone flip gesture resets the countdown to `configuredDurationMinutes`.
4. If enabled and waiting, active media playback (`isMusicActive()`) transitions the timer to `Active`.
5. If in `Fading` state, a volume change or phone flip gesture cancels fade, restores pre-fade volume, and resets the timer to `Active`.
6. Changes in volume, flip gesture detection, or media playback state are logged to `EventLogger`.

### Expiry & Smart Wake-Up Goal Alarm Creation

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

### Import & Export Settings

1. User taps **Export** in `MainActivity`.
2. `MainActivity` reads configuration from `SharedPreferences`, serializes values into JSON Schema Version 1 (`version`, `duration_minutes`, `active`, `wake_up_goal_enabled`, `wake_up_goal_hour`, `wake_up_goal_minute`, `min_sleep_duration_minutes`), and launches Android's native system share sheet via `Intent.ACTION_SEND` with MIME type `text/plain`.
3. Logs the action to `EventLogger`.
4. User taps **Import** in `MainActivity`.
5. An instructional `AlertDialog` input prompt appears with explanatory instructions (pre-filled with valid clipboard JSON if present).
6. User confirms import; `MainActivity` validates JSON syntax, schema version, and numeric boundaries.
7. Upon successful validation, preferences are written to `SharedPreferences`, an `ACTION_REDRAW_NOTIFICATION` intent is sent to `SleepTimerService` to reload state and redraw the notification shade, a success Toast is shown, and the action is logged to `EventLogger`.
8. Upon invalid JSON or out-of-range parameters, preferences are left untouched, an error Toast is displayed, and a warning is logged to `EventLogger`.

## Android manifest and permissions

Declared in `app/src/main/AndroidManifest.xml`:

- `FOREGROUND_SERVICE`: permits foreground-service operation.
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK`: required for the media playback foreground-service type on newer Android versions.
- `MODIFY_AUDIO_SETTINGS`: permits changing the music stream volume.
- `VIBRATE`: permits triggering haptic feedback vibration pulses.
- `SCHEDULE_EXACT_ALARM`: permits scheduling exact alarms with `AlarmManager`.
- `com.android.alarm.permission.SET_ALARM`: permits setting system clock alarms via `AlarmClock` intents.
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
