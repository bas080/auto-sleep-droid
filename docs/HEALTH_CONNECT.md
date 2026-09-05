# Health Connect Integration

## Overview

Auto Sleep Droid integrates with Android Health Connect (`androidx.health.connect:connect-client`) to automatically record sleep sessions (`SleepSessionRecord`) based on sleep timer expiration, nap alarm completions, and wake alarm dismissals.

## Product Goal

Automatically log your nightly sleep sessions and daytime naps into Android Health Connect without requiring manual sleep tracking apps or wearables. When you fall asleep listening to media or take a quick nap, Auto Sleep Droid records the start and end of your sleep cycle directly into Health Connect upon alarm dismissal.

## Architecture and Flow

1. **Sleep & Nap Start Time Capture**:
   - **Nightly Sleep**: When the sleep timer expires and active media playback is paused (or when volume fade completes), the exact timestamp (`sleep_start_time_ms`) is persisted to `SharedPreferences`.
   - **Naps**: When a nap alarm is started, the nap start timestamp (`nap_start_time_ms`) is persisted to `SharedPreferences`.
2. **Wake Time Capture**:
   - When a scheduled wake-up alarm or nap alarm triggers and is dismissed (via notification action button, hardware volume key press, or main UI), the current timestamp (`wake_time_ms`) is captured as the wake time.
3. **Session Persistence**:
   - If `health_connect_enabled` is true, Health Connect SDK is available on the device, and write permission (`android.permission.health.WRITE_SLEEP`) is granted:
     - Auto Sleep Droid constructs a `SleepSessionRecord` with `startTime` set to `nap_start_time_ms` (for naps) or `sleep_start_time_ms` (for nightly sleep) and `endTime` set to `wake_time_ms`, along with local system zone offsets (`ZoneOffset`).
     - Asynchronously writes the record to Health Connect via `HealthConnectClient.insertRecords(...)` on a background I/O thread.
     - Logs the outcome to `EventLogger`.
     - Clears the pending start timestamp to prevent duplicate session records.

## Data Schema

The Health Connect integration uses `androidx.health.connect.client.records.SleepSessionRecord`:

- `startTime`: `Instant` created from `sleep_start_time_ms`.
- `endTime`: `Instant` created from `wake_time_ms`.
- `startZoneOffset`: System local `ZoneOffset` at sleep start time.
- `endZoneOffset`: System local `ZoneOffset` at wake time.
- `title`: "Sleep"

## Permissions and Compatibility

- **Permissions**: `<uses-permission android:name="android.permission.health.WRITE_SLEEP" />`
- **Android 14+ (API 34+)**: Health Connect is integrated into the Android framework. Permission usage activity alias `ViewPermissionUsageActivity` is registered in `AndroidManifest.xml`.
- **Android 13 and below (API 26-33)**: Requires the Health Connect app installed from Google Play Store.
- **SDK Status Check**: Checked dynamically via `HealthConnectClient.getSdkStatus(context)`. If Health Connect is not available on the device, enabling the switch displays an informative Toast message and prevents invalid state activation.

## Settings and User Interface

- Configuration switch located in `MainActivity` under the dedicated Health Connect section (`label_health_connect`).
- Toggling Health Connect ON automatically opens Android Health Connect permission settings for immediate permission configuration.
- Re-checking permission status on `onResume()` automatically disables sync if write permissions are revoked in Health Connect settings.
- Exported and imported seamlessly alongside existing app configuration in JSON format (`health_connect_enabled`).

## Build Size Optimization

Integrating Health Connect and Protocol Buffers adds AndroidX Health Connect, ProtoBuf runtime, and Kotlin coroutines libraries. Enabling R8 minification (`minifyEnabled true`) in `app/build.gradle` trims unused library classes, keeping the release APK size at ~330 KB.

## Questions and Future Considerations

The following questions and potential future enhancements are presented for consideration:

1. **Sleep Session Start Boundaries**:
   - *Current Behavior*: Sleep start time is recorded when the sleep timer expires (when media fades out and pauses).
   - *Question*: Should sleep start time be configurable to measure from when the sleep timer *starts counting down* instead of when media pauses?

2. **Sleep Stages**:
   - *Current Behavior*: Sleep sessions are logged as single uninterrupted sleep sessions (`SleepSessionRecord`).
   - *Question*: Since Auto Sleep Droid does not require wearable heart rate or motion sensors, logging detailed stages (LIGHT, DEEP, REM) is currently omitted. Is single-session logging sufficient, or should we consider manual/heuristic stage estimations?

3. **Wake Alarms Without Sleep Timer**:
   - *Current Behavior*: A sleep session is only persisted if the sleep timer was used prior to sleep (capturing `sleep_start_time_ms`).
   - *Question*: If a user sets a wake alarm but does not use the sleep timer at night, should the app fall back to calculating estimated sleep start as `wakeTime - minSleepDuration` (or `wakeTime - targetSleepHours`), or only log when explicit sleep start events occur?

4. **Session History and Management**:
   - *Current Behavior*: Auto Sleep Droid writes records to Health Connect; viewing and deleting past sessions is handled by the system Health Connect app.
   - *Question*: Would you like Auto Sleep Droid to request `READ_SLEEP` permission in a future update to display past sleep session history directly inside the app's Event Logs or a dedicated History view?
