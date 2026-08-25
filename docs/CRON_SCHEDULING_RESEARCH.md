# Android Background Scheduling & Robustness Research

This document evaluates Android cron-like scheduling mechanisms for background task execution, process recoverability, and robustness under battery saver, Doze mode, and aggressive OEM process termination.

---

## Overview & The Problem of Battery Saver / Doze Mode

Android enforces strict power management restrictions to preserve battery life:
1. **Doze Mode**: When a device is unplugged, stationary, and screen-off for a period, Android restricts network access and defers CPU-intensive background tasks.
2. **Battery Saver Mode**: When battery saver is active, background execution is heavily throttled, alarms may be deferred, location/sensors disabled, and background apps/services may be killed.
3. **App Standby Buckets**: Apps are assigned standby buckets (Active, Working Set, Frequent, Rare, Restricted) based on user interaction frequency. Restricted/Rare buckets have strict limits on background job frequency and execution windows.
4. **OEM Process Killers**: Custom Android ROMs (Samsung OneUI, Xiaomi MIUI/HyperOS, Huawei EMUI, OnePlus OxygenOS) aggressively terminate background services and process trees despite standard Android specifications.

---

## Scheduling Options Comparison

### 1. Jetpack WorkManager (`androidx.work.WorkManager`)

`WorkManager` is Google's recommended library for deferrable, guaranteed background work. It automatically chooses the best underlying API (`JobScheduler` on API 23+, `AlarmManager` + `BroadcastReceiver` on older devices).

#### Features & Mechanism
- **Periodic Work (`PeriodicWorkRequest`)**: Schedules recurring tasks with a minimum interval of 15 minutes.
- **One-Time Work (`OneTimeWorkRequest`)**: Schedules a single execution after an initial delay or when constraints are met.
- **Execution Constraints**: Can require constraints such as `setRequiresBatteryNotLow`, `setRequiresCharging`, or `setRequiredNetworkType`.
- **Expedited Jobs (`setExpedited()`)**: On Android 12+ (API 31+), expedited jobs run immediately (subject to quota) and can run even when battery saver is enabled. On older APIs, they are backed by Foreground Services.

#### Robustness & Recoverability
- **Persistence**: Work requests are persisted in an internal SQLite database (`workdb`). If the app or device reboots, WorkManager automatically reschedules queued work.
- **Battery Saver Handling**: Standard periodic work is deferred when battery saver or Doze mode is active. However, `setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)` will attempt immediate execution or fall back safely.
- **Execution Window**: Periodic work is inherently inexact. The minimum repeat interval is **15 minutes** (with a minimum flex interval of 5 minutes).

