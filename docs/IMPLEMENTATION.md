# Auto Sleep Droid Implementation Details

This document details internal architecture and implementation behaviors for AI agents and developers.

## Architecture & Component Breakdown

Auto Sleep Droid is written 100% in Kotlin for Android 8.0+ (minSdk 26, targetSdk 34) and follows a clean unidirectional data flow architecture.

### Key Classes

```
com.bas080.autosleepdroid
├── AutoSleepApplication    (Application subclass, global crash handler setup)
├── MainActivity           (Single-screen UI, settings controls, overlay views, dialogs)
├── MainService            (Foreground service, state machine, playback listener, alarms)
├── PreferenceManager      (Centralized SharedPreferences manager with memoized computed values)
├── PreferenceComputations (Computations for session phases, awake window, and settings)
├── PreferenceKeys          (Constant key names for preferences)
├── AppDefaults             (Centralized app default constants and bounds)
├── DurationInputView       (Custom compound View for hour and minute wheel pickers)
├── SettingRowView          (Custom compound View for setting rows with title/desc/switches)
├── HealthConnectManager    (Health Connect API 34+ integration client)
├── BootReceiver            (BroadcastReceiver restoring service state upon reboot)
└── EventLogger             (Timestamped event log buffer with theme-aware formatting)
```

## Service and State Machine (`MainService`)

File: `app/src/main/java/com/bas080/autosleepdroid/MainService.kt`

`MainService` is a foreground service (`foregroundServiceType="mediaPlayback"`) managing sleep timer state transitions, audio focus, playback detection, and wake alarms.

### System States

- `OFF`: Sleep timer manually disabled.
- `WAITING`: Timer enabled and sitting passively waiting for media playback to begin via `AudioPlaybackCallback`.
- `ACTIVE`: Media playback detected; active countdown running towards expiration timestamp (`timer_ends_at`). Rescheduled by volume key clicks or timer duration adjustments.
- `FADING`: Countdown reached zero; 30-second volume fade-out running along an ease-out quadratic curve (`gain = (1 - progress)^2`) down to zero before requesting transient audio focus loss to pause media playback.

Responsibilities:

- Create the low-importance ongoing notification (`setOngoing(true)`) representing system state without showing sleep timer duration, using concise monochrome text-style symbols (`⏸︎` `\u23F8\uFE0E` for fadeout time, `⏰︎` `\u23F0\uFE0E` for scheduled wake alarm) rendered in default notification text color.
- Display "Click notification when awake" as the notification title (`setContentTitle`) during the sleep/awake window (`shouldShowAwakeAction()`), falling back to "Auto Sleep Droid" (`app_name`) outside the window.
- Set content intent (`ACTION_NOTIFICATION_CLICK`) targeting `MainService`: if within awake window or alarm phase (`shouldShowAwakeAction()`), clicking the notification triggers "I'm Awake" (showing a toast, stopping alarms, logging sleep session, updating wake schedule, without opening `MainActivity`). Otherwise, launches `MainActivity`.
- Expose a single notification shade action button: the sleep timer toggle ("Disable" when enabled, or "Enable" when disabled).
- Respect `show_notification` preference (default `false`); when `show_notification` is `false`, remove the ongoing service notification via `stopForeground(STOP_FOREGROUND_REMOVE)` and `manager.cancel(NOTIFICATION_ID)` across all timer states (`Off`, `Waiting`, `Active`, `Fading`).
- Centralize state management and `SharedPreferences` observation via `PreferenceManager`.
- Store timer configuration (`duration_minutes`), enabled state (`active`), wall-clock target expiration (`timer_ends_at`), active timer start timestamp (`timer_start_time_ms`), show notification setting (`show_notification`), and wake-up goal settings in `SharedPreferences`.
- Schedule exact timer expiry using `AlarmManager.setExactAndAllowWhileIdle()` and handler callbacks on the main looper.
- Listen for media playback state changes using `AudioManager.AudioPlaybackCallback` (API 26+) dynamically only during `Waiting` state instead of periodic polling.
- Register `VOLUME_CHANGED_ACTION` broadcast receiver dynamically during `Active` and `Fading` states, or while the wake-up alarm is ringing/snoozed.
- Transition from `Waiting` to `Active` when playback callback detects active music playback while enabled, and reset an `Active` or `Fading` countdown when volume changes occur.
- Fade music volume from the captured current level to zero over 30 seconds upon expiry using an ease-out quadratic curve.
- Request transient audio focus (`AudioManager.requestAudioFocus`) to pause active media playback, restore pre-fade volume after media is paused (after a short 500ms delay), and revert to the `Waiting` state.
- Upon sleep timer start/reschedule or when the current alarm rings, schedule/update the daily recurring `"Auto Sleep"` wake-up alarm via `AlarmManager.setAlarmClock` if Smart Wake-Up Goal is enabled in the background. When triggered (`ACTION_WAKEUP_ALARM_EXPIRY`), `MainService` automatically schedules the next day's alarm for the same goal time, ensures `STREAM_ALARM` is set to an audible baseline level and explicitly unmuted on API 23+ if muted, plays the default system alarm tone using `RingtoneManager` with a 3-minute gentle volume crescendo, and updates the ongoing status notification to display the alarm status.
- Cancel/dismiss the `"Auto Sleep"` wake-up alarm via `AlarmManager.cancel` on stop or smart alarm cancel in the background.
- Trigger a short, faint haptic feedback pulse (`Vibrator`) upon turning off/on and volume button resets.
- Log lifecycle and state events to `EventLogger`.

