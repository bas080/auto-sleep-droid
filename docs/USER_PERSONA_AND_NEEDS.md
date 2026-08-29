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
* **Auto Sleep Droid Solution**: Uses the **Smart Wake-Up Goal** feature. Auto Sleep Droid calculates `Math.max(desiredWakeTime, bedtime + timerDuration + minSleepSafeguard)` and schedules a system wake-up alarm automatically. *(Note: Short daytime power naps are out of scope for the Smart Wake-Up Goal feature; see `docs/NAP_FEATURE_OPTIONS.md` for nap feature research).*

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
When configuring a wake-up goal (e.g. `6:30 AM` for day workers or `4:30 PM` for nightshift workers), the user reasons: "I want to wake up at my target time, but if I fall asleep late, I need my minimum sleep safeguard enforced." The app automatically enforces `min_sleep_duration_minutes` so the alarm dynamically shifts regardless of whether sleep occurs at night or during the day. (Daytime power naps are intentionally distinct and unsupported by this main sleep feature; see research in `docs/NAP_FEATURE_OPTIONS.md`).

---

## 5. User Journey & Workflow

```
+-----------------------------------------------------------------------------------+
| 1. PRE-SLEEP / BEDTIME (Nighttime or Post-Nightshift)                             |
| User starts media playback (podcast/music/audiobook) in their favorite app.       |
| SleepTimerService detects audio start and automatically enters ACTIVE state.      |
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

## 6. Feature Scope & Out-of-Scope Clarification

- **Supported Use Cases**:
  - Sleep timer for media playback auto-pause.
  - Flip gesture timer extension.
  - Smart Wake-Up Goal for overnight and nightshift sleep schedule safeguards.
- **Explicitly Unsupported / Future Research**:
  - **Daytime Power Naps**: Short daytime naps (e.g., 20-30 minute power naps relative to sleep start) are not covered by the Smart Wake-Up Goal. Feature research and architectural options for power naps are documented separately in `docs/NAP_FEATURE_OPTIONS.md`.

---

## 7. Summary

Auto Sleep Droid fulfills the needs of nighttime and nightshift media listeners by providing a frictionless, screen-free sleep management experience. By combining notification shade controls, natural duration parsing (`7h 30m`, `0.5h`), gesture-based timer extension, and dynamic wake-up goal safeguards, the app aligns perfectly with the mental model of its users.
