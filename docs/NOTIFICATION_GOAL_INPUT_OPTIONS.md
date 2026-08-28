# Options for Configuring Smart Wake-Up Goal from Notifications

## Overview

This document provides technical research, framework analysis, UX trade-offs, and architectural options for bringing the Smart Wake-Up Goal ("Auto Sleep") configuration UI directly into or triggering from the ongoing sleep timer notification shade in Auto Sleep Droid.

---

## 1. Background & Problem Context

### What is the Smart Wake-Up Goal?
Auto Sleep Droid includes a Smart Wake-Up Goal feature ("Auto Sleep") designed to automatically set a daily wake-up alarm while protecting the user's sleep duration:
- **Target Goal Time**: The user's desired daily wake-up clock time (e.g., `06:30 AM`).
- **Minimum Sleep Duration**: A minimum sleep safeguard duration in hours (default 7.5 hours / 450 minutes).
- **Dynamic Alarm Calculation**: When the sleep timer starts or is reset, `SleepTimerService` calculates the wake-up alarm timestamp as `Math.max(targetGoalTime, timerStartTime + sleepTimerDuration + minimumSleepDuration)` and schedules a non-recurring system alarm via `AlarmManager.setAlarmClock`.

### Current Goal UI Location
Currently, setting or clearing the wake-up goal requires opening the main app screen (`MainActivity`):
- **Set Goal**: Tapping "Set Wake-Up Goal" opens an `AlertDialog` containing an `EditText` for minimum sleep hours (`7.5`) and a native `TimePicker` widget.
- **Clear Goal**: Tapping "Clear Goal" disables the goal (`wake_up_goal_enabled = false`) and cancels scheduled alarms.
- **Notification Visibility**: The expanded notification shade displays current alarm status (e.g. `Alarm set for 6:15 AM`), but offers no interactive controls to set or clear the goal from the shade.

### Objective
Provide screen-free or notification-first access to configure and clear the wake-up goal directly from the notification shade without requiring the user to open `MainActivity`.

---

## 2. Android Framework & System UI Constraints

### 1. System UI Execution Context
Notifications are rendered outside the application process by **Android System UI** (`com.android.systemui`). Interactive UI elements inside notifications must strictly adhere to System UI APIs.

### 2. Custom Layout (`RemoteViews`) Restrictions
Android OS explicitly prohibits interactive input controls within custom notification layouts (`RemoteViews`). Interactive components such as `TimePicker`, `DatePicker`, `EditText`, `SeekBar`, `Spinner`, or custom wheel views are **stripped or ignored** by `NotificationManager`.

### 3. Notification Action Slot Budget
Standard Android notification layouts render up to **3 visible action buttons** per notification:
- **Slot 1 (In Use)**: `"Set Timer"` (`RemoteInput` for sleep timer duration in minutes).
- **Slot 2 (In Use)**: `"Turn Off"` (when timer is enabled) or `"Turn On"` (when timer is disabled).
- **Slot 3 (Available)**: Currently unallocated in all notification states (`Off`, `Waiting`, `Active`, `Fading`), providing exactly 1 free action button slot for goal management.

### 4. Inline Reply Capabilities (`RemoteInput`)
- `RemoteInput` allows users to type text or pick preset chips directly inside the notification shade.
- On API 28+, `RemoteInput.Builder.setChoices(...)` displays quick-reply chips below the notification text field.
- System UI delivers input text asynchronously to `SleepTimerService` via a `PendingIntent`.

### 5. Dialog Activity Overlay Capabilities (`PendingIntent.getActivity`)
- A notification action button can trigger a `PendingIntent.getActivity()` that opens a lightweight, dialog-themed Activity (`Theme.Material.Dialog`) directly overlaid above whichever app or screen the user is currently viewing.

---

## 3. Evaluation of Architectural Options

