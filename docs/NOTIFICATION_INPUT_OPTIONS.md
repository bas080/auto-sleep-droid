# Notification Duration Input Options in Android

## Overview

This document analyzes options for improving sleep timer duration entry from the Android notification shade. Specifically, it addresses whether strict integer/numeric input validation can be enforced within the notification shade itself, details Android framework constraints, and lays out potential architectural options.

---

## 1. Technical Analysis: Is Strict Integer Input Possible in Notifications?

### The Core Constraint
In Android, the notification shade is rendered and managed by **System UI** (`com.android.systemui`), which runs in a separate system process outside the application.

When an app adds inline reply functionality via `RemoteInput`:
1. The app defines `RemoteInput` and attaches it to a `Notification.Action`.
2. System UI renders the inline text field (`EditText`) and manages soft keyboard interactions.
3. The app receives the user's input string only *after* the user submits the reply.

### Input Type Hints (`InputType.TYPE_CLASS_NUMBER`)
- The app can pass input type hints via `RemoteInput.getExtras().putInt("android.intent.extra.inputType", InputType.TYPE_CLASS_NUMBER)`.
- **Behavior:** This requests soft keyboards (such as Gboard) to open in numeric mode by default when the user taps the inline reply field.
- **Limitation:** System UI does not filter or sanitize characters in real time for `RemoteInput`. Users can still switch keyboard pages, paste text, or use custom keyboards that allow non-numeric characters.
- **Conclusion:** **Strict client-side restriction (disabling non-numeric input before submission) is not possible in standard notification inline replies.** Input validation and fallback logic must always occur in the application process upon receiving the intent payload.

---

## 2. Layout of Available Options

### Option A: `RemoteInput` with Predefined Quick Chips (`setChoices`)
- **Description:** Keep the inline `RemoteInput` text field (with `InputType.TYPE_CLASS_NUMBER` hint) and add predefined duration choices using `RemoteInput.Builder.setChoices(...)` (e.g. `["5", "10", "15", "20", "30", "45", "60"]`).
- **User Experience:** On modern Android versions (API 28+), System UI displays these choices as quick-reply chips directly under the notification. The user can tap a single chip to set a preset duration instantly without typing, or tap the text field to enter a custom duration.
- **Pros:**
  - Standard Android API with zero custom layout overhead.
  - Eliminates typing for common timer durations (5m, 15m, 30m, 60m).
  - Maintains fallback validation for custom text input.
- **Cons:**
  - Does not restrict manual typing of non-numeric text if the user opens the keyboard.

### Option B: Quick Increment/Decrement Notification Actions
- **Description:** Add notification action buttons for quick duration adjustments, such as `+5m` and `+15m` (or `-5m` / `+10m`).
- **User Experience:** Single-tap actions in the notification shade to adjust the configured timer duration up or down.
- **Pros:**
  - 100% guaranteed numeric operation (no freeform text input involved).
  - Extremely convenient for quick adjustments before or during playback.
- **Cons:**
  - Notification action limit (Android supports up to 3 action buttons per notification standard layout; inline reply currently uses 1 slot).

### Option C: Dialog Activity / NumberPicker Trigger from Notification
- **Description:** Replace or supplement the inline reply action with a Notification Action that launches a lightweight Dialog Activity (`Theme.Material.Dialog`) containing a native Android `NumberPicker` or numeric-only `EditText`.
- **User Experience:** Tapping "Set Timer" opens a clean, centered dialog overlay with a scrollable wheel or numeric input, allowing exact integer selection before dismissing back to the previous screen.
- **Pros:**
  - Strict input validation and native `NumberPicker` UX (impossible to enter invalid characters).
  - Full control over UI styling and min/max constraints (1 to 1440 minutes).
- **Cons:**
  - Briefly opens a visual dialog over the current app instead of staying strictly inside the notification shade.

### Option D: Custom `RemoteViews` Notification Layout
- **Description:** Attempting to build a custom notification layout with custom input widgets using `RemoteViews`.
- **Feasibility Note:** **Not Feasible for Input.** Android explicitly restricts interactive UI components in `RemoteViews`. Only basic passive views (`TextView`, `ImageView`, `Button`, `ImageButton`, `ProgressBar`) are supported. Interactive input controls like `EditText`, `NumberPicker`, `Spinner`, or `SeekBar` are prohibited and ignored by `NotificationManager`.

---

## 3. Comparison Summary

| Option | In-Shade Native? | Strict Integer Guarantee? | UX Quality | Complexity |
|---|---|---|---|---|
| **Current (`RemoteInput` + `TYPE_CLASS_NUMBER`)** | Yes | No (requires backend fallback) | Good (opens numeric keypad) | Low (implemented) |
| **Option A (`RemoteInput` + `setChoices`)** | Yes | Partial (chips are exact, text input has fallback) | Excellent (1-tap chips + numeric keypad) | Low |
| **Option B (Increment Actions `+5m` / `+15m`)** | Yes | Yes (100% numeric) | Very Good (1-tap adjustment) | Low |
| **Option C (Dialog `NumberPicker`)** | No (Dialog overlay) | Yes (100% numeric) | Excellent (scroll wheel / validated dialog) | Medium |
| **Option D (Custom `RemoteViews` `NumberPicker`)** | N/A | Impossible (Unsupported by Android OS) | N/A | Unsupported |

---

## 4. Recommendation

If enhancing notification duration entry in the future:
1. **Combine Option A (`setChoices`) with current `RemoteInput`**: Provide 1-tap preset chips (`10`, `15`, `20`, `30`, `45`, `60`) in `RemoteInput.Builder.setChoices()`. This offers the cleanest in-shade experience without changing existing notification layout constraints.
2. **Combine with Option B (Quick Action Buttons)** if space permits: Add a `+5m` action button to quickly add time during active playback.
