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
│       │   ├── BootReceiver.kt
│       │   ├── EventLogger.kt
│       │   ├── HealthConnectManager.kt
│       │   ├── MainActivity.kt
│       │   ├── MainService.kt
│       │   └── SettingRowView.kt
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml
│           │   └── view_setting_row.xml
│           ├── values/
│           │   ├── attrs.xml
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
│   ├── SLEEP_SESSION.md
│   ├── SPEC.md
│   └── UPDATE_NOTIFICATIONS.md
├── build.gradle
├── gradle.properties
├── gradlew
└── settings.gradle
```

## Main components

### `MainService` (renamed from `SleepTimerService`)

File: `app/src/main/java/com/bas080/autosleepdroid/MainService.kt`

This is the main application component. It is a foreground service with the `mediaPlayback` foreground-service type.

Responsibilities:

- Create the low-importance ongoing notification (`setOngoing(true)`) representing one of four system states: `Off`, `Waiting`, `Active`, or `Fading`.
- Display concise, directly visible text in the main notification body (`setContentText`). No content is hidden behind expanded shade views.
- Set content intent targeting `MainActivity` so tapping the notification opens `MainActivity`.
- Expose notification shade action buttons: toggle ("Disable" when enabled, or "Enable" when disabled) and "Nap" / "Cancel Nap" to quickly set or cancel nap timers.
- Respect `show_notification` preference (default `false`); when `show_notification` is `false`, remove the ongoing service notification via `stopForeground(STOP_FOREGROUND_REMOVE)` and `manager.cancel(NOTIFICATION_ID)` across all timer states (`Off`, `Waiting`, `Active`, `Fading`).
- Centralize state management and `SharedPreferences` observation via `PreferenceManager`, exposing `LocalBinder` to allow `MainActivity` to bind to `MainService` and register key-specific listeners.
- Store timer configuration (`duration_minutes`), enabled state (`active`), wall-clock target expiration (`timer_ends_at`), active timer start timestamp (`timer_start_time_ms`, updated when the timer starts or is reset via flip gesture, volume button, or duration update), show notification setting (`show_notification`), and wake-up goal settings in `SharedPreferences`.
- Schedule exact timer expiry using `AlarmManager.setExactAndAllowWhileIdle()` and handler callbacks on the main looper, falling back to `setAndAllowWhileIdle()` or foreground service callbacks if exact alarm permission is denied.
- Listen for media playback state changes using `AudioManager.AudioPlaybackCallback` (API 26+) dynamically only during `Waiting` state instead of periodic polling.
- Register accelerometer sensor listener on a dedicated background `HandlerThread` (with 300ms temporal throttling) and `VOLUME_CHANGED_ACTION` broadcast receiver dynamically during `Active` and `Fading` states, or while the wake-up alarm is ringing.
- Transition from `Waiting` to `Active` when playback callback detects active music playback while enabled, and reset an `Active` or `Fading` countdown when volume changes or a phone flip gesture occurs.
- Fade music volume from the captured current level to zero over 30 seconds upon expiry using an ease-out quadratic curve (starting fast and slowing down).
- Request transient audio focus (`AudioManager.requestAudioFocus`) to pause active media playback, restore pre-fade volume after media is paused (after a short 500ms delay), and revert to the `Waiting` state.
- Upon sleep timer start/reschedule or when the current alarm rings, schedule/update the daily recurring `"Auto Sleep"` wake-up alarm via `AlarmManager.setAlarmClock` if Smart Wake-Up Goal is enabled in the background. When triggered (`ACTION_WAKEUP_ALARM_EXPIRY`), `MainService` automatically schedules the next day's alarm for the same goal time, ensures `STREAM_ALARM` is set to an audible baseline level and explicitly unmuted on API 23+ if muted, plays the default system alarm tone using `RingtoneManager` with a 3-minute gentle volume crescendo, and updates the ongoing status notification to display the alarm status.
- Support nap timer alarm scheduling (`ACTION_START_NAP`, `ACTION_CANCEL_NAP`, `ACTION_NAP_EXPIRY`). When the sleep timer is reset or rescheduled, active nap alarms are pushed forward by the same reset increment. Dismissing a nap alarm does not adjust or affect the current scheduled wake-up time.
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

File: `app/src/main/java/com/bas080/autosleepdroid/NapDialogActivity.kt`

A translucent-themed activity (`@android:style/Theme.Translucent.NoTitleBar`) launched from `MainActivity` or the status notification's "Nap" action when no nap is active:

- Constructs an `AlertDialog` using `AlertDialog.Builder` wrapped with `ContextThemeWrapper(this, R.style.AppTheme)` containing `DurationInputView(dialogContext)` prefilled with previously used nap duration (`nap_duration_minutes`, default 20) and standard positive ("Nap") / negative ("Cancel") buttons, matching the exact dialog styling and theme of all duration configuration dialogs across `MainActivity`.
- Confirming "Nap" persists the nap duration in `SharedPreferences` and sends `ACTION_START_NAP` with `EXTRA_NAP_DURATION_MINUTES` to `MainService`.

### `AwakeDialogActivity`

File: `app/src/main/java/com/bas080/autosleepdroid/AwakeDialogActivity.kt`

A translucent-themed activity (`@android:style/Theme.Translucent.NoTitleBar`) launched automatically when opening `MainActivity` during an active sleep session (active timer or `sleep_start_time_ms` within 14 hours) or from the status notification's "I'm Awake" action (shown strictly during active sleep sessions when wake alarms are enabled):

- Constructs an `AlertDialog` using `AlertDialog.Builder` wrapped with `ContextThemeWrapper(this, R.style.AppTheme)` presenting a cancelable "Are you awake?" confirmation dialog.
- Confirming "I'm Awake" sends `ACTION_AWAKE` to `MainService` to log the sleep session (preserving `current_wake_hour` and `current_wake_minute` if triggered during a nap), then finishes.
- Canceling or dismissing the dialog finishes without modifying alarm schedules or logging sleep sessions.

### `PreferenceManager`

File: `app/src/main/java/com/bas080/autosleepdroid/PreferenceManager.kt`

Centralized preference key constants, key-specific `SharedPreferences` observation, and async execution:

- Centralizes public static final constants for all application preference keys (`KEY_ACTIVE`, `KEY_DURATION_MINUTES`, `KEY_AUTO_TIMER_ENABLED`, `KEY_WAKE_UP_GOAL_ENABLED`, `KEY_WAKE_UP_GOAL_HOUR`, `KEY_WAKE_UP_GOAL_MINUTE`, `KEY_CURRENT_WAKE_HOUR`, `KEY_CURRENT_WAKE_MINUTE`, `KEY_MIN_SLEEP_DURATION_MINUTES`, `KEY_NAP_DND_ENABLED`, `KEY_NAP_DURATION_MINUTES`, `KEY_NAP_ALARM_ENDS_AT`, `KEY_HEALTH_CONNECT_ENABLED`, `KEY_HC_MIN_DURATION_MINUTES`, `KEY_WAKEUP_LAST_SCHEDULED_MS`, etc.).
- Encapsulates `SharedPreferences.OnSharedPreferenceChangeListener` to map specific preference keys to custom callbacks (`OnPreferenceChangeListener`) using thread-safe data structures (`ConcurrentHashMap`, `CopyOnWriteArraySet`).
- Provides lazy evaluation and memoization of computed values via `getComputed(computeKey, computer)` using self-tracking `PreferenceGetter`. Computed values automatically record their preference key dependencies during execution and automatically invalidate cached results when any accessed preference key changes.
- Ensures callbacks fire strictly when their target key changes, providing reactive state synchronization between `MainService` and `MainActivity` without requiring manual `redrawNotification()` intent calls.
- Provides explicit registration/unregistration methods (`registerListener`, `unregisterListener`) and manual cache invalidation (`invalidateComputed`, `invalidateAllComputed`).
- Offloads background computations using a single-threaded `ExecutorService`.

### `SessionPhase`

File: `app/src/main/java/com/bas080/autosleepdroid/PreferenceComputations.kt`

Defines the `SessionPhase` enum (`IDLE`, `INITIATION_AND_ACTIVE_SLEEP`, `PRE_ALARM_WINDOW`, `ALARM`) and top-level `getSessionPhase(now, currentWakeTime, minSleepDuration, isSessionOngoing, isAlarmRingingOrSnoozed)` evaluation function that determines the lifecycle phase relative to scheduled wake alarm time. Detailed session phase workflow is documented in `docs/SLEEP_SESSION.md`.

### `SettingRowView`

File: `app/src/main/java/com/bas080/autosleepdroid/SettingRowView.kt`

Custom compound `ViewGroup` extending `LinearLayout` that encapsulates settings row layout, styling, and enablement logic:

- Uses `view_setting_row.xml` shared layout containing title `TextView`, description `TextView`, value `TextView`, and `Switch`.
- Supports custom XML attributes defined in `attrs.xml`: `rowTitle`, `rowDescription`, `rowValue`, `rowType` (`none`, `value`, `switch`), `valueId`, and `switchId`.
- Automatically assigns designated resource IDs to child `valueTextView` and `switchView` so standard `findViewById` calls on `Activity` find child views seamlessly.
- Encapsulates `setEnabled(boolean enabled)` logic, dynamically adjusting row container and child view enablement, alpha transparency (`1.0f` vs `0.38f`), and focusable/clickable states.

### `MainActivity`

File: `app/src/main/java/com/bas080/autosleepdroid/MainActivity.kt`

The launcher activity starts `MainService`, requests `POST_NOTIFICATIONS` on Android 13+, prompts for exact alarm permissions on Android 12+, and presents the main configuration UI (`activity_main.xml`).

Main Configuration Controls & Action Links:

- Service Binding & Lifecycle Safety: Binds to `MainService` (`BIND_AUTO_CREATE`) via `ServiceConnection` on `onStart()`, registers key-specific preference listeners via `PreferenceManager` on service connection or `onResume()`, uses `PreferenceManager.getComputed` for memoized UI string formatting and state evaluations, and explicitly unregisters all listeners and unbinds in `onPause()` / `onStop()` to prevent memory leaks.
- Single-screen configuration UI:
  - Section headings (`headerNap`, `headerTimer`, `headerAlarm`, `headerHealthConnect`, `headerDnd`, `headerAbout`) remain enabled (`true`) with full opacity (`1.0f`) at all times.
  - Nap alarm section at top (`btn_nap` button launching `NapDialogActivity` or displaying "I'm Awake" to cancel active nap).
  - Sleep timer enable/disable Switch (`active` preference).
  - Sleep timer duration input using custom `DurationInputView` (`input_duration`, incorporating `NumberPicker` hour and minute wheel pickers configured via `configure(minHours, maxHours, minuteStep)`, saving `duration_minutes` preference, displaying formatted duration value on `text_duration_value`). Timer duration controls remain enabled when the sleep timer switch is OFF.
  - Wake-up alarm enable Switch (`wake_up_goal_enabled` preference, labeled "Wake-up alarm").
  - Target wake-up goal time Button (`btn_target_time`, displaying formatted system time and opening `TimePickerDialog` on click).
  - Minimum sleep duration input using custom `DurationInputView` (`input_min_sleep`, saving `min_sleep_duration_minutes` preference).
  - Do Not Disturb section featuring Nap DND Switch (`row_nap_dnd`) and Auto sleep timer Switch (`row_auto_timer`).
  - About section featuring Version row (`btn_version`), Feedback row (`btn_feedback`, which presents a prompt dialog asking if the user wants to include event logs in their email), and Links row (`btn_links`).
- Links header & action list dialog: Manual, Logs, Donate, Export, and Import.
- Full-screen Manual & Event Logs Views: Overlay `RelativeLayout` views in `activity_main.xml` with a Back button pinned to the bottom-right corner (`alignParentBottom="true"`, `alignParentEnd="true"`), displaying formatted HTML manual text or real-time monospace event logs and closing upon Back button tap or hardware back button press.
- Crash Reporting: Prompts user on launch via `AlertDialog` if a pending uncaught exception was saved in `SharedPreferences` by `AutoSleepApplication`. Choosing to send report opens the email client prefilled with the crash stack trace, recent event logs (`EventLogger.getEvents`), and app/device version metadata.
- Export Settings Action: Serializes current preferences into a Schema Version 1 JSON string, launches system share action (`ACTION_SEND`), and logs to `EventLogger`.
- Import Settings Action: Prompts user with instructional `AlertDialog`, validates syntax and boundaries, applies valid values, sends `ACTION_REDRAW_NOTIFICATION` to `MainService`, refreshes UI controls, and logs to `EventLogger`.

## Error Handling Philosophy

Write code with the least amount of defensive null guards and `try-catch` blocks necessary. When the application reaches an invalid or unexpected state, code should fail fast and throw an exception rather than silently masking issues. Uncaught exceptions are intercepted by `AutoSleepApplication`'s global uncaught exception handler, which persists the crash report and stack trace to `SharedPreferences` so `MainActivity` can prompt the user to submit a detailed crash report upon next launch.

### `HealthConnectManager`

File: `app/src/main/java/com/bas080/autosleepdroid/HealthConnectManager.kt`

Utility object managing integration with Android Health Connect (`androidx.health.connect:connect-client`):

- Provides `isHealthConnectAvailable(context)` to check SDK status.
- Provides `hasSleepWritePermission(context, callback)` to check if `WRITE_SLEEP` permission is granted.
- Provides `writeSleepSession(context, startTimeMs, endTimeMs, callback)` to write `SleepSessionRecord` objects containing start/end timestamps and local system zone offsets (`ZoneOffset`) on a background I/O dispatcher.
- Handles sleep session recording on alarm dismissal or when triggered explicitly via **I'm Awake** (`ACTION_AWAKE`), falling back to minimum sleep duration estimates if `sleep_start_time_ms` was not previously persisted.
- Uses session-anchored minimum sleep calculations (`sleep_start_time_ms + min_sleep_duration_minutes`) when a sleep session started within the last 14 hours to prevent pushing alarms forward on brief night wake-ups or morning playback checks.

### `EventLogger`

File: `app/src/main/java/com/bas080/autosleepdroid/EventLogger.kt`

Centralized logging utility that formats event lines with timestamps (`yyyy-MM-dd HH:mm:ss - <message>`). Keeps logs bounded up to 500 lines in memory and `SharedPreferences`, notifying UI listeners of new events.

### `BootReceiver`

File: `app/src/main/java/com/bas080/autosleepdroid/BootReceiver.kt`

Receives `BOOT_COMPLETED`, logs the reboot event, and starts the foreground service. `MainService` then reads persisted state. If previously in an enabled/running state (`Waiting`, `Active`, `Fading`), it restores to the `Waiting` state using the configured duration; if explicitly in `Off` state, it remains `Off`.

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
| `sleep_start_time_ms` | long | Wall-clock timestamp (millis) recorded when sleep timer expired or when media was paused while active |
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
