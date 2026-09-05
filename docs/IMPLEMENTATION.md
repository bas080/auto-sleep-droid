# Auto Sleep Droid Implementation Guide

## Purpose

This document describes the implementation that currently exists in the repository. Use it as the code-oriented source of truth when modifying the app.

The app is an Android sleep timer app configured directly from a single main UI screen (`MainActivity`), with full-screen Manual and Event Logs views and simplified notification shade actions.

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
│   ├── EVENTS_AND_STATES.md
│   ├── HEALTH_CONNECT.md
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
- Display concise, directly visible text in the main notification body (`setContentText`). No content is hidden behind expanded shade views.
- Set content intent targeting `MainActivity` so tapping the notification opens `MainActivity`.
- Expose notification shade action buttons: toggle ("Disable" when enabled, or "Enable" when disabled) and "Nap" / "Cancel Nap" to quickly set or cancel nap timers.
- Respect `show_notification` preference (default `false`); when `show_notification` is `false`, remove the ongoing service notification via `stopForeground(STOP_FOREGROUND_REMOVE)` and `manager.cancel(NOTIFICATION_ID)` across all timer states (`Off`, `Waiting`, `Active`, `Fading`).
- Store timer configuration (`duration_minutes`), enabled state (`active`), wall-clock target expiration (`timer_ends_at`), show notification setting (`show_notification`), and wake-up goal settings in `SharedPreferences`.
- Schedule exact timer expiry using `AlarmManager.setExactAndAllowWhileIdle()` and handler callbacks on the main looper, falling back to `setAndAllowWhileIdle()` or foreground service callbacks if exact alarm permission is denied.
- Listen for media playback state changes using `AudioManager.AudioPlaybackCallback` (API 26+) dynamically only during `Waiting` state instead of periodic polling.
- Register accelerometer sensor listener on a dedicated background `HandlerThread` (with 300ms temporal throttling) and `VOLUME_CHANGED_ACTION` broadcast receiver dynamically during `Active` and `Fading` states, or while the wake-up alarm is ringing.
- Transition from `Waiting` to `Active` when playback callback detects active music playback while enabled, and reset an `Active` or `Fading` countdown when volume changes or a phone flip gesture occurs.
- Fade music volume from the captured current level to zero over 30 seconds upon expiry using an ease-out quadratic curve (starting fast and slowing down).
- Request transient audio focus (`AudioManager.requestAudioFocus`) to pause active media playback, restore pre-fade volume after media is paused (after a short 500ms delay), and revert to the `Waiting` state.
- Upon sleep timer start/reschedule or when the current alarm rings, schedule/update the daily recurring `"Auto Sleep"` wake-up alarm via `AlarmManager.setAlarmClock` if Smart Wake-Up Goal is enabled in the background. When triggered (`ACTION_WAKEUP_ALARM_EXPIRY`), `SleepTimerService` automatically schedules the next day's alarm for the same goal time, ensures `STREAM_ALARM` is set to an audible baseline level, plays the default system alarm tone using `RingtoneManager` with a 3-minute gentle volume crescendo, and updates the ongoing status notification to display the alarm status.
- Support nap timer alarm scheduling (`ACTION_START_NAP`, `ACTION_CANCEL_NAP`, `ACTION_NAP_EXPIRY`). When the sleep timer is reset or rescheduled, active nap alarms are pushed forward by the same reset increment.
- Cancel/dismiss the `"Auto Sleep"` wake-up alarm via `AlarmManager.cancel` on stop or smart alarm cancel in the background.
- Trigger a short, faint haptic feedback pulse (`Vibrator`) upon turning off/on, volume button resets, and flip gestures.
- Log lifecycle and state events to `EventLogger`.

Important constants:

- Notification channel: `sleep_timer`
- Notification ID: `1001`
- Minimum duration: `1` minute
- Maximum duration: `1440` minutes (24 hours)
- Fade duration: `30_000` milliseconds

### `NapDialogActivity`

File: `app/src/main/java/com/bas080/autosleepdroid/NapDialogActivity.java`

A translucent-themed activity (`@android:style/Theme.Translucent.NoTitleBar`) launched from `MainActivity` or the status notification's "Nap" action when no nap is active:

- Constructs an `AlertDialog` using `AlertDialog.Builder` wrapped with `ContextThemeWrapper(this, R.style.AppTheme)` containing `DurationInputView(dialogContext)` prefilled with previously used nap duration (`nap_duration_minutes`, default 20) and standard positive ("Nap") / negative ("Cancel") buttons, matching the exact dialog styling and theme of all duration configuration dialogs across `MainActivity`.
- Confirming "Nap" persists the nap duration in `SharedPreferences` and sends `ACTION_START_NAP` with `EXTRA_NAP_DURATION_MINUTES` to `SleepTimerService`.

