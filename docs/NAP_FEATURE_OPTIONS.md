# Feature Research & Options: Daytime Power Nap Support

## Overview

This document provides technical research, user requirements, System UI constraints, and architectural options for introducing a **Power Nap Feature** to Auto Sleep Droid.

---

## 1. Context & Distinement from Smart Wake-Up Goal

Auto Sleep Droid currently features a **Smart Wake-Up Goal** designed for overnight sleep:
- **Target Goal Time**: Fixed daily clock time (e.g. `06:30 AM`).
- **Minimum Sleep Safeguard**: Enforces a minimum overnight sleep duration (e.g., 7.5 hours / 450 minutes).
- **Calculation**: Schedules alarm for `Math.max(goalTime, bedtime + timerDuration + minSleepSafeguard)`.

### Why Overnight Goals Fail for Naps
When taking a short daytime power nap (e.g., 20 to 30 minutes, or a full 90-minute REM cycle nap):
- Users do **not** want a target clock time like 6:30 AM.
- Users want an alarm that rings **relative to when they fall asleep** (or after media playback finishes).
- **Nap Formula**: `napAlarmTime = currentTime + sleepTimerDuration + napDuration`.

---

## 2. User Needs & Workflows for Napping

### User Mental Model for Power Naps
1. **Quick Launch**: "I want to take a 20-minute power nap while listening to a podcast."
2. **Dual Timer Concept**:
   - **Media Sleep Timer**: Audio fades out and pauses after 15-20 minutes (as user falls asleep).
   - **Nap Alarm Timer**: System alarm rings 20-30 minutes *after* user falls asleep (or after media timer expires) to wake the user up before entering deep sleep inertia.
3. **Natural Input**: Input nap durations using flexible natural formats (`20m`, `30m`, `45m`, `90m`, `0.5h`).

---

## 3. Android System UI & Framework Constraints

1. **Notification Action Slot Budget**:
   Android notifications render a maximum of **3 visible action buttons**:
   - **Slot 1 (In Use)**: `"Sleep <duration>"` (inline duration input).
   - **Slot 2 (In Use)**: `"Turn Off"` / `"Turn On"` (state toggle).
   - **Slot 3 (In Use)**: `"Set Goal"` / `"Alarm <time>"` (launches `GoalSettingsDialogActivity`).

2. **AlarmManager setAlarmClock Integration**:
   Power nap alarms must use `AlarmManager.setAlarmClock` so the OS displays the upcoming alarm indicator in the status bar and guarantees exact wakeup even under Doze mode.

---

## 4. Analysis of Technical Options

### Option A: Nap Mode in Overlay Dialog (`GoalSettingsDialogActivity`) — **RECOMMENDED**
- **Description**: Extend `GoalSettingsDialogActivity` to offer a mode switch between **Overnight Goal** (Target Clock Time e.g. `6:30 AM` + Minimum Sleep) and **Power Nap** (Nap Duration e.g. `20m` or `30m`).
- **User Experience**: Tapping `"Set Goal"` in the notification shade opens the lightweight dialog overlay over the current app. The user selects "Power Nap", enters nap duration (`20m` / `0.5h`), and taps "OK". `SleepTimerService` calculates the relative nap alarm timestamp and schedules the alarm via `AlarmManager.setAlarmClock`.
- **Pros**:
  - Requires no additional notification action slots.
  - Reuses existing `GoalSettingsDialogActivity` overlay architecture.
  - Full support for `DurationUtils` flexible duration parsing (`20m`, `30m`, `0.5h`, `90m`).
- **Cons**:
  - Requires opening the overlay dialog instead of a 1-tap preset.

### Option B: Inline Reply Command Parsing (`Sleep 20m, Nap 30m`)
- **Description**: Allow the existing notification inline reply field (Slot 1) to parse command syntax like `nap 30m` or `nap 0.5h`.
- **User Experience**: User taps `"Sleep 20m"`, types `nap 30m`, and submits.
- **Pros**: Stays entirely inside the expanded notification shade.
- **Cons**: Requires users to remember text command syntax (`nap 30m`).

### Option C: Preset Quick Choices (`Nap 20m`, `Nap 30m`, `Nap 90m`)
- **Description**: Add predefined RemoteInput quick choice chips to the notification shade.
- **Pros**: 1-tap selection from notification shade.
- **Cons**: Limited to fixed predefined durations.

---

## 5. Architectural Recommendation

**Option A (Overlay Dialog Switch in `GoalSettingsDialogActivity`) is the recommended primary architecture:**
1. Update `GoalSettingsDialogActivity` layout with a segment toggle: `[ Overnight Goal | Power Nap ]`.
2. When "Power Nap" is selected:
   - Hide the `TimePicker`.
   - Display a single Nap Duration input field prefilled with `30m` (parsed via `DurationUtils.parseDurationMinutes(input, DefaultUnit.MINUTES)`).
3. On "OK", `SleepTimerService` calculates `targetAlarmMs = System.currentTimeMillis() + (sleepTimerMinutes * 60_000L) + (napMinutes * 60_000L)` and schedules the system clock alarm via `AlarmManager.setAlarmClock`.
