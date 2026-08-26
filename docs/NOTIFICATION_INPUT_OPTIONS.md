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

## 4. Flexible Duration String Parsing Rules

Since System UI cannot restrict `RemoteInput` to pure numeric keyboard entry on all keyboards, an effective software pattern is to support natural duration string parsing when receiving input.

### Recommended Specification for Parsing Input Strings

1. **Plain Numeric Input:**
   - Input containing only digits (e.g. `45`) is interpreted as minutes (e.g. `45m` -> 45 minutes).
2. **Hours Only (`Xh` / `XH`):**
   - Input specified in hours (e.g. `1h`, `2H`) is converted to minutes (`1h` -> 60 minutes).
3. **Combined Hours & Minutes (`XhYm`):**
   - Input containing both hours and minutes (e.g. `2h15m`, `1h30m`) is parsed into total minutes (`2h15m` -> 135 minutes).
4. **Seconds Ignored (`...s`):**
   - Input containing seconds specifiers (e.g. `2h10m5s` or `45m30s`) drops the seconds portion and parses valid higher units (`2h10m5s` -> 130 minutes; `45m30s` -> 45 minutes).
5. **Strict Fallback on Ambiguous or Malformed Input:**
   - If the input contains unrecognized formatting, duplicate unit patterns, or ambiguous structures (e.g. `10x10h4m`, `10m10`, `abc`, `10h20h`), parsing fails immediately. The system safely falls back to the previously configured duration or default (20m).

### Summary Table of String Parsing Behavior

| User Input String | Parsed Duration | Status / Action |
|---|---|---|
| `30` | 30 minutes | Valid (plain integer defaults to minutes) |
| `1h` | 60 minutes | Valid (`1 * 60m`) |
| `2h15m` | 135 minutes | Valid (`2 * 60m + 15m`) |
| `2h10m5s` | 130 minutes | Valid (`2 * 60m + 10m`, seconds `5s` dropped) |
| `15m30s` | 15 minutes | Valid (`15m`, seconds `30s` dropped) |
| `10x10h4m` | Fallback (e.g. 20m) | Invalid (malformed pattern) |
| `10m10` | Fallback (e.g. 20m) | Invalid (ambiguous trailing digits without unit) |
| `10h20h` | Fallback (e.g. 20m) | Invalid (duplicate hour specifiers) |

---

## 5. Recommendation

If enhancing notification duration entry in the future:
1. **Implement Flexible Duration String Parsing (Section 4)**: Accept natural inputs like `1h`, `2h15m`, while safely ignoring seconds (`2h10m5s`) and falling back on invalid inputs (`10m10`, `10x10h4m`).
2. **Combine Option A (`setChoices`) with current `RemoteInput`**: Provide 1-tap preset chips (`10`, `15`, `20`, `30`, `45`, `60`) in `RemoteInput.Builder.setChoices()`. This offers the cleanest in-shade experience without changing existing notification layout constraints.
3. **Combine with Option B (Quick Action Buttons)** if space permits: Add a `+5m` action button to quickly add time during active playback.