### Option A: Dedicated "Set Goal" Action with Inline `RemoteInput` & Quick Chips
- **Description**: Occupy Action Slot 3 with a `"Set Goal"` action button that opens an inline `RemoteInput` text field with predefined quick choice chips (e.g., `["6:00 AM", "6:30 AM", "7:00 AM", "7:30 AM", "Clear Goal"]`) via `setChoices()`.
- **User Experience**: Tapping `"Set Goal"` expands inline choices directly under the notification shade. The user can tap a single chip to set a goal time instantly, or type a custom time (e.g. `6:30`, `7am`, `22:00`, `clear`).
- **Pros**:
  - 100% in-shade experience without leaving the notification shade or opening window overlays.
  - Quick-reply chips provide 1-tap goal selection for popular wake-up times.
  - Fully utilizes the 3rd available notification action slot across all service states.
- **Cons**:
  - Requires parsing time strings on the backend (`SleepTimerService`).
  - Configuring minimum sleep safeguard duration alongside goal time in a single text field requires a structured string format (e.g., `6:30 8h`) or defaulting minimum sleep duration to its previously saved value.

### Option B: Dialog Activity Overlay (`Theme.Material.Dialog`) Launched from Notification Action
- **Description**: The `"Set Goal"` notification action launches a lightweight, transparent/dialog-themed Activity (`GoalSettingsDialogActivity`) over the foreground screen, displaying native `TimePicker` and Minimum Sleep `EditText` controls.
- **User Experience**: Tapping `"Set Goal"` immediately presents a centered, dark/light dialog overlay. The user selects the time on the `TimePicker` wheel, adjusts minimum sleep duration if needed, and taps "OK". The dialog vanishes instantly, returning focus to the previous app while updating the notification.
- **Pros**:
  - Full native Android GUI controls (`TimePicker` wheel/digital clock, numeric `EditText`).
  - Guaranteed 100% input validation (no freeform text parsing needed).
  - Allows precise configuration of both Goal Time AND Minimum Sleep Duration simultaneously.
- **Cons**:
  - Briefly pops up a visual overlay window over the foreground app rather than remaining strictly inside the notification shade.

### Option C: Quick Action Toggle / Preset Cycle Button
- **Description**: Action Slot 3 displays current goal status (e.g., `"Goal: 06:30"` or `"Set Goal"`). Tapping the action toggles Goal ON/OFF, or cycles through preset goal times (e.g. `06:00` -> `06:30` -> `07:00` -> `Off`).
- **User Experience**: Single-tap interaction to enable/disable or step through common goal times.
- **Pros**:
  - Extremely fast 1-tap interaction.
  - 100% guaranteed valid values (no text parsing or dialogs).
- **Cons**:
  - Inflexible if the user wants to set a custom wake-up time not included in the preset cycle.

### Option D: Unified "Set Timer / Goal" Inline `RemoteInput` Action
- **Description**: Keep only two notification actions (`"Set Timer"` and `"Turn Off"`/`"Turn On"`), and update the `"Set Timer"` inline reply to support both duration and goal input using prefix syntax (e.g. typing `20` sets 20-minute sleep timer; typing `@6:30` or `goal 6:30` sets wake-up goal time; typing `goal off` clears goal).
- **User Experience**: Power users enter commands into a single inline reply field.
- **Pros**:
  - Preserves Action Slot 3 for future extensions.
- **Cons**:
  - Less intuitive for casual users; requires discovering or remembering prefix syntax.

### Option E: Custom Notification Layout (`RemoteViews`) - Feasibility Analysis
- **Feasibility**: **Not Feasible.**
- **Reasoning**: As established in Section 2, Android System UI prohibits interactive widgets like `TimePicker` or `EditText` in custom `RemoteViews` notification layouts.

---

## 4. Comparison Summary Matrix

| Option | In-Shade Native? | Strict Validation? | Sets Time & Min Sleep? | UX Quality | Complexity |
|---|---|---|---|---|---|
| **Option A (Inline `RemoteInput` + Chips)** | Yes | Backend String Parser | Time (Min Sleep uses saved default) | Excellent (1-tap chips or text) | Low-Medium |
| **Option B (Dialog Activity Overlay)** | No (Dialog overlay) | Yes (Native `TimePicker`) | Yes (Both parameters) | Excellent (Visual GUI picker) | Low-Medium |
| **Option C (Toggle / Cycle Action Button)** | Yes | Yes (Presets) | Saved Min Sleep | Good (1-tap toggle) | Low |
| **Option D (Unified `RemoteInput` Prefix)** | Yes | Backend String Parser | Time (Min Sleep uses saved default) | Moderate (Requires syntax) | Medium |
| **Option E (Custom `RemoteViews` Layout)** | N/A | Impossible | N/A | N/A | Unsupported |

