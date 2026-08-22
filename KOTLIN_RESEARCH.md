# Kotlin Adoption Research: auto-sleep-droid

## Executive Summary

This document presents a comprehensive research analysis on the pros and cons of migrating **auto-sleep-droid** from Java 17 to Kotlin.

Auto-sleep-droid is a specialized, notification-only Android sleep timer application for media playback. The application operates without a traditional Activity UI screen and relies on a foreground service (`SleepTimerService`), broadcast receivers (`BootReceiver`), and system integration (`MediaSessionAccessService`, `AudioManager`, `NotificationListenerService`).

While Kotlin is Google's recommended language for Modern Android Development (MAD), adopting it for this specific project involves trade-offs regarding binary size, F-Droid build reproducibility, compiler overhead, and architectural simplification.

---

## Project Context & Technical Baseline

To evaluate language suitability objectively, we must analyze the specific characteristics of auto-sleep-droid:

| Parameter | Current Status in `auto-sleep-droid` |
|---|---|
| **Codebase Size** | ~4 Java source files (~450 lines of code total) |
| **Language & Target** | Java 17 (`sourceCompatibility` & `targetCompatibility` = 17), Android SDK 35 |
| **Dependencies** | Zero external runtime dependencies (pure Android framework APIs) |
| **User Interface** | 100% notification-driven; `MainActivity` only handles initial setup permission redirects |
| **Build System** | Gradle 8.7.3 / 9.4.1 with Android Gradle Plugin (AGP) 8.7.3 |
| **Distribution Target** | F-Droid reproducible builds (`dependenciesInfo` metadata disabled) |
| **State Complexity** | 5 discrete system states (`Permissions Pending`, `Off`, `Waiting`, `Active`, `Fading`) |

---

## Pros of Adopting Kotlin for auto-sleep-droid

### 1. Superior State Machine Modeling with Sealed Classes
Currently, `SleepTimerService.java` manages state using a collection of synchronized mutable boolean flags:
```java
private boolean enabled;
private boolean active;
private boolean fading;
private boolean wasPermissionsPending;
private boolean suppressVolumeReset;
```
Maintaining these flags across async callbacks (`Handler.postDelayed`), user actions (`TURN_OFF`, `SET_DURATION`), and polling loops introduces potential state synchronization bugs (e.g., `active` and `fading` both being true simultaneously).

In Kotlin, the 5 system states specified in `SPEC.md` can be represented as an immutable sealed hierarchy:

```kotlin
sealed interface TimerState {
    object PermissionsPending : TimerState
    object Off : TimerState
    data class Waiting(val durationMinutes: Int) : TimerState
    data class Active(val durationMinutes: Int, val endsAtMillis: Long) : TimerState
    data class Fading(val durationMinutes: Int, val step: Int, val initialVolume: Int) : TimerState
}
```

Benefits:
- Eliminates illegal state combinations by construction.
- Exhaustive `when` expressions ensure that all notification updates, volume handler logic, and user actions handle every state explicitly without wildcard fallbacks.

### 2. Compile-Time Null Safety for Android System Services
In `SleepTimerService.java` and `MediaSessionAccessService.java`, interactions with system services like `AudioManager`, `NotificationManager`, `MediaSessionManager`, and `RemoteInput` intent extras require defensive null checking or risk `NullPointerException` (NPE):

```java
// Java: Defensive null check required
CharSequence reply = RemoteInput.getResultsFromIntent(intent) == null
        ? null
        : RemoteInput.getResultsFromIntent(intent).getCharSequence(REMOTE_INPUT_KEY);
```

Kotlin enforces nullability directly in the type system:
```kotlin
// Kotlin: Safe calls and idiomatic null handling
val reply = RemoteInput.getResultsFromIntent(intent)
    ?.getCharSequence(REMOTE_INPUT_KEY)
    ?.toString()
```
This guarantees compile-time safety for optional intent extras, system service retrievals, and notification actions.

### 3. Structured Concurrency via Coroutines vs Handler Loops
`SleepTimerService.java` currently uses `android.os.Handler` with `postDelayed` to manage four distinct async operations:
1. `expiryRunnable`: Timer expiration callback.
2. `fadeRunnable`: 15-step volume fade animation (30-second duration).
3. `notificationRunnable`: 1-minute notification time display refresh.
4. `inputPollRunnable`: 1-minute polling loop for music status and volume changes.

Managing callbacks manually requires explicit cleanup in `cancelTimerCallbacks()` and `onDestroy()` to prevent memory leaks or post-destruction state modifications.

With Kotlin Coroutines:
```kotlin
// Fade step loop with coroutines and structured cancellation
private suspend fun executeVolumeFade(initialVolume: Int) {
    val targetVolume = initialVolume / 2
    for (step in 1..15) {
        delay(2000L) // 30s / 15 steps
        val nextVolume = initialVolume - (initialVolume - targetVolume) * step / 15
        setVolume(nextVolume)
    }
    finishExpiry()
}
```
Benefits:
- Scope-bound coroutine jobs automatically cancel when the service or timer stops, eliminating callback leaks.
- Sequential, imperative code structure replaces fragmented `Runnable` posts.

