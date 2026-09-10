# User Personas, Needs, and Reasoning — Auto Sleep Droid

## Executive Summary

Auto Sleep Droid is an Android sleep timer application designed specifically for people who listen to media (podcasts, audiobooks, white noise, music, or videos) while falling asleep.

Unlike traditional sleep timer apps that require opening a bright, full-screen application UI at bedtime, Auto Sleep Droid is designed around a **zero-gaze, low-friction mental model**: users control the timer directly from the Android notification shade or via intuitive physical gestures (such as flipping the phone) in the dark.

---

## 1. Problem Context & Market Gap

### The Bedtime Screen Dilemma
When preparing for sleep, exposure to blue light and complex visual interfaces stimulates brain activity and disrupts circadian rhythms. Most media players (YouTube, Spotify, Podcast apps) or standalone sleep timer apps require users to:
1. Unlock their phone in the dark.
2. Suffer a burst of bright screen illumination.
3. Navigate menus or open visual clock wheels.

### Unreliable Media Timers
Built-in sleep timers inside media apps suffer from two major flaws:
- **Inconsistent UX**: Every app places its sleep timer in a different sub-menu with varying options.
- **Inflexible Timing**: If the user is still awake when the timer expires, extending it requires unlocking the phone, re-opening the app, and re-setting the timer—waking the user up further.

---

## 2. Target User Personas

### Persona A: Alex — "The Audiobook & Podcast Sleeper"
* **Demographics**: 28 years old, Software Designer.
* **Bedtime Routine**: Listens to history podcasts or audiobooks in bed every night to quiet an overactive mind.
* **Goal**: Fall asleep within 20 to 30 minutes without losing his place in a book or playing media all night.
* **Pain Point**: "If the timer stops while I'm still awake, I have to pick up my phone, turn on the screen, and find the app. That instantly ruins my drowsiness."
* **Auto Sleep Droid Solution**: Uses inline notification replies (`Sleep 30m`) or a simple physical phone flip gesture in the dark to reset the countdown without ever unlocking or looking at the screen.

### Persona B: Sam — "The Sleep Schedule Disciplinarian"
* **Demographics**: 35 years old, Project Manager & Parent.
* **Bedtime Routine**: Goes to bed at varying times depending on family schedule, but must wake up early for work.
* **Goal**: Get at least 7.5 hours of overnight sleep each night and ensure an alarm wakes her up in time for work.
* **Pain Point**: "When I go to bed late, if I set a fixed 6:30 AM alarm, I won't get enough sleep. But if I set a timer for 8 hours, I might oversleep my meeting."
* **Auto Sleep Droid Solution**: Uses the **Smart Wake-Up Goal** feature. Auto Sleep Droid calculates `Math.max(desiredWakeTime, bedtime + timerDuration + minSleepSafeguard)` and schedules a system wake-up alarm automatically.

### Persona C: Morgan — "The Low-Friction Minimalist"
* **Demographics**: 42 years old, Teacher.
* **Bedtime Routine**: Prefers a clutter-free phone and zero unnecessary app interaction.
* **Goal**: Set a sleep timer once and let it run automatically whenever music starts playing.
* **Pain Point**: "I hate apps with complex settings, pop-up ads, or unnecessary background drains."
* **Auto Sleep Droid Solution**: Appreciates the `WAITING` state architecture: the app sits silently in the notification shade, starts the timer automatically when music begins playing, and uses minimal system resources.

