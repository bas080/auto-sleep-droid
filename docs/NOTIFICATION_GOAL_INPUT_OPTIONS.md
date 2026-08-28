# Options for Configuring Smart Wake-Up Goal from Notifications

## Overview

This document provides technical research, framework analysis, UX trade-offs, and architectural recommendations for bringing the Smart Wake-Up Goal ("Auto Sleep") configuration UI to the ongoing sleep timer notification shade in Auto Sleep Droid.

After evaluating available Android System UI mechanisms, **the Dialog Activity Overlay approach (`GoalSettingsDialogActivity`) is selected as the primary, recommended architecture.**

---

## 1. Background & Problem Context

### What is the Smart Wake-Up Goal?
Auto Sleep Droid includes a Smart Wake-Up Goal feature ("Auto Sleep") designed to automatically set a daily wake-up alarm while protecting the user's sleep duration:
- **Target Goal Time**: The user's desired daily wake-up clock time (e.g., `06:30 AM`).
- **Minimum Sleep Duration**: A minimum sleep safeguard duration in hours (default 7.5 hours / 450 minutes).
- **Dynamic Alarm Calculation**: When the sleep timer starts or is reset, `SleepTimerService` calculates the wake-up alarm timestamp as `Math.max(targetGoalTime, timerStartTime + sleepTimerDuration + minimumSleepDuration)` and schedules a non-recurring system alarm via `AlarmManager.setAlarmClock`.

### Current Goal UI Location
Currently, setting or clearing the wake-up goal requires opening the main app screen (`MainActivity`):
- **Set Goal**: Tapping "Set Goal" opens an `AlertDialog` containing an `EditText` for minimum sleep hours (`7.5`) and a native `TimePicker` widget.
- **Stop**: Tapping "Stop" disables the goal (`wake_up_goal_enabled = false`) and cancels scheduled alarms (disabled when goal is off).
- **Notification Visibility**: The expanded notification shade displays current alarm status (e.g. `Alarm set for 6:15 AM`), but offers no interactive controls to set or clear the goal from the shade.

### Objective
Provide direct, screen-free or notification-triggered access to configure and clear the wake-up goal without requiring the user to open `MainActivity`.

---

## 2. Android Framework & System UI Constraints

### 1. System UI Execution Context
Notifications are rendered outside the application process by **Android System UI** (`com.android.systemui`). Interactive UI elements inside notifications must strictly adhere to System UI capabilities and constraints.

### 2. Custom Layout (`RemoteViews`) Restrictions
Android OS explicitly prohibits interactive input controls within custom notification layouts (`RemoteViews`). Interactive components such as `TimePicker`, `DatePicker`, `EditText`, `SeekBar`, `Spinner`, or custom wheel views are **stripped or ignored** by `NotificationManager`.

### 3. Notification Action Slot Budget
Standard Android notification layouts render up to **3 visible action buttons** per notification:
- **Slot 1 (In Use)**: `"Set Timer"` (`RemoteInput` for sleep timer duration in minutes).
- **Slot 2 (In Use)**: `"Turn Off"` (when timer is enabled) or `"Turn On"` (when timer is disabled).
- **Slot 3 (Available)**: Currently unallocated across all notification states (`Off`, `Waiting`, `Active`, `Fading`), providing exactly 1 free action button slot for goal management.

### 4. Dialog Activity Overlay Capabilities (`PendingIntent.getActivity`)
- A notification action button can trigger a `PendingIntent.getActivity()` that opens a lightweight, dialog-themed Activity (`Theme.Material.Dialog` or `Theme.DeviceDefault.Dialog.Alert`) directly overlaid above whichever app or screen the user is currently viewing.
- The dialog renders native GUI controls (`TimePicker`, numeric input) with full input validation and instant dismissal upon completion.

---

## 3. Analysis of Technical Options

### Option A: Dialog Activity Overlay (`GoalSettingsDialogActivity`) — **RECOMMENDED**
- **Description**: Action Slot 3 displays a `"Set Goal"` (or `"Goal: 06:30"`) action button that fires a `PendingIntent.getActivity()`. This opens a lightweight, dialog-themed activity (`GoalSettingsDialogActivity`) directly over the foreground app.
- **User Experience**: Tapping `"Set Goal"` immediately presents a clean dialog containing a native `TimePicker` (clock wheel or digital time selector), a Minimum Sleep Duration input, and an "OK" / "Stop" button set. Tapping "OK" updates the goal and dismisses the dialog instantly, returning the user to their current app while updating the notification in the background.
- **Pros**:
  - **Native GUI Controls**: Full access to Android's built-in `TimePicker` and formatted inputs.
  - **Zero String Parsing Ambiguity**: 100% guaranteed input validation for hours, minutes, AM/PM, and minimum sleep safeguard.
  - **Multi-Parameter Support**: Naturally configures both Target Goal Time AND Minimum Sleep Duration in a single, clean interface.
  - **Seamless UX**: Opens directly over whichever app the user is using (e.g., YouTube, Spotify, podcast player) and vanishes immediately upon confirmation.
  - **Fits Action Slot 3**: Occupies the single remaining notification action slot.
- **Cons**:
  - Momentarily displays a window overlay over the current app instead of remaining purely within the expanded notification shade bounds.

