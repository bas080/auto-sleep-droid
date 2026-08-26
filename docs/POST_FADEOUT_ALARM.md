# Post-Fadeout Alarm & Audio Resumption Feature

## Overview & Motivation

This document specifies the design and behavior of the **Post-Fadeout Alarm** and **Post-Fadeout Audio Resumption** features in Auto Sleep Droid.

### Motivation
When using a sleep timer, a user plays audio (podcasts, music, white noise, audiobooks) to help them fall asleep. However, setting a fixed morning alarm (e.g., 7:00 AM) can result in:
- **Undersleeping** if the user takes longer to fall asleep or resets the timer multiple times.
- **Oversleeping** if the user falls asleep earlier than expected.

By dynamically scheduling a wake-up event **$N$ hours after volume fade-out completes**, the app aligns wake-up time with when the user actually fell asleep, ensuring they get their target duration of sleep (e.g., 7 or 8 hours).

---

## User Interface & Configuration

The main UI (`MainActivity`) provides controls placed at the top of the screen, directly above the timestamped event log (`ScrollView`).

### UI Elements & Interactions

1. **Set Wake-up Duration Button:**
   - Positioned at the top of the main UI layout above the logs.
   - Tapping the button opens a clean integer input dialog allowing the user to enter target sleep duration $N$ in hours (with a valid range of 1 to 12 hours).

2. **Dialog Cancel / Clear Action:**
   - Tapping **Cancel** in the dialog or entering `0` clears and disables the post-fadeout wake-up configuration.
   - Clearing sets the feature state to disabled in `SharedPreferences` and resets the UI status text.

3. **Status Description Text:**
   - Rendered directly underneath the configuration button, and above the debug event log.
   - Displays relevant status text reflecting the current post-fadeout wake-up configuration:
     - When configured: Displays the configured sleep duration (e.g., `"Post-fadeout wake-up: 8h after fade-out"`).
     - When not configured / cleared: Displays fallback text (e.g., `"Post-fadeout wake-up: Off"`).

---

## Technical Architecture Options

### Option A: External Clock App Intent Integration
Auto Sleep Droid delegates alarm management to the device's default clock app using standard Android system intents.

- **Action:** `android.provider.AlarmClock.ACTION_SET_ALARM`
- **Extras:**
  - `EXTRA_HOUR`: Calculated 24-hour target hour.
  - `EXTRA_MINUTES`: Calculated target minute.
  - `EXTRA_MESSAGE`: `"auto-sleep-droid"` (used to find and update existing created alarm).
  - `EXTRA_SKIP_UI`: `true` (schedules alarm in background without launching full clock app UI).
- **Permission Required:** `<uses-permission android.permission.SET_ALARM />`

### Option B: Post-Fadeout Audio Resumption (Simplified In-App Mechanism)
Instead of invoking external clock apps (which vary in behavior across device OEMs), the app can simply **resume audio playback $N$ hours after fade-out completes**.

- **Workflow:**
  1. Sleep timer fade-out completes and pauses active media playback.
  2. `SleepTimerService` schedules an internal `AlarmManager.setExactAndAllowWhileIdle()` countdown set for $N$ hours in the future.
  3. Upon expiration, `SleepTimerService` requests transient Audio Focus (`AudioManager.requestAudioFocus`) and issues a media play command (`KEYCODE_MEDIA_PLAY` or `MediaSession` play call).
  4. Audio starts playing again to gently wake the user up.
- **Advantages:**
  - Completely avoids OEM clock app quirks (e.g. OEMs ignoring `EXTRA_SKIP_UI` or failing to overwrite labeled alarms).
  - Requires zero external permissions beyond existing media playback capabilities.
  - Simplifies user interaction to a basic integer input (1 to 12 hours) and direct audio resumption.

---

## Technical Issues Requiring Attention for Option B Implementation

Before implementing Option B (Post-Fadeout Audio Resumption), the following key Android platform issues and edge cases must be addressed:

### 1. Media Session Termination & Low Memory Killer (LMK)
- **Problem:** Over the course of an 8-hour sleep period, Android's Low Memory Killer (LMK) or Doze mode battery optimization will likely terminate background third-party media player processes (e.g. Spotify, Pocket Casts, YouTube Music).
- **Impact:** Sending a generic media key intent (`KEYCODE_MEDIA_PLAY`) or requesting `AudioManager` focus when no media app is active in memory will fail to resume playback.
- **Solution Strategy:**
  - Utilize `MediaSessionManager` / `MediaSessionAccessService` (which Auto Sleep Droid already implements) to inspect active media sessions.
  - If no active session exists when the timer fires, dispatch a explicit launch `PendingIntent` for the last active media app package, or fallback to broadcasting `ACTION_MEDIA_BUTTON` (`KEYCODE_MEDIA_PLAY`) targeting the last known media app package.

### 2. Exact Alarm Scheduling & Doze Mode Restrictions (Android 12+)
- **Problem:** On Android 12+ (API 31+), `AlarmManager.setExactAndAllowWhileIdle()` requires the `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM` permission. If exact alarm permission is denied by the user or restricted by the OS, `AlarmManager` falls back to inexact alarms.
- **Impact:** Inexact alarms in Doze mode can be delayed by 15 to 30 minutes, resulting in delayed wake-up times.
- **Solution Strategy:**
  - Check `AlarmManager.canScheduleExactAlarms()` before setting the wake-up timer.
  - If granted, use `setExactAndAllowWhileIdle()`. If missing, notify the user or prompt for exact alarm permission on main UI launch, falling back gracefully to `setAndAllowWhileIdle()`.

### 3. Service Survival & WakeLock Execution in Deep Doze Mode
- **Problem:** Devices entering deep Doze mode throttle CPU frequency and disable network/background tasks. If `SleepTimerService` is killed or unbound, the wake-up timer callback must execute reliably while screen is off.
- **Solution Strategy:**
  - Use a `BroadcastReceiver` registered in `AndroidManifest.xml` (or an `AlarmManager` `PendingIntent`) as the trigger for the $N$-hour wake-up callback.
  - Acquire a temporary `PowerManager.WakeLock` (`PARTIAL_WAKE_LOCK`) inside the broadcast receiver for up to 10 seconds to ensure the CPU stays active while requesting Audio Focus and firing the media play command.

### 4. Audio Volume Ramp (Avoid Abrupt Loud Playback)
- **Problem:** When media was paused at fade-out completion, pre-fade volume was restored. Resuming audio playback abruptly after 8 hours at full pre-fade volume can startle the user awake.
- **Solution Strategy:**
  - Store `preFadeVolume` and start audio resumption at zero or low volume ($10\%$).
  - Over a 15–30 second fade-in period, gradually ramp up the music stream volume (`STREAM_MUSIC`) to the user's pre-fade volume level using an ease-in curve.

---

## User Workflow & Experience

1. **Configuration:** The user opens the main app screen, taps the duration button above the logs, enters integer hours $N$ (e.g. `8`), and confirms. Status text underneath updates immediately.
2. **Playback & Fade:** The user plays audio, and Auto Sleep Droid counts down to volume fade-out.
3. **Fade Completion:** When volume fade-out finishes and media playback is paused, Auto Sleep Droid calculates target wake-up time ($N$ hours post-fadeout).
4. **Wake-up Trigger:** Depending on configured mode:
   - *Option A (Intent Alarm):* Dispatches `ACTION_SET_ALARM` with label `"auto-sleep-droid"` to the default alarm app.
   - *Option B (Audio Resumption):* Internal timer fires $N$ hours later and resumes media playback.

---

## Edge Cases & Considerations

1. **Midnight & Next-Day Rollover:** Calculated target time automatically handles rolling over into the next day (e.g., 11:30 PM + 8 hours = 7:30 AM next day).
2. **Cancelled Fade-Out:** If fade-out is interrupted or cancelled, no post-fadeout wake-up alarm or audio resumption is scheduled.
3. **Integer Input Bounds:** Input is validated between 1 and 12 hours; invalid or out-of-range inputs safely fall back to cleared/disabled state.