### Persona D: Jordan — "The Nightshift Worker"
* **Demographics**: 31 years old, Emergency Room Nurse.
* **Bedtime Routine**: Works 12-hour nightshifts (7:00 PM to 7:00 AM). Returns home and goes to sleep around 8:30 AM in a darkened room.
* **Goal**: Sleep during the day, waking up at 4:30 PM for her next shift, while guaranteeing a minimum 7.5 hours of sleep even if her shift runs late.
* **Pain Point**: "When my nightshift runs late and I don't get into bed until 10:00 AM, a fixed 4:30 PM alarm gives me less than 6.5 hours of sleep. Opening bright apps in my darkened room disrupts my sleep cycle."
* **Auto Sleep Droid Solution**: Uses the **Smart Wake-Up Goal** configured for her target shift wake time (`4:30 PM`). If she falls asleep at 8:30 AM, the alarm triggers at 4:30 PM. If her shift runs late and she falls asleep at 10:00 AM, Auto Sleep Droid's minimum sleep safeguard automatically shifts the wake-up alarm to 5:30 PM to guarantee 7.5 hours of rest. Zero-gaze notification controls allow her to adjust settings without introducing bright screen light into her darkened bedroom.

---

## 3. User Needs & Key Pain Points

| User Need | Traditional Timer Problem | Auto Sleep Droid Solution |
|---|---|---|
| **Zero Bedtime Screen Light** | Bright full-screen app UIs disturb darkness and disrupt melatonin. | Ongoing silent notification shade controls & overlay dialogs. |
| **Effortless Extension** | Extending an expired timer requires unlocking the phone and navigating UIs. | Flip phone gesture (face-up <-> face-down) resets timer instantly in the dark. |
| **Gentle Transitions** | Sudden audio pauses jar the user awake. | Smooth 30-second volume fade-out along an ease-out curve before pausing. |
| **Natural Time Formatting** | Numeric-only inputs require calculating minutes vs hours. | Natural duration format parsing (`20m`, `1h`, `1h 15m`, `7h 30m`, `0.5h`). |
| **Flexible Wake-Up Safeguard** | Fixed alarms don't adapt to late bedtimes or nightshifts; sleep timers don't guarantee wake times. | Smart Wake-Up Goal dynamically schedules alarm respecting minimum sleep duration for overnight or nightshift sleep. |

---

## 4. User Thinking, Reasoning, and Mental Models

### 1. "I want to set it and forget it"
Users think in terms of intent ("I'm going to sleep now for about 30 minutes") rather than configuration. They want the app to adapt to their playback start rather than forcing them to start the timer manually before pressing play on their podcast player.

### 2. "Don't wake me up to tell me I'm going to sleep"
Users expect bedtime tools to be unobtrusive. Heads-up banners, loud notification chimes, or pop-up windows are considered severe UX bugs. Auto Sleep Droid uses `IMPORTANCE_LOW` notifications with `setOnlyAlertOnce(true)` so notifications update silently in the shade.

### 3. "If I'm still awake, I want a physical gesture"
When a user feels the volume fading down while still awake, their reasoning is tactile: "I don't want to open my eyes or look at light." Flipping the phone over on the nightstand cancels the fade, restores pre-fade volume, and resets the countdown without visual engagement.

### 4. "Protect my minimum sleep (Nighttime or Nightshift)"
When configuring a wake-up goal (e.g. `6:30 AM` for day workers or `4:30 PM` for nightshift workers), the user reasons: "I want to wake up at my target time, but if I fall asleep late, I need my minimum sleep safeguard enforced." The app automatically enforces `min_sleep_duration_minutes` so the alarm dynamically shifts regardless of whether sleep occurs at night or during the day.

---

## 5. User Journey & Workflow

```
+-----------------------------------------------------------------------------------+
| 1. PRE-SLEEP / BEDTIME (Nighttime or Post-Nightshift)                             |
| User starts media playback (podcast/music/audiobook) in their favorite app.       |
| MainService detects audio start and automatically enters ACTIVE state.      |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
| 2. BEDTIME ADJUSTMENT (Optional)                                                  |
| User pulls down notification shade to check duration.                             |
| Uses inline reply ("Sleep 45m") or Goal Dialog ("7h 30m") to adjust sleep params. |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
| 3. FALLING ASLEEP / TIMER EXPIRATION                                              |
| Timer reaches expiration; volume fades down smoothly over 30 seconds.              |
| - IF USER IS STILL AWAKE: Flips phone over to cancel fade & extend timer.         |
| - IF USER IS ASLEEP: Volume reaches 0, media pauses via Audio Focus.              |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
| 4. WAKE-UP (Morning or Post-Shift Afternoon)                                      |
| Smart Wake-Up Goal alarm triggers at scheduled time.                              |
| User dismisses from notification shade or flips phone to snooze for 9 minutes.    |
+-----------------------------------------------------------------------------------+
```

