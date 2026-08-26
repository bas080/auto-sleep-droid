# Post-Fadeout Audio Resumption Feature

# Specification

## Product Goal & Motivation
When using a sleep timer, a user plays audio (podcasts, music, white noise, audiobooks) to help them fall asleep. However, setting a fixed morning alarm (e.g., 7:00 AM) can result in:
- **Undersleeping** if the user takes longer to fall asleep or resets the timer multiple times.
- **Oversleeping** if the user falls asleep earlier than expected.

The Post-Fadeout Audio Resumption feature dynamically schedules a wake-up event **$N$ hours after volume fade-out completes**. Aligning wake-up time directly with when volume fade-out finishes ensures the user gets their target sleep duration (e.g., 7 to 8 hours) regardless of when they drifted off.

## User Interface & Controls
The main UI (`MainActivity`) displays controls positioned at the top of the layout, directly above the timestamped debug event log (`ScrollView`).

- **Set Wake-Up Duration Button:**
  - Located at the top of the main UI layout above the logs.
  - Tapping the button opens a clean numeric input dialog prompting the user to select the target sleep duration $N$ in hours as a float value (e.g. 7.5 hours, valid range: 0.1 to 24.0 hours).

- **Clear Button:**
  - Positioned next to the duration selection button at the top of the main UI.
  - Tapping the **Clear** button (or entering `0` in the dialog) disables and clears the post-fadeout wake-up configuration, setting `post_fadeout_wake_up_enabled = false` and resetting the status text to `"Post-fadeout audio resumption: Off"`.

- **Dialog Cancel Action:**
  - Tapping **Cancel** inside the duration input dialog simply closes/dismisses the dialog window without clearing, altering, or resetting any previously configured post-fadeout wake-up duration.

- **Status Description Text:**
  - Positioned directly underneath the duration and clear buttons, and above the debug event log.
  - Displays status text reflecting the active configuration:
    - Configured: `"Post-fadeout audio resumption: 8h after fade-out"`
    - Disabled / Cleared: `"Post-fadeout audio resumption: Off"`

## User Workflow & Visible Behavior
1. **Configuration:** The user opens `MainActivity`, taps the duration button above the logs, inputs float hours $N$ (e.g., `7.5`), and confirms. The status text updates immediately.
2. **Dismissing Dialog:** If the user opens the duration dialog and taps **Cancel**, the dialog closes without changing the current configuration.
3. **Clearing Configuration:** Tapping the **Clear** button next to the duration button explicitly disables the post-fadeout wake-up feature and resets status text.
4. **Timer & Fade-Out:** The user plays media audio and the sleep timer counts down to expiration.
5. **Fade-Out Completion:** When the 30-second volume fade-out finishes and media is paused, Auto Sleep Droid calculates target wake-up time:
   $$\text{Wake-Up Time} = \text{Fade-Out Completion Time} + N \text{ hours}$$
6. **Wake-Up Execution:** Exactly $N$ hours post-fadeout, the app requests Audio Focus and resumes audio playback.
7. **Wake-Up Cancellation:** Stopping/turning off the sleep timer or restarting a timer countdown cancels any scheduled post-fadeout wake-up alarm.

---

# Implementation

## Post-Fadeout Audio Resumption Architecture
Auto Sleep Droid schedules an internal wake-up timer to resume audio playback directly in-app $N$ hours after fade-out completes.

- **Workflow:**
  1. Upon fade-out completion, `SleepTimerService` schedules `AlarmManager.setExactAndAllowWhileIdle()` set for $N$ hours in the future.
  2. Upon expiration, `SleepTimerService` requests transient Audio Focus via `AudioManager.requestAudioFocus(AUDIOFOCUS_GAIN)`.
  3. `SleepTimerService` inspects active media sessions via `MediaSessionManager` / `MediaSessionAccessService` and issues a media play command (`KEYCODE_MEDIA_PLAY` or target package playback intent).
  4. Music stream volume (`STREAM_MUSIC`) starts low and gradually ramps up over a 15–30 second ease-in curve to the user's pre-fade volume, gently waking the user up.

---

# Open Issues & Future Considerations

The following technical issues require evaluation and resolution during feature implementation:

## 1. Third-Party Media Session Survival & LMK
- **Problem:** Over an 8-hour sleep period, Android's Low Memory Killer (LMK) or Doze battery optimizations may terminate background third-party media player apps (e.g., Spotify, Pocket Casts).
- **Impact:** Generic `KEYCODE_MEDIA_PLAY` key events or `AudioManager` focus requests will fail to resume audio if no player process is alive in memory.
- **Resolution Strategy:** Use `MediaSessionManager` / `MediaSessionAccessService` to inspect active sessions. If no session is active when the timer fires, launch an explicit `PendingIntent` or target package broadcast for the last active media app.

## 2. Exact Alarm Permissions & Doze Throttling on Android 12+
- **Problem:** `AlarmManager.setExactAndAllowWhileIdle()` requires `SCHEDULE_EXACT_ALARM` permission on Android 12+ (API 31+). If missing, alarms fall back to inexact timing.
- **Impact:** Inexact alarms during Doze mode can be delayed by 15–30 minutes.
- **Resolution Strategy:** Query `AlarmManager.canScheduleExactAlarms()`. If missing, prompt the user in `MainActivity` or fall back safely with logged warnings.

## 3. Screen-Off CPU Execution & WakeLocks
- **Problem:** Devices in deep Doze mode restrict background CPU execution while the screen is off.
- **Resolution Strategy:** Trigger the wake-up callback via a manifest-registered `BroadcastReceiver` or `AlarmManager` `PendingIntent`, acquiring a temporary `PowerManager.WakeLock` (`PARTIAL_WAKE_LOCK`) for up to 10 seconds to hold CPU state while requesting Audio Focus and starting playback.

## 4. Audio Volume Ramp-Up Curve
- **Problem:** Resuming audio playback abruptly after 8 hours at full pre-fade volume can startle the user.
- **Resolution Strategy:** Capture `preFadeVolume`, start audio resumption at zero/low volume ($10\%$), and gradually ramp up stream volume (`STREAM_MUSIC`) over a 15–30 second ease-in curve.