Important constants:

- Notification channel: `sleep_timer`
- Notification ID: `1001`
- Minimum duration: `1` minute
- Maximum duration: `1440` minutes (24 hours)
- Fade duration: `30_000` milliseconds

### `AwakeDialogActivity`

File: `app/src/main/java/com/bas080/autosleepdroid/AwakeDialogActivity.kt`

A translucent-themed activity (`@android:style/Theme.Translucent.NoTitleBar`) launched from the status notification's "I'm Awake" action:

- Constructs an `AlertDialog` using `AlertDialog.Builder` wrapped with `ContextThemeWrapper(this, R.style.AppTheme)` presenting a cancelable "Are you awake?" confirmation dialog.
- Confirming "I'm Awake" sends `ACTION_AWAKE` to `MainService` to log the sleep session, update current wake-up time to `max(targetGoalTime, T - 15m)`, and reschedule tomorrow's alarm.
- Canceling or dismissing the dialog finishes without modifying alarm schedules or logging sleep sessions.

### `PreferenceManager`

File: `app/src/main/java/com/bas080/autosleepdroid/PreferenceManager.kt`

Centralized reactive preferences manager:

- Wraps `SharedPreferences` for type-safe reads and writes of boolean, integer, long, and string preferences.
- Centralizes preference keys via `PreferenceKeys` object (`KEY_ACTIVE`, `KEY_DURATION_MINUTES`, `KEY_AUTO_TIMER_ENABLED`, `KEY_WAKE_UP_GOAL_ENABLED`, `KEY_WAKE_UP_GOAL_HOUR`, `KEY_WAKE_UP_GOAL_MINUTE`, `KEY_CURRENT_WAKE_HOUR`, `KEY_CURRENT_WAKE_MINUTE`, `KEY_MIN_SLEEP_DURATION_MINUTES`, `KEY_HEALTH_CONNECT_ENABLED`, `KEY_HC_MIN_DURATION_MINUTES`, `KEY_WAKEUP_LAST_SCHEDULED_MS`, etc.).
- Encapsulates `SharedPreferences.OnSharedPreferenceChangeListener` to map specific preference keys to custom callbacks (`OnPreferenceChangeListener`) using thread-safe data structures.
- Provides lazy evaluation and memoization of computed values via `getComputed(computeKey, computer)` using self-tracking `PreferenceGetter`.
- Ensures callbacks fire strictly when their target key changes, providing reactive state synchronization between `MainService` and `MainActivity`.

### `SessionPhase`

File: `app/src/main/java/com/bas080/autosleepdroid/PreferenceComputations.kt`

