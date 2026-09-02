# Import and Export Settings Feature Specification & Design

## Overview

This document provides the specification, data format design, user experience workflows, and technical architecture for the **Import and Export Settings** feature in Auto Sleep Droid ([Issue #61](https://github.com/bas080/auto-sleep-droid/issues/61)).

The Import and Export feature enables users to easily backup, restore, or transfer their application configuration (sleep timer duration, state, Smart Wake-Up Goal settings, and minimum sleep safeguard) across devices or app reinstallations using a simple JSON string shared via Android's native system share sheet or pasted into an import dialog.

---

## 1. Requirements Summary

As specified in Issue #61 and user design updates:
- **Export**: Serializes configuration into a JSON string and launches a system share action (`Intent.ACTION_SEND`) allowing the user to copy or send settings.
- **Import**: Presents an instructional dialog prompting the user to paste or enter an application configuration string and updates application settings.
- **Data Format**: A standardized, structured JSON string format.
- **UI Location**: Placed on `MainActivity` rendered in the scrollable list of action links at the bottom of the form under a "Links" header alongside Manual, Logs, Feedback, and Donate.

---

## 2. Analysis of Data Format Options

Several serialization formats were evaluated for importing and exporting Auto Sleep Droid configuration settings:

| Format Option | Human Readable? | Extensible? | Validation Safety | Format Example | Recommendation |
|---|---|---|---|---|---|
| **Option A: Plain JSON with Schema Versioning** | Yes | Excellent | High (native `JSONObject` validation) | `{"version":1,"duration_minutes":20,"wake_up_goal_enabled":true,"wake_up_goal_hour":6,"wake_up_goal_minute":30,"min_sleep_duration_minutes":450}` | **RECOMMENDED** |
| **Option B: Base64-Encoded JSON** | No | Excellent | High (requires decode step) | `eyJ2ZXJzaW9uIjoxLCJkdXJhdGlvbl9taW51dGVzIjoyMC...` | Secondary |
| **Option C: Key-Value / URL Query String** | Partial | Fair | Moderate (custom parser required) | `version=1&duration=20&goal_enabled=true&goal_hour=6&goal_minute=30&min_sleep=450` | Deprecated |

### Why Plain JSON with Schema Versioning is Selected
1. **Transparency & Editability**: Plain JSON allows advanced users to inspect and manually edit configuration values directly in clipboard or text editors before importing.
2. **Standardization**: Android provides native, robust JSON parsing (`org.json.JSONObject`) without requiring external dependencies or custom parser code.
3. **Future Compatibility**: Including a `"version"` key ensures backward and forward compatibility as new features or preferences are added in future releases.
4. **Compact Payload**: A full export string is lightweight (~130 characters), easily fitting clipboard buffers and messaging apps.

---

## 3. Data Schema Specification (Version 1)

### JSON Schema

The export string must be a valid JSON object adhering to schema version `1`:

```json
{
  "version": 1,
  "duration_minutes": 20,
  "active": false,
  "wake_up_goal_enabled": true,
  "wake_up_goal_hour": 6,
  "wake_up_goal_minute": 30,
  "min_sleep_duration_minutes": 450
}
```

### Schema Field Specification

| Field Name | Type | Allowed Range / Values | Required | Description |
|---|---|---|---|---|
| `version` | Integer | `1` | Yes | Schema version identifier for forward compatibility. |
| `duration_minutes` | Integer | `1` to `1440` (minutes) | Yes | Configured sleep timer duration (default 20 minutes, max 24 hours). |
| `active` | Boolean | `true`, `false` | No | Whether the timer is currently enabled (defaults to `false` if missing). |
| `wake_up_goal_enabled` | Boolean | `true`, `false` | Yes | Whether the Smart Wake-Up Goal feature is enabled. |
| `wake_up_goal_hour` | Integer | `0` to `23` (hours) | Yes | Target wake-up goal clock hour (24-hour format). |
| `wake_up_goal_minute` | Integer | `0` to `59` (minutes) | Yes | Target wake-up goal clock minute. |
| `min_sleep_duration_minutes` | Integer | `1` to `1440` (minutes) | Yes | Minimum sleep safeguard duration in minutes (default 450 = 7.5 hours). |

---

## 4. User Interface & Layout Design

### Placement on `MainActivity`

The Import/Export control section is located on `MainActivity` (`activity_main.xml`), integrated into the vertical link list at the bottom of the form under a "Links" header alongside existing action links (`Manual`, `Logs`, `Feedback`, `Donate`).

### Layout Components
- **Link Section Integration**: Borderless button action links (`btn_export` for "Export" and `btn_import` for "Import") added to the vertical link list inside `activity_main.xml`.

---

## 5. User Workflows & Experience

### Export Workflow (System Share Action)

1. User opens `MainActivity` and taps **"Export"** (or **"Export Settings"**).
2. `MainActivity` reads current preferences from `SharedPreferences` (`sleep_timer` file).
3. `MainActivity` constructs the JSON payload according to Schema Version 1.
4. `MainActivity` launches Android's native system share sheet via `Intent.ACTION_SEND` (`Intent.createChooser` with MIME type `text/plain` and `Intent.EXTRA_TEXT` set to the JSON string).
5. The system share sheet allows the user to copy the configuration string to clipboard, send via messaging/email apps, or save to notes.
6. An entry is added to `EventLogger` (`"Exported settings via system share sheet"`).

### Import Workflow with Instructional Dialog

1. User opens `MainActivity` and taps **"Import"** (or **"Import Settings"**).
2. An instructional modal dialog (`AlertDialog`) titled **"Import Settings"** appears containing:
   - **Instructional Message**: *"Paste your settings JSON configuration string below to import app preferences:"*
   - **Text Input Area**: An `EditText` field for pasting or entering the JSON string.
   - **Auto-Paste Convenience**: If valid JSON is detected on the device clipboard, it is automatically pre-filled into the text input area.
3. User pastes or verifies the JSON configuration string and taps **"Import"**.
4. The application validates the string:
   - Verifies valid JSON syntax.
   - Verifies the `"version"` field is supported (`1`).
   - Checks that numerical parameters fall within valid ranges (`duration_minutes` 1-1440, `wake_up_goal_hour` 0-23, `wake_up_goal_minute` 0-59, `min_sleep_duration_minutes` 1-1440).
5. **On Valid Import**:
   - Updates settings in `SharedPreferences`.
   - Sends an intent (`ACTION_REDRAW_NOTIFICATION`) to `SleepTimerService` to immediately apply new timer/goal settings and redraw the notification shade.
   - Displays a Toast confirmation message ("Settings imported successfully").
   - Logs the event to `EventLogger` (`"Imported settings from string"`).
6. **On Invalid Import**:
   - Existing settings in `SharedPreferences` remain completely unchanged.
   - Displays an error Toast message ("Invalid settings format").
   - Logs a warning to `EventLogger` (`"Failed to import settings: invalid format"`).

---

## 6. Technical Architecture & Data Synchronization

### Key Android APIs
- `android.content.Intent.ACTION_SEND` and `Intent.createChooser` for native share sheet export.
- `android.content.ClipboardManager` and `android.content.ClipData` for import clipboard detection.
- `org.json.JSONObject` for JSON parsing and serialization.
- `android.app.AlertDialog` for the instructional import dialog.
- `android.widget.Toast` for user feedback bubbles.
- `SharedPreferences` for loading and persisting configuration values.

---

## 7. Summary

The Import and Export Settings feature provides a clean, dependency-free mechanism for users to back up, restore, or share their Auto Sleep Droid configuration via a standardized JSON string. Integrated into `MainActivity` with system share action (`ACTION_SEND`) upon export and an instructional dialog upon import, it combines convenient zero-friction sharing with safety validation.
