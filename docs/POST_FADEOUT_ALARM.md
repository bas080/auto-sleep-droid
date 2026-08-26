# Post-Fadeout Alarm Feature

## Overview & Motivation

This document specifies the design and behavior of the **Post-Fadeout Alarm** feature in Auto Sleep Droid.

### Motivation
When using a sleep timer, a user plays audio (podcasts, music, white noise, audiobooks) to help them fall asleep. However, setting a fixed morning alarm (e.g., 7:00 AM) can result in:
- **Undersleeping** if the user takes longer to fall asleep or resets the timer multiple times.
- **Oversleeping** if the user falls asleep earlier than expected.

The Post-Fadeout Alarm solves this by dynamically scheduling a wake-up alarm **$N$ hours after volume fade-out completes**. Because volume fade-out marks the estimated time the user fell asleep, setting an alarm relative to fade-out completion ensures the user gets their target duration of sleep (e.g., 7.5 or 8 hours) regardless of when they drifted off.

---

## User Interface & Configuration

The main UI (`MainActivity`) provides controls placed at the top of the screen, directly above the timestamped event log (`ScrollView`).

### UI Elements & Interactions

1. **Set Alarm Duration Button:**
   - Positioned at the top of the main UI layout above the logs.
   - Tapping the button opens a native Android `TimePickerDialog` in 24-hour mode (`is24HourView = true`), where the hour wheel (0–23) represents target sleep hours and the minute wheel (0–59) represents target sleep minutes $N$ (e.g., 8 hours 0 minutes).

2. **Time Picker Cancel / Clear Action:**
   - Tapping **Cancel** in the `TimePickerDialog` or setting the duration to `0h 0m` disables/clears the post-fadeout alarm configuration.
   - Clearing sets `post_fadeout_alarm_enabled = false` in `SharedPreferences` and resets the UI status text.

3. **Status Description Text:**
   - Rendered directly underneath the configuration button, and above the debug event log.
   - Displays relevant status text reflecting the current post-fadeout alarm configuration:
     - When configured: Displays the configured sleep duration (e.g., `"Post-fadeout alarm: 8h 0m after fade-out"`).
     - When not configured / cleared: Displays fallback text (e.g., `"Post-fadeout alarm: Off"`).

---

## User Workflow & Experience

1. **Configuration:** The user opens the main app screen, taps the alarm button above the logs, selects $N$ hours and minutes using the 24-hour `TimePickerDialog`, and confirms. The status text underneath updates immediately.
2. **Playback & Fade:** The user plays audio, and Auto Sleep Droid counts down to volume fade-out.
3. **Fade Completion:** When the 30-second volume fade-out finishes and media playback is paused, Auto Sleep Droid calculates the target wake-up time:
   $$\text{Alarm Time} = \text{Fade-Out Completion Time} + N \text{ hours}$$
4. **Alarm Scheduling:** Auto Sleep Droid dispatches an Android system Intent to set or update an alarm set for $\text{Alarm Time}$ in the phone's default clock app.
5. **Wake-up:** The phone's default alarm app rings $N$ hours after the user's media finished fading out.

---

## Android Intent Integration

Auto Sleep Droid delegates alarm management to the device's default clock app using standard Android system intents rather than maintaining an in-app alarm player.

### Intent Action & Extras

The app launches `android.provider.AlarmClock.ACTION_SET_ALARM` with the following extras:

| Extra | Constant | Description | Value |
|---|---|---|---|
| **Action** | `AlarmClock.ACTION_SET_ALARM` | Intent action to create/set an alarm in the default clock app | `"android.intent.action.SET_ALARM"` |
| **Hour** | `AlarmClock.EXTRA_HOUR` | Target hour of the day in 24-hour format (0–23) | Calculated from target wake-up time |
| **Minutes** | `AlarmClock.EXTRA_MINUTES` | Target minute of the hour (0–59) | Calculated from target wake-up time |
| **Message** | `AlarmClock.EXTRA_MESSAGE` | Label identifying the alarm in the clock app | `"auto-sleep-droid"` |
| **Skip UI** | `AlarmClock.EXTRA_SKIP_UI` | Bypasses opening the clock app UI so setting happens silently | `true` |