### Option B: Inline `RemoteInput` with Text Parsing & Quick Choice Chips
- **Description**: Action Slot 3 opens an inline reply field where the user types a time string (e.g. `6:30`, `7am`, `22:00`, `clear`) or picks from preset quick chips (`["6:00 AM", "6:30 AM", "7:00 AM", "Clear"]`).
- **Pros**: Stays entirely inside the expanded notification shade.
- **Why It Is Secondary/Inadequate**:
  - **Complex Freeform String Entry**: Typing clock times (`6:30` vs `0630` vs `6:30 PM`) on a soft keyboard requires complex string parsing and error-prone keyboard interactions.
  - **Inability to Configure Minimum Sleep Safeguard**: Inline text replies cannot cleanly capture both a target wake-up time AND a minimum sleep safeguard duration without requiring cumbersome syntax (e.g. `6:30 8h`).
  - **Limited Chip Presets**: Preset chips only work for fixed times and cannot cover custom wake-up schedules.

### Option C: Quick Action Toggle / Preset Cycle Button
- **Description**: Tapping Action Slot 3 toggles the goal ON/OFF or cycles through pre-saved goal times (`6:00 AM` -> `6:30 AM` -> `7:00 AM` -> `Off`).
- **Why It Is Secondary/Inadequate**: Extremely limited; cannot set arbitrary custom wake-up times or adjust minimum sleep safeguards.

### Option D: Custom Notification Layout (`RemoteViews`) — **NOT FEASIBLE**
- **Feasibility**: Prohibited by Android System UI (interactive widgets like `TimePicker` are not supported in `RemoteViews`).

---

## 4. Comparison Summary Matrix

| Option | Native GUI `TimePicker`? | Strict Input Validation? | Sets Time & Min Sleep? | UX Quality | Recommendation Status |
|---|---|---|---|---|---|
| **Option A (Dialog Activity Overlay)** | Yes (`TimePicker`) | Yes (100% Guaranteed) | Yes (Both fields) | Excellent (Visual picker over app) | **PRIMARY / PREFERRED** |
| **Option B (Inline `RemoteInput` + Chips)** | No (Text field) | No (Requires parser) | Partial (Time only) | Moderate (Typing required) | Deprecated / Secondary |
| **Option C (Toggle / Cycle Action Button)** | No | Yes (Presets) | No (Uses saved min sleep) | Fair (Presets only) | Secondary |
| **Option D (Custom `RemoteViews` Layout)** | N/A | N/A | N/A | N/A | Unsupported by Android |

---

## 5. Detailed Architectural Specification for the Dialog Approach

### 1. Notification Action Integration
In `SleepTimerService.buildNotification()`, allocate **Action Slot 3** for Goal Alarm management across all service states (`Off`, `Waiting`, `Active`, `Fading`):

- **Action Title**:
  - When Goal is Disabled: `"Set Goal"`
  - When Goal is Enabled: `"Goal: 06:30 AM"` (displays formatted goal time)
- **PendingIntent**:
  - `PendingIntent.getActivity(...)` targeting `GoalSettingsDialogActivity`.

### 2. Dialog Activity Component (`GoalSettingsDialogActivity`)
- **Theme**: `android:theme="@android:style/Theme.Material.Dialog"` (or `Theme.DeviceDefault.Dialog.Alert`) for a compact, clean modal overlay.
- **Layout Controls**:
  1. **Minimum Sleep Duration Field**: An `EditText` (decimal input) prefilled with the currently configured minimum sleep duration in hours (default `7.5`).
  2. **Target Goal `TimePicker`**: Native `TimePicker` widget prefilled with saved goal hour and minute (default `06:30 AM`).
  3. **Action Buttons**:
     - **"OK"**: Saves goal time and minimum sleep duration, enables goal (`wake_up_goal_enabled = true`), triggers `checkAndScheduleSmartWakeUpAlarm()`, redrawn notification, and calls `finish()`.
     - **"Stop"**: Disables goal (`wake_up_goal_enabled = false`), cancels scheduled alarms, redrawn notification, and calls `finish()` (disabled when goal is off).

### 3. Data & State Flow
1. User taps `"Set Goal"` or `"Goal: 06:30 AM"` in the notification shade.
2. System UI fires the `PendingIntent` and opens `GoalSettingsDialogActivity` as a lightweight overlay over the foreground app.
3. User selects target wake-up time on the `TimePicker` wheel and taps "Save".
4. `GoalSettingsDialogActivity` writes values to `SharedPreferences` (`wake_up_goal_enabled`, `wake_up_goal_hour`, `wake_up_goal_minute`, `min_sleep_duration_minutes`).
5. `GoalSettingsDialogActivity` sends an intent to `SleepTimerService` (`ACTION_REDRAW_NOTIFICATION` / `ACTION_UPDATE_GOAL`) to recalculate the `"Auto Sleep"` system alarm and redraw the notification shade immediately.
6. `GoalSettingsDialogActivity` calls `finish()` and closes, returning the user instantly to their active app.

---

## 6. Conclusion & Summary

The **Dialog Activity Overlay approach** is the primary, recommended strategy for configuring the Smart Wake-Up Goal from the notification shade:
- It leverages Android's native `TimePicker` and dialog themes to deliver an intuitive, error-free experience.
- It seamlessly configures both goal parameters (Target Wake-Up Time and Minimum Sleep Safeguard).
- It fits perfectly into the notification shade's 3rd action slot while keeping the interaction fast and lightweight.