---

## 6. Feature Scope & Key Features

- **Supported Use Cases**:
  - Sleep timer for media playback auto-pause.
  - Flip gesture timer extension and volume button resets/dismissals.
  - Quick Nap Alarm accessible directly from the main screen and notification shade.
  - Smart Wake-Up Goal for overnight and nightshift sleep schedule safeguards.

---

## 7. User Roleplay Journey & Friction Analysis Report

### Roleplay Narrative: Alex ("The Audiobook & Podcast Sleeper")
* **Scenario**: Bedtime setup at 11:15 PM, zero-gaze extension in the dark at 11:40 PM, and early afternoon power nap setup at 2:00 PM.
* **Bedtime Setup Workflow**:
  Alex opens `MainActivity` to configure his bedtime settings. He sets the sleep timer duration to 30 minutes via the `DurationInputView` dialog, enables the Wake-Up Goal switch with a target wake time of 6:30 AM, and sets his minimum sleep safeguard to 7.5 hours. Upon starting media playback, the ongoing status notification transitions to `"Fades out at 11:45 PM (30m) • Wake at 6:30 AM"`.
* **Zero-Gaze Extension Workflow**:
  At 11:40 PM, as the volume begins fading down, Alex flips his phone on his nightstand without unlocking the screen. The subtle haptic vibration confirms the action, pre-fade volume is restored, and the timer resets to 12:10 AM. Because the timer was reset late at 11:40 PM with a 7.5-hour minimum sleep safeguard (`11:40 PM + 7.5h = 07:10 AM`), Auto Sleep Droid automatically shifts his scheduled wake alarm from 6:30 AM forward to 7:10 AM to safeguard his rest.
* **Nap Timer Workflow**:
  At 2:00 PM the following afternoon, Alex pulls down his notification shade and taps the "Nap" secondary action button. A translucent dialog appears prefilled with 20 minutes. Confirming the nap schedules an alarm for 2:20 PM and changes the notification action button to "I'm Awake". When the nap alarm triggers, tapping "I'm Awake" stops the alarm, logs the nap session, and resets the action button back to "Nap".

### Identified Friction Points & UX Recommendations
* **Target Wake Time vs. Current Wake Time Clarity**:
  Having two distinct wake time fields ("Target wake-up time" and "Current wake-up time") in `MainActivity` can cause initial confusion. First-time users may wonder why their wake time changed automatically when late resets occur. Adding a brief explanatory label under "Current wake-up time" helps clarify that it automatically adapts to enforce minimum sleep.
* **Duration Input Wheel Pickers without Text Labels**:
  `DurationInputView` displays hour and minute wheel pickers side by side without explicit "hours" and "mins" text labels above or beside the wheels. Users configuring timers in dark conditions benefit from clear visual unit indicators.
* **Secondary Action Omission during Active Sleep Phase**:
  During the Initiation & Active Sleep Phase, the secondary action button in the notification shade is hidden to maintain minimal shade UI. Users waking up midway through the night who check the notification shade will not see a secondary action until the Pre-Alarm Window begins.
* **First-Time Permission Flow for Nap DND**:
  Toggling "Nap DND" for the first time opens system Notification Policy Access settings. Providing an inline explanatory dialog prior to launching system settings improves user context.

---

## 8. Summary

Auto Sleep Droid fulfills the needs of nighttime and nightshift media listeners by providing a frictionless, screen-free sleep management experience. By combining notification shade controls, natural duration parsing (`7h 30m`, `0.5h`), gesture-based timer extension, and dynamic wake-up goal safeguards, the app aligns perfectly with the mental model of its users.
