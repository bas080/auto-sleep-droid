# Auto Sleep Droid Performance Guide

This document outlines the performance optimizations implemented in Auto Sleep Droid to minimize CPU usage, battery drain, memory footprint, and latency, as well as strategies for further performance enhancements.

---

## 1. How Performance Has Been Increased

### Event-Driven Playback Listening
- **Mechanism**: Replaced periodic background polling loops with `AudioManager.AudioPlaybackCallback` (available on Android 8.0+ / API 26+).
- **Benefit**: The app remains dormant in the `Waiting` state without consuming CPU cycles or waking the processor. State changes (media starting or stopping) trigger immediate event callbacks.

### Dynamic Sensor & Receiver Registration
- **Mechanism**: The accelerometer sensor listener (`SensorManager`) and volume change receiver (`VOLUME_CHANGED_ACTION`) are registered **only** during `Active` (timer running) and `Fading` states. They are immediately unregistered when the timer enters `Off` or `Waiting` states.
- **Benefit**: Prevents continuous background accelerometer polling and system volume broadcast processing when the sleep timer is idle or disabled, dramatically saving battery.

### Wall-Clock Expiry & AlarmManager Offloading
- **Mechanism**: Expiration target times are calculated and persisted as wall-clock millisecond timestamps (`timer_ends_at`). Exact system alarms (`AlarmManager.setExactAndAllowWhileIdle()`) schedule the backup wakeup.
- **Benefit**: Eliminates the need for continuous sub-second timer threads or active handler tick loops while counting down. The CPU can enter low-power deep sleep (Doze mode) during active timer operation.

### Pure State Machine Architecture
- **Mechanism**: Core state transitions, duration validations, volume calculation curves, and reset logic are isolated within `SleepTimerStateMachine.java`, decoupled from Android framework contexts.
- **Benefit**: State calculations execute in microseconds with minimal object allocation and zero IPC overhead. Unit tests execute in seconds without inflating heavy Android runtime environments.

### Low-Importance Foreground Service Notification
- **Mechanism**: Notification channel importance is set to `IMPORTANCE_LOW` (`setOnlyAlertOnce(true)`).
- **Benefit**: UI updates to notification text and remaining duration do not trigger intrusive system alerts, sounds, or repetitive vibration effects, reducing SystemUI layout re-draw overhead.

---

## 2. How Performance Can Be Increased Further

### Build System & Developer Tooling Optimizations
- **Gradle Configuration Cache**: Enable `org.gradle.configuration-cache=true` in `gradle.properties` to cache build configuration phases and reduce incremental build/test execution times.
- **R8 / ProGuard Optimization**: Enable code shrinking and resource optimization in `app/build.gradle` for release builds to reduce APK size and runtime method tables.

### Logging Threading & I/O Optimization
- **Background I/O for `EventLogger`**: Currently, `EventLogger` formats timestamps and persists log histories to `SharedPreferences` on the calling thread. Offloading string formatting and `SharedPreferences` commits (`apply()` vs background thread executor) will prevent potential main-thread jank during frequent event logging.

### Sensor Sampling & Gesture Detection Tuning
- **Hysteresis & Downsampling**: The accelerometer listener evaluates raw $Z$-axis force on every sensor event. Implementing a simple time-threshold or hysteresis filter (e.g., evaluating orientation change at most once every 200ms) will reduce CPU interrupt handling during physical movement.
- **Sensor Batching**: When active, sensor events can be registered with `SENSOR_DELAY_UI` or `SENSOR_DELAY_NORMAL` with max report latency parameters to allow the hardware sensor hub to batch events when supported.

### Deep Sleep Idle Optimizations for Extended Timers
- **Inactive Listener Suspension**: For long sleep timers (e.g., 2+ hours), dynamic listeners could be suspended after a period of user inactivity and woken up via `AlarmManager` near expiration, further reducing battery drain during overnight use.
