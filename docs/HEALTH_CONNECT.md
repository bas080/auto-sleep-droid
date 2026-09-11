# Health Connect Integration Architecture

This document describes the design, permissions, session filtering, data formatting, and permission revocation behavior for Android Health Connect integration in Auto Sleep Droid.

## Overview
Auto Sleep Droid integrates with Android Health Connect (`androidx.health.connect:connect-client`) to automatically record sleep sessions (`SleepSessionRecord`) based on sleep timer expiration and wake alarm dismissals or "I'm Awake" presses.

## Value Proposition
Automatically log your nightly sleep sessions into Android Health Connect without requiring manual sleep tracking apps or wearables. When you fall asleep listening to media, Auto Sleep Droid records the start and end of your sleep cycle directly into Health Connect upon wake.

## Session Lifecycle & Data Formatting
1. **Sleep Start Time Capture**:
   - **Nightly Sleep**: When the sleep timer is activated or reset, `timer_start_time_ms` is persisted. When the sleep timer expires and media is paused (or media is paused while the timer is active), `sleep_start_time_ms` is persisted.
2. **Wake Time Capture**:
   - When a scheduled wake-up alarm triggers and is dismissed or when **I'm Awake** is tapped, the current timestamp (`wake_time_ms`) is captured as the wake time.
3. **Record Creation**:
   - Auto Sleep Droid constructs a `SleepSessionRecord` with `startTime` set to `timer_start_time_ms` or `sleep_start_time_ms` (falling back to `wake_time_ms - min_sleep_duration`) and `endTime` set to `wake_time_ms`, along with local system zone offsets (`ZoneOffset`).
4. **Duration Safeguard Threshold**:
   - Configurable minimum session threshold (`hc_min_duration_minutes`, default 15 minutes, range 0–2h, step 5m) configured under the Health Connect section in `MainActivity`. Sleep sessions shorter than this threshold are filtered out before writing to Health Connect.

## Permissions & Consent Workflow
- Declare `android.permission.health.WRITE_SLEEP` in `AndroidManifest.xml`.
- When the Health Connect toggle is turned ON in `MainActivity`, the app checks for write permissions. If missing, it launches the app-specific Health Connect permission management screen (`android.health.connect.action.MANAGE_HEALTH_PERMISSIONS`).
- When the Health Connect toggle is turned OFF in `MainActivity`, the app revokes all Health Connect permissions via `PermissionController.revokeAllPermissions()`. Revoking permissions stops future syncs while retaining previously recorded data in Health Connect.