Defines the `SessionPhase` enum (`IDLE`, `INITIATION_AND_ACTIVE_SLEEP`, `PRE_ALARM_WINDOW`, `ALARM`) and top-level `getSessionPhase(now, currentWakeTime, minSleepDuration, isSessionOngoing, isAlarmRingingOrSnoozed)` evaluation function that determines the lifecycle phase relative to scheduled wake alarm time.

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
  - Section headings (`headerTimer`, `headerAlarm`, `headerHealthConnect`, `headerDnd`, `headerBackup`, `headerAbout`) remain enabled (`true`) with full opacity (`1.0f`) at all times.
  - Sleep timer enable/disable Switch (`active` preference).
  - Sleep timer duration input using custom `DurationInputView` (`input_duration`, incorporating `NumberPicker` hour and minute wheel pickers configured via `configure(minHours, maxHours, minuteStep)`, saving `duration_minutes` preference, displaying formatted duration value on `text_duration_value`). Timer duration controls remain enabled when the sleep timer switch is OFF.
  - Wake-up alarm enable Switch (`wake_up_goal_enabled` preference, labeled "Wake-up alarm").
  - Target wake-up goal time Button (`btn_target_time`, displaying formatted system time and opening `TimePickerDialog` on click).
  - Minimum sleep duration input using custom `DurationInputView` (`input_min_sleep`, saving `min_sleep_duration_minutes` preference).
  - Do Not Disturb section featuring Auto sleep timer Switch (`row_auto_timer`, enabling automated DND tracking without forcing navigation away to system settings).
  - Backup section featuring Export settings row (`btn_export`) and Import settings row (`btn_import`).
  - About section featuring Version row (`btn_version`), Feedback row (`btn_feedback`, which presents a prompt dialog asking if the user wants to include event logs in their email), and Links row (`btn_links`).
- Links header & action list dialog: Manual, Logs, and Donate.
- Full-screen Manual & Event Logs Views: Overlay `RelativeLayout` views in `activity_main.xml` with a Back button pinned to the bottom-right corner, displaying formatted HTML manual text or real-time monospace event logs.
- Crash Reporting: Prompts user on launch via `AlertDialog` if a pending uncaught exception was saved in `SharedPreferences`.
- Export Settings Action: Serializes current preferences into a Schema Version 1 JSON string, launches system share action (`ACTION_SEND`).
- Import Settings Action: Prompts user with instructional `AlertDialog`, validates syntax and boundaries, applies valid values, updating preferences reactively via `PreferenceManager`.

## Error Handling Philosophy

Write code with the least amount of defensive null guards and `try-catch` blocks necessary. Code fails fast on unexpected state, caught by global exception handler in `AutoSleepApplication`.

### `HealthConnectManager`

File: `app/src/main/java/com/bas080/autosleepdroid/HealthConnectManager.kt`

Utility object managing integration with Android Health Connect (`androidx.health.connect:connect-client`):

- Provides `isHealthConnectAvailable(context)` to check SDK status.
- Provides `hasSleepWritePermission(context, callback)` to check if `WRITE_SLEEP` permission is granted.
- Provides `writeSleepSession(context, startTimeMs, endTimeMs, callback)` to write `SleepSessionRecord` objects containing start/end timestamps and local system zone offsets (`ZoneOffset`) on a background I/O dispatcher.
- Handles sleep session recording on wake alarm dismissal or when triggered explicitly via **I'm Awake** (`ACTION_AWAKE`).

### `EventLogger`

File: `app/src/main/java/com/bas080/autosleepdroid/EventLogger.kt`

Centralized logging utility that formats event lines with timestamps (`yyyy-MM-dd HH:mm:ss - <message>`). Keeps logs bounded up to 500 lines in memory and `SharedPreferences`.

### `BootReceiver`

File: `app/src/main/java/com/bas080/autosleepdroid/BootReceiver.kt`

Receives `BOOT_COMPLETED`, logs the reboot event, and starts the foreground service. `MainService` then reads persisted state.

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
| `health_connect_enabled` | boolean | Whether sleep sessions are synchronized with Android Health Connect |
| `sleep_start_time_ms` | long | Wall-clock timestamp (millis) recorded when sleep timer expired or when media was paused while active |

Event log history is stored in the `event_logger` `SharedPreferences` file:

| Key | Type | Meaning |
|---|---|---|
| `logs` | string | Newline-separated event log entries (capped at 500 lines) |