### 4. Boilerplate Reduction and Expressive Syntax
Kotlin reduces boilerplate through language features well-suited for Android development:
- **Property Accessors**: Replaces getter/setter verbosity.
- **Extension Functions**: Simplifies framework calls (e.g., `context.getSystemService<AudioManager>()`).
- **Standard Scope Functions**: `apply`, `also`, `let`, and `run` streamline `Notification.Builder` and `Intent` initialization.
- **String Templates**: Replaces `String.format` or resource parameters cleanly.
- **Delegated Properties**: Simplifies `SharedPreferences` reads and writes via delegated properties or Kotlin extensions (`edit { ... }`).

---

## Cons of Adopting Kotlin for auto-sleep-droid

### 1. APK Footprint and Runtime Overhead
For small Android utilities, binary size is a critical performance metric.
- **Current Java Build**: The debug APK is ~150-200 KB because it contains no external runtime libraries beyond the Android framework.
- **Kotlin Standard Library (`kotlin-stdlib`)**: Compiling Kotlin introduces `kotlin-stdlib`, adding approximately 1.5 MB - 2.5 MB of uncompressed bytecode (or ~300-500 KB compressed APK size).
- **R8 / Minification Requirement**: To offset this footprint, R8 code shrinking (`minifyEnabled true`) must be enabled. However, R8 increases build times, requires ProGuard rule verification, and introduces risk of over-aggressive stripping in notification listener reflection or service bindings.

For a notification-only app designed to be lightweight, tripling the APK size is a notable downside.

### 2. F-Droid Compliance and Reproducible Build Complexities
Auto-sleep-droid is built with F-Droid reproducibility in mind (`dependenciesInfo` with `includeInApk = false` and `includeInBundle = false`).
- **Kotlin Compiler Metadata**: The Kotlin compiler injects `@kotlin.Metadata` annotations into generated `.class` files. Differences in Kotlin compiler versions, build environment paths, or Kotlin plugin patches can cause non-deterministic bytecode generation.
- **Toolchain Pinning**: Achieving deterministic, reproducible Kotlin builds requires strict compiler flag configurations (e.g., `-Xno-param-assertions`, `-Xno-call-assertions`, `-Xtype-enhancement-improvements-strict-mode`) and exact Gradle plugin pinning across build environments.
- **Java 17 Determinism**: Pure Java compilation via `javac` is inherently more deterministic and simpler to verify across different F-Droid build daemons.

### 3. Build Time and Toolchain Overhead
- **Gradle Compilation Time**: The Kotlin Gradle Plugin (`org.jetbrains.kotlin.android`) increases build configuration time and compilation latency compared to pure `javac`.
- **Daemon Memory**: Running the Kotlin daemon alongside the Gradle daemon increases RAM consumption during builds, which impacts CI/CD runners and low-spec development environments.

### 4. Modern Java 17 Already Addresses Historical Java Pain Points
Auto-sleep-droid uses Java 17 (`JavaVersion.VERSION_17`). Java 17 provides modern features that eliminate much of the traditional "legacy Java" verbosity:
- `var` type inference for local variables.
- Switch expressions and pattern matching.
- Text blocks for clean multi-line string resources or logging.
- `java.time` APIs.

Because the entire application is under 500 lines of code across 4 files, the maintenance burden of Java 17 is already minimal.

### 5. Migration Risk vs. User Value
Refactoring 100% of the existing codebase from Java to Kotlin introduces regression risk in subtle runtime flows:
- `RemoteInput` result extraction and numeric keyboard presentation in notification shade replies.
- `AudioManager.isMusicActive()` and stream volume change tracking during active fades.
- Re-testing boot completed broadcast receiver behaviors across diverse Android API levels (minSdk 26 to targetSdk 35).

Since auto-sleep-droid is feature-complete and meets all requirements in `SPEC.md`, rewriting working code provides no direct user-facing benefit.

---

## Architectural Comparison Matrix

| Feature / Aspect | Java 17 (Current Implementation) | Kotlin (Proposed Migration) |
|---|---|---|
| **Lines of Code (LOC)** | ~450 LOC | ~280 LOC (~35% reduction) |
| **APK Size (Debug)** | ~180 KB | ~1.8 MB (without R8) / ~350 KB (with R8) |
| **State Representation** | Multiple mutable `boolean` flags | `sealed interface TimerState` |
| **Async Operations** | `Handler.postDelayed()` & `Runnable` | Coroutines & `delay()` / `Flow` |
| **Null Safety** | Defensive manual checks (`!= null`) | Built-in compile-time nullability |
| **Build Dependencies** | Android SDK only | `kotlin-stdlib`, Kotlin Gradle Plugin |
| **F-Droid Reproducibility** | High (Pure `javac` execution) | Medium (Requires Kotlin compiler flag tuning) |
| **Maintenance Complexity** | Low (Small codebase, familiar Java) | Low-Medium (Requires Kotlin knowledge) |