### Required Permission

The feature requires the standard Android permission declared in `AndroidManifest.xml`:

```xml
<uses-permission android.permission.SET_ALARM />
```

*Note:* `com.android.alarm.permission.SET_ALARM` is a `normal` level permission granted automatically at app installation. It does not require runtime permission prompts.

---

## Resolved Technical Architecture & Solutions

### 1. `TimePickerDialog` 24-Hour Mode for Relative Duration Entry
- **Resolution:** `TimePickerDialog` instantiated with `is24HourView = true` displays an intuitive 0–23 hour and 0–59 minute selection without AM/PM indicators.
- **Handling Zero & Cancel:** Setting `00:00` or clicking **Cancel** sets `enabled = false` in `SharedPreferences`, updating the status text to `"Post-fadeout alarm: Off"`.

### 2. Service-Context Background Intent Execution
- **Resolution:** Because `SleepTimerService` is an active Android Foreground Service (`foregroundServiceType="mediaPlayback"`), launching the system activity intent from background context requires adding `Intent.FLAG_ACTIVITY_NEW_TASK`.
- **Background Launch Exemption:** Foreground services executing user-triggered sleep routines are exempted from Android 10+ background activity start restrictions when sending standard system intents with `EXTRA_SKIP_UI = true`.

### 3. Alarm Reuse & Label Matching
- **Resolution:** Passing `EXTRA_MESSAGE = "auto-sleep-droid"` ensures that on standard AOSP / Google Clock implementations, existing alarms with the label `"auto-sleep-droid"` are overwritten with the new wake-up time rather than creating duplicated alarm entries.

### 4. Intent Receiver Validation
- **Resolution:** Before launching the intent, `intent.resolveActivity(packageManager)` checks that a handler exists. If no clock app is available, `SleepTimerService` logs a warning to `EventLogger` without crashing.

---

## Remaining / Leftover Issues

The following edge cases remain inherent to Android system intent delegation across heterogeneous device ecosystems:

1. **OEM Clock App Behavior Variations:**
   - Certain customized OEM ROMs (e.g. MIUI/HyperOS, ColorOS) do not respect `EXTRA_SKIP_UI = true` and may force-open the OEM Clock UI when the intent is dispatched, or may ignore `EXTRA_MESSAGE` matching and create duplicate alarms instead of updating the existing `"auto-sleep-droid"` alarm.
   - *Mitigation:* Document in user notes that stock Google Clock or AOSP Clock apps provide the cleanest background alarm reuse experience.

2. **Accidental Dismissal via Time Picker Cancel:**
   - Tapping "Cancel" in the `TimePickerDialog` clears the configured post-fadeout alarm. If a user opens the picker just to view current settings and taps Cancel, the configuration will be cleared.
   - *Mitigation:* Future UI iterations can add an explicit "Clear" action button alongside the "Set Alarm Duration" button to separate cancellation of the dialog from clearing the stored preference.

---

## Edge Cases & Technical Considerations

### 1. Midnight & Next-Day Rollover
When adding $N$ hours to the fade-out completion time, the target wake-up time often rolls over into the next calendar day (e.g., fade-out completes at 11:30 PM, target sleep duration is 8 hours $\rightarrow$ alarm target is 7:30 AM next day).
- Target hour and minute calculations use standard system time utilities (`java.util.Calendar` or `java.time.ZonedDateTime`).
- Android's `ACTION_SET_ALARM` automatically schedules the alarm for the next occurrence of the specified `EXTRA_HOUR` and `EXTRA_MINUTES`.

### 2. Interrupted or Cancelled Fade-Out
- If volume fade-out is cancelled before completion (e.g., due to user pressing volume buttons or flipping the phone), no alarm is scheduled.
- Alarm intent creation occurs exclusively upon successful transition out of `Fading` state when media is paused and volume is restored.

### 3. User Disabling Feature
- The user can clear/disable the post-fadeout alarm feature via the Time Picker cancel action or UI controls. When disabled, fade-out completes without launching an alarm intent.