### `MainActivity`

File: `app/src/main/java/com/bas080/autosleepdroid/MainActivity.java`

The launcher activity starts `SleepTimerService`, requests `POST_NOTIFICATIONS` on Android 13+, prompts for exact alarm permissions on Android 12+, and presents the main configuration UI (`activity_main.xml`).

Main Configuration Controls & Action Links:

- Single-screen configuration UI:
  - Section headings (`headerNap`, `headerTimer`, `headerAlarm`, `headerAbout`) remain enabled (`true`) with full opacity (`1.0f`) at all times.
  - Nap alarm section at top (`btn_nap` button launching `NapDialogActivity` or canceling active nap).
  - Sleep timer enable/disable Switch (`active` preference).
  - Sleep timer duration input using custom `DurationInputView` (`input_duration`, saving `duration_minutes` preference, displaying formatted duration value on `text_duration_value`). Timer duration controls remain enabled when the sleep timer switch is OFF.
  - Auto sleep timer (DND) toggle Switch (`auto_timer_enabled` preference).
  - Wake-up alarm enable Switch (`wake_up_goal_enabled` preference, labeled "Wake-up alarm").
  - Target wake-up goal time Button (`btn_target_time`, displaying formatted system time and opening `TimePickerDialog` on click).
  - Minimum sleep duration input using custom `DurationInputView` (`input_min_sleep`, saving `min_sleep_duration_minutes` preference).
- Links header & action link list at the bottom of the form: Manual, Logs, Feedback, Donate, Export, and Import rendered inside custom `FlowLayout` wrapping inline with light font weight (`sans-serif-light`) separated by middle dots (`·`).
- Full-screen Manual & Event Logs Views: Overlay `RelativeLayout` views in `activity_main.xml` with a Back button pinned to the bottom-right corner (`alignParentBottom="true"`, `alignParentEnd="true"`), displaying formatted HTML manual text or real-time monospace event logs and closing upon Back button tap or hardware back button press.
- Export Settings Action: Serializes current preferences into a Schema Version 1 JSON string, launches system share action (`ACTION_SEND`), and logs to `EventLogger`.
- Import Settings Action: Prompts user with instructional `AlertDialog`, validates syntax and boundaries, applies valid values, sends `ACTION_REDRAW_NOTIFICATION` to `SleepTimerService`, refreshes UI controls, and logs to `EventLogger`.

### `HealthConnectManager`

File: `app/src/main/java/com/bas080/autosleepdroid/HealthConnectManager.kt`

Utility object managing integration with Android Health Connect (`androidx.health.connect:connect-client`):

- Provides `isHealthConnectAvailable(context)` to check SDK status.
- Provides `hasSleepWritePermission(context, callback)` to check if `WRITE_SLEEP` permission is granted.
- Provides `writeSleepSession(context, startTimeMs, endTimeMs, callback)` to write `SleepSessionRecord` objects containing start/end timestamps and local system zone offsets (`ZoneOffset`) on a background I/O dispatcher.

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
| `show_notification` | boolean | Whether ongoing sleep timer notification is visible across timer states |
| `timer_ends_at` | long | Wall-clock timestamp (millis) when active timer expires |
| `auto_timer_enabled` | boolean | Whether DND-based Auto Sleep Timer is enabled |
| `wake_up_goal_enabled` | boolean | Whether Smart Wake-Up Goal is enabled |
| `wake_up_goal_hour` | integer | Target goal hour of day (0-23) |
| `wake_up_goal_minute` | integer | Target goal minute (0-59) |
| `min_sleep_duration_minutes` | integer | Safeguard minimum sleep duration in minutes (default 450 = 7.5h) |
| `nap_duration_minutes` | integer | Previously used nap duration in minutes (default 20) |
| `nap_alarm_ends_at` | long | Wall-clock timestamp (millis) when active nap alarm triggers |
| `health_connect_enabled` | boolean | Whether sleep sessions are synchronized with Android Health Connect |
| `sleep_start_time_ms` | long | Wall-clock timestamp (millis) recorded when sleep timer expired |
| `nap_start_time_ms` | long | Wall-clock timestamp (millis) recorded when a nap alarm started |

Event log history is stored in the `event_logger` `SharedPreferences` file:

| Key | Type | Meaning |
|---|---|---|
| `logs` | string | Newline-separated event log entries (capped at 500 lines) |

## Build and release

Run unit tests locally:

```sh
./gradlew test
```

Local debug build:

```sh
./gradlew assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.