---

## Concrete Code Comparison Examples

### Example 1: Duration Input Parsing

#### Current Java 17 (`SleepTimerService.java`)
```java
private void handleDurationReply(Intent intent) {
    CharSequence reply = RemoteInput.getResultsFromIntent(intent) == null
            ? null
            : RemoteInput.getResultsFromIntent(intent).getCharSequence(REMOTE_INPUT_KEY);

    int duration = -1;
    if (!TextUtils.isEmpty(reply)) {
        try {
            duration = Integer.parseInt(reply.toString().trim());
        } catch (NumberFormatException ignored) {
            duration = -1;
        }
    }

    if (isValidDuration(duration)) {
        configuredDurationMinutes = duration;
    } else if (!isValidDuration(configuredDurationMinutes)) {
        configuredDurationMinutes = DEFAULT_DURATION_MINUTES;
    }
    // ...
}
```

#### Equivalent Kotlin
```kotlin
private fun handleDurationReply(intent: Intent) {
    val reply = RemoteInput.getResultsFromIntent(intent)
        ?.getCharSequence(REMOTE_INPUT_KEY)
        ?.toString()
        ?.trim()

    val duration = reply?.toIntOrNull()
    if (duration != null && isValidDuration(duration)) {
        configuredDurationMinutes = duration
    } else if (!isValidDuration(configuredDurationMinutes)) {
        configuredDurationMinutes = DEFAULT_DURATION_MINUTES
    }
    // ...
}
```

---

### Example 2: Pausing Active Media Sessions

#### Current Java 17 (`MediaSessionAccessService.java`)
```java
public static void pauseAll(Context context) {
    MediaSessionManager sessionManager =
            (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
    if (sessionManager == null) {
        return;
    }

    try {
        ComponentName listener = new ComponentName(context, MediaSessionAccessService.class);
        List<MediaController> sessions = sessionManager.getActiveSessions(listener);
        for (MediaController controller : sessions) {
            if (controller.getTransportControls() != null) {
                controller.getTransportControls().pause();
            }
        }
    } catch (SecurityException ignored) {
        // Notification access has not been granted yet.
    }
}
```

#### Equivalent Kotlin
```kotlin
object MediaSessionAccessService : NotificationListenerService() {
    fun pauseAll(context: Context) {
        val sessionManager = context.getSystemService<MediaSessionManager>() ?: return
        try {
            val listener = ComponentName(context, MediaSessionAccessService::class.java)
            val sessions = sessionManager.getActiveSessions(listener) ?: return
            for (controller in sessions) {
                controller.transportControls?.pause()
            }
        } catch (ignored: SecurityException) {
            // Notification access has not been granted yet.
        }
    }
}
```

---

## Verdict & Recommendations

### Recommendation: **Retain Java 17 for Current Scope**

For **auto-sleep-droid** in its current form, **retaining Java 17 is recommended**.

#### Rationale:
1. **Size & Efficiency First**: auto-sleep-droid is a single-purpose utility with no UI. Keeping the APK under 200 KB with zero external dependencies aligns perfectly with the application's minimal, background-oriented design.
2. **F-Droid Build Stability**: Avoiding `kotlin-stdlib` and Kotlin compiler metadata simplifies reproducible build verification on F-Droid.
3. **Small Codebase Overhead**: At ~450 lines of Java code, the verbosity penalty of Java is negligible and easily readable.

---

### Alternative Scenario: **When Migration to Kotlin is Justified**

A migration to Kotlin should be reconsidered if any of the following project goals emerge:
1. **Feature Expansion**: Adding complex user features (e.g., custom fade curves, multi-timer presets, scheduling, Jetpack Glance widgets).
2. **Jetpack Compose / Glance Adoption**: If the project decides to add a full settings UI or Glance notification widgets using modern Jetpack libraries that require Kotlin.
3. **Architecture Redesign**: Rewriting `SleepTimerService` into a formal Kotlin `StateFlow`-driven state machine for unit testability.

---

## Summary Checklist for Potential Kotlin Migration

If the project owners decide to move forward with a Kotlin migration in the future, the following steps must be taken:

- [ ] Add Kotlin Gradle Plugin (`org.jetbrains.kotlin.android`) to root `build.gradle` and `app/build.gradle`.
- [ ] Configure R8 / ProGuard (`minifyEnabled true`) in `app/build.gradle` to strip unused `kotlin-stdlib` classes.
- [ ] Configure Kotlin compiler flags for reproducible builds in `app/build.gradle`:
  ```groovy
  tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).configureEach {
      kotlinOptions {
          freeCompilerArgs += [
              "-Xno-param-assertions",
              "-Xno-call-assertions"
          ]
      }
  }
  ```
- [ ] Migrate components in order: `BootReceiver` -> `MediaSessionAccessService` -> `MainActivity` -> `SleepTimerService`.
- [ ] Verify F-Droid build reproducibility and APK binary size delta.
- [ ] Conduct thorough manual verification of notification inline replies, volume resets, and reboot recovery.