#### Sources & Official Documentation
- [WorkManager Guide](https://developer.android.com/topic/libraries/architecture/workmanager)
- [PeriodicWorkRequest API Reference](https://developer.android.com/reference/androidx/work/PeriodicWorkRequest)
- [Support for Expedited Work](https://developer.android.com/topic/libraries/architecture/workmanager/how-to/expedited-work)

---

### 2. `AlarmManager` (`setExactAndAllowWhileIdle` / `setAlarmClock`)

`AlarmManager` provides access to the system alarm services. It allows scheduling tasks to run at a specific time in the future.

#### Features & Mechanism
- **`setExactAndAllowWhileIdle()`**: Triggers an alarm at an exact time even while the device is in Doze mode. Android limits how frequently these alarms fire (at most once every 5–15 minutes per app under Doze).
- **`setAlarmClock()`**: Intended for user-facing alarm clocks. Shows an alarm icon in the status bar. It has the **highest priority** in Android: it will wake the device out of Doze mode and bypass battery saver throttling.
- **`setInexactRepeating()`**: Schedules periodic alarms, but Android batches them to minimize battery drain.

#### Robustness & Recoverability
- **Process Termination**: When the alarm fires, Android sends a `PendingIntent` (usually to a `BroadcastReceiver`). Even if the app process was terminated by battery saver, Android resurrects the process to deliver the broadcast.
- **Reboot Behavior**: Alarms are cleared when the device reboots. The app must listen for `ACTION_BOOT_COMPLETED` via a `BroadcastReceiver` to reschedule alarms after a reboot.
- **Battery Saver Handling**: `setAlarmClock()` is virtually immune to battery saver and Doze mode. `setExactAndAllowWhileIdle()` runs during Doze maintenance windows but can still be delayed under extreme battery saver restrictions.

#### Sources & Official Documentation
- [Schedule alarms guide](https://developer.android.com/training/scheduling/alarms)
- [AlarmManager API Reference](https://developer.android.com/reference/android/app/AlarmManager)
- [AlarmManager.setExactAndAllowWhileIdle() Documentation](https://developer.android.com/reference/android/app/AlarmManager#setExactAndAllowWhileIdle(int,%20long,%20android.app.PendingIntent))
- [AlarmManager.setAlarmClock() Documentation](https://developer.android.com/reference/android/app/AlarmManager#setAlarmClock(android.app.AlarmManager.AlarmClockInfo,%20android.app.PendingIntent))

---

### 3. `JobScheduler` (`android.app.job.JobScheduler`)

`JobScheduler` is the platform API for scheduling jobs that execute in the application's process.

#### Features & Mechanism
- Allows scheduling jobs based on conditions (charging, idle, network connected).
- Supports periodic execution (`setPeriodic(long millis)`).
- Supports `setPersisted(true)` to survive device reboots (requires `RECEIVE_BOOT_COMPLETED` permission).

#### Robustness & Recoverability
- **Doze / Battery Saver**: The OS defers `JobScheduler` jobs during Doze mode and battery saver until maintenance windows or until battery saver is turned off.
- **Minimum Periodic Interval**: Minimum period is **15 minutes**.
- **Limitations**: Higher overhead compared to `AlarmManager` for precise time-based callbacks.

#### Sources & Official Documentation
- [JobScheduler API Reference](https://developer.android.com/reference/android/app/job/JobScheduler)
- [Optimize for Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby)

---

## Summary Matrix

| Feature / Criteria | WorkManager | AlarmManager (`setExactAndAllowWhileIdle`) | AlarmManager (`setAlarmClock`) | Foreground Service |
| :--- | :--- | :--- | :--- | :--- |
| **Minimum Repeat / Execution Frequency** | 15 minutes | Exact / Any interval | Exact / Any interval | Continuous / Polling |
| **Exact Timing Guarantee** | No (Inexact) | Yes (subject to Doze throttling) | Yes (Highest priority) | Real-time |
| **Doze Mode Bypass** | Only Expedited Work | Yes (`AllowWhileIdle`) | Yes (`setAlarmClock`) | Exempt while running |
| **Battery Saver Bypass** | Partial (`setExpedited`) | Partial | Yes | High priority (visible notification) |
| **Survives Process Termination** | Yes (Internal SQLite DB) | Yes (OS PendingIntent) | Yes (OS PendingIntent) | If restarted (`START_STICKY`) |
| **Survives Device Reboot** | Yes (Automated) | Requires `BOOT_COMPLETED` receiver | Requires `BOOT_COMPLETED` receiver | Requires `BOOT_COMPLETED` receiver |

---

## Recommendations for Auto Sleep Droid

1. **Current Foreground Service Strategy**:
   - `SleepTimerService` running as an ongoing Foreground Service (`mediaPlayback`) is already the standard Android mechanism for active, visible timers. Android prioritizes foreground services, making them resistant to general battery saver killing.

2. **Enhancing Recoverability with `AlarmManager`**:
   - If OEM battery savers kill the foreground service process during long sleep countdowns, scheduling an exact `AlarmManager.setExactAndAllowWhileIdle()` or `setAlarmClock()` alarm as a **fail-safe heartbeat / backup trigger** when starting the timer guarantees that the OS will resurrect the app process at timer expiration even if the service was killed.

3. **When to use `WorkManager`**:
   - WorkManager is ideal for periodic background cleanup or sync tasks (e.g. daily log truncation or analytics), but is **not** suitable for sub-15 minute exact countdown timers due to its 15-minute minimum execution window restriction.
