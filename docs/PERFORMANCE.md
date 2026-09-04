# Auto Sleep Droid Performance & Efficiency Guide

This document provides a technical evaluation of the performance characteristics of Auto Sleep Droid. It details the efficiency mechanisms currently implemented, critically analyzes existing performance bottlenecks and anti-patterns, and outlines actionable technical strategies to increase runtime efficiency, reduce memory allocations, and optimize battery drain.

---

## 1. Implemented Performance Optimizations

### 1.1 Event-Driven Playback Detection
- **Mechanism**: The app uses `AudioManager.AudioPlaybackCallback` (API 26+) to receive operating system callbacks when media playback starts or stops.
- **Performance Impact**: Completely eliminates background polling loops while in the `Waiting` state. The CPU remains untouched until an actual media playback state transition occurs.

### 1.2 State-Gated Sensor & Broadcast Receiver Lifecycle
- **Mechanism**: Hardware accelerometer listener (`SensorManager`) and system volume broadcast receiver (`VOLUME_CHANGED_ACTION`) are dynamically registered **only** when the timer enters `Active` or `Fading` states. They are immediately unregistered in `Off` and `Waiting` states.
- **Performance Impact**: Prevents continuous background accelerometer interrupts and system-wide volume broadcast handling when the timer is idle, avoiding unnecessary battery and CPU drain.

### 1.3 Offloaded Sensor Thread & Temporal Throttling
- **Mechanism**: Accelerometer callbacks are registered to a dedicated background `HandlerThread` (`SensorThread`) rather than the main looper, with a 300ms temporal throttle (`SENSOR_THROTTLE_MS`) filtering out rapid accelerometer noise.
- **Performance Impact**: Dispatches raw sensor interrupts away from the UI/main thread and minimizes main-thread looper competition during movement.

### 1.4 Wall-Clock Timestamp Persistence & Alarm Offloading
- **Mechanism**: Target expiration is stored as a wall-clock Unix timestamp (`timer_ends_at`). Expiry is backed by `AlarmManager.setExactAndAllowWhileIdle()`.
- **Performance Impact**: The system kernel can put the application and CPU into low-power deep sleep (Doze mode) during active timer countdowns. The app does not maintain an active sub-second countdown thread or CPU wake lock.

### 1.5 Decoupled State Machine Architecture
- **Mechanism**: Core state transitions, duration validation, and fade-out calculations are isolated in `SleepTimerStateMachine.java` using pure Java primitives.
- **Performance Impact**: State evaluations run synchronously in microseconds with zero Android framework IPC overhead and minimal garbage collection (GC) allocation.

### 1.6 Notification Channel Importance Optimization
- **Mechanism**: Ongoing service notifications use `NotificationManager.IMPORTANCE_LOW` with `setOnlyAlertOnce(true)`.
- **Performance Impact**: Prevents SystemUI from triggering sound, haptic feedback, or intrusive visual pop-ups during notification updates, minimizing layout redraw overhead in the status bar.

### 1.7 Build System & CI Gradle Caching
- **Mechanism**: Enabled Gradle build caching (`org.gradle.caching=true`) and parallel execution (`org.gradle.parallel=true`) in `gradle.properties`, along with `cache: 'gradle'` in GitHub Actions workflows (`.github/workflows/android-release.yml`).
- **Performance Impact**: Significantly reduces clean and incremental build execution times and speeds up CI unit test runs across workflow executions.

### 1.8 Shared Themed Dialog Context & Direct SharedPreferences Synchronization
- **Mechanism**: Dialog views (such as `NapDialogActivity`) construct `AlertDialog.Builder` and `DurationInputView` with a shared `ContextThemeWrapper(this, R.style.AppTheme)`, eliminating XML layout reinflations and custom dialog layouts. Additionally, wake-up alarm calculations directly persist pushed wake times to `SharedPreferences` in `SleepTimerService` and filter alarm string formatting behind `isWakeAlarmEnabled()`.
- **Performance Impact**: Reduces view hierarchy complexity and layout passes during dialog display, avoids unnecessary SystemUI notification string formatting and redraws when alarms are disabled, and eliminates multi-process IPC roundtrips between activity and service for scheduled alarm time synchronization.

---

## 2. Critical Performance Analysis & Current Bottlenecks

A thorough audit of the runtime codebase reveals several critical performance bottlenecks and architectural anti-patterns that impact main-thread latency, memory allocations, and disk I/O.

### 2.1 Disk I/O & Main-Thread Overhead in `EventLogger`
- **Issue**: `EventLogger.log(context, message)` serializes up to 500 string entries into a single newline-separated string and executes `SharedPreferences.edit().putString(...).apply()` on **every single log call**.
- **Impact**:
  - Calling `log()` during 30 continuous volume fade steps (1-second intervals) enqueue 30 disk-write operations to the background `SharedPreferences` disk thread in rapid succession.
  - String concatenation (`sb.append(events.get(i))`) inside `persistLogs()` creates $O(N)$ string allocations on every log invocation, causing garbage collector pressure.
  - Date formatting via `SimpleDateFormat` instantiates a new formatter object or parses dates repeatedly on the calling thread.

### 2.2 Object Allocations in `buildNotification()`
- **Issue**: `buildNotification()` instantiates new `Notification.Builder`, `RemoteInput`, `Notification.Action`, and `Intent` instances, and queries system string resources every time the notification is updated.
- **Impact**: While acceptable for occasional updates, frequent redrawing (e.g., during inline reply setup or state changes) causes transient memory allocations on the main thread.

---

## 3. Strategies for Further Performance Increases

To achieve maximum efficiency and responsiveness, future development should prioritize the following optimizations:

### 3.1 Asynchronous & Bounded Logging Subsystem
- **Action**: Refactor `EventLogger` to buffer log entries in memory and flush to `SharedPreferences` asynchronously on a background thread executor or during app shutdown.
- **Action**: Replace full-array string serialization with ring-buffer storage or SQLite/Room to eliminate $O(N)$ string concatenation on log writes.
- **Action**: Use an immutable thread-safe `ConcurrentLinkedQueue` or array ring buffer for in-memory event access to avoid allocating defensive copies on `getEvents()`.

### 3.2 Notification Builder Caching & Re-use
- **Action**: Cache static `Notification.Action` instances and `RemoteInput` builders in memory rather than reconstructing them on every state change.
- **Action**: Only update notifications when user-visible state or time display values actually change.

### 3.3 R8 / ProGuard Code & Resource Shrinking
- **Action**: Enable R8 / ProGuard obfuscation, code shrinking (`minifyEnabled true`), and resource shrinking (`shrinkResources true`) in `app/build.gradle` for production release builds to reduce final APK size, shrink runtime method tables, and improve class loading performance on device.