---

## 5. Specification for Goal Inline Text Parsing (Option A)

When using inline reply (`RemoteInput`) for goal setting, `SleepTimerService` must parse input strings flexibly while safely handling edge cases.

### Supported Input Formats

1. **12-Hour Time Format (`H:MM am/pm` or `H:MM`):**
   - `6:30` -> 06:30 AM
   - `6:30am` or `6:30 am` -> 06:30 AM
   - `11:15pm` or `11:15 pm` -> 11:15 PM
   - `7am` or `7 PM` -> 07:00 AM / 07:00 PM
2. **24-Hour Time Format (`HH:MM` or `HHMM`):**
   - `06:30` -> 06:30 AM
   - `22:00` -> 10:00 PM
   - `0700` -> 07:00 AM
3. **Combined Goal Time & Minimum Sleep Safeguard:**
   - `6:30 8h` -> Goal Time `06:30 AM`, Minimum Sleep `8.0 hours`
   - `7:00am 7.5h` -> Goal Time `07:00 AM`, Minimum Sleep `7.5 hours`
   - `06:30 450m` -> Goal Time `06:30 AM`, Minimum Sleep `450 minutes` (7.5h)
4. **Clear / Disable Commands:**
   - `clear`, `off`, `disable`, `none`, `cancel`, `0` -> Disables goal alarm (`wake_up_goal_enabled = false`).

### Summary Table of Parsing Behavior

| User Input String | Target Goal Time | Min Sleep Duration | Goal Enabled? | Status / Action |
|---|---|---|---|---|
| `6:30` | 06:30 AM | Saved value (7.5h) | True | Valid (12-hour AM default) |
| `6:30am` | 06:30 AM | Saved value (7.5h) | True | Valid |
| `11:00pm` | 11:00 PM | Saved value (7.5h) | True | Valid |
| `22:30` | 10:30 PM | Saved value (7.5h) | True | Valid (24-hour format) |
| `0700` | 07:00 AM | Saved value (7.5h) | True | Valid (Compact 4-digit) |
| `6:30 8h` | 06:30 AM | 8.0 hours (480m) | True | Valid (Time + Min Sleep) |
| `7:00am 7.5h` | 07:00 AM | 7.5 hours (450m) | True | Valid (Time + Min Sleep) |
| `clear` / `off` | Unchanged | Unchanged | False | Goal disabled & alarm cancelled |
| `invalid_text` | Unchanged | Unchanged | Unchanged | Safe fallback; keeps current goal |

---

## 6. Recommended Architectural Strategy

### Recommended Hybrid Implementation
The optimal solution combines **Option A (Inline `RemoteInput` with Quick Chips)** for in-shade convenience with **Option B (Dialog Activity Overlay)** for rich visual time picking:

1. **Action Slot 3 in Notification (`"Set Goal"`)**:
   - Add `"Set Goal"` action button across all service states (`Off`, `Waiting`, `Active`, `Fading`).
   - Attach `RemoteInput` with predefined quick-reply chips: `["6:00 AM", "6:30 AM", "7:00 AM", "7:30 AM", "Clear"]`.
2. **Inline Fast Path**:
   - Tapping a preset chip (or typing `6:30`) instantly sets the goal time in the notification shade without opening any app window.
3. **Rich Dialog Overlay Path**:
   - Long-pressing or tapping a dedicated dialog trigger (or including a `"More..."` choice chip) launches a lightweight `GoalSettingsDialogActivity` featuring native `TimePicker` and Minimum Sleep duration inputs for users who prefer a visual wheel picker.
4. **State Persistence & Alarm Scheduling**:
   - Upon receiving valid goal input, `SleepTimerService` updates `SharedPreferences` (`wake_up_goal_enabled`, `wake_up_goal_hour`, `wake_up_goal_minute`, `min_sleep_duration_minutes`).
   - The service recalculates and schedules/updates the `"Auto Sleep"` system alarm via `checkAndScheduleSmartWakeUpAlarm()`.
   - The notification is redrawn immediately to reflect the updated goal and tonight's alarm schedule.
