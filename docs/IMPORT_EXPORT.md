# Import and Export Settings Feature Specification & Design

## Overview

This document provides the specification, data format design, user experience workflows, and technical architecture for the **Import and Export Settings** feature in Auto Sleep Droid ([Issue #61](https://github.com/bas080/auto-sleep-droid/issues/61)).

The Import and Export feature enables users to easily backup, restore, or transfer their application configuration (sleep timer duration, state, Smart Wake-Up Goal settings, and minimum sleep safeguard) across devices or app reinstallations using a simple string copied to or pasted from the Android system clipboard.

---

## 1. Requirements Summary

As specified in Issue #61:
- **Export**: Copies an application configuration string directly to the device clipboard.
- **Import**: Takes an application configuration string (via input dialog or clipboard paste) and updates application settings.
- **Data Format**: A standardized, structured string format.
- **UI Location**: Placed on `MainActivity` directly underneath the live event log UI.

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

The export string must be a valid JSON object adhering to the schema version `1`:

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

The Import/Export control section is located in `MainActivity` (`activity_main.xml`), placed horizontally directly below the event log `ScrollView`:

```text
+-------------------------------------------------------------+
| Auto Sleep Droid  v1.3.0                                    |
| [Releases]  [GitHub]  [Issues]  [Donate]                    |
+-------------------------------------------------------------+
| EVENT LOGS                                                  |
| 8/29 14:02:10 - MainActivity created                        |
| 8/29 14:02:11 - SleepTimerService initialized               |
| ...                                                         |
+-------------------------------------------------------------+
| [ Export Settings ]               [ Import Settings ]       |
+-------------------------------------------------------------+
```

### Layout Components
- **Container**: A horizontal `LinearLayout` placed underneath the `ScrollView` containing the log output.
- **Buttons**:
  - `btn_export`: "Export Settings" button.
  - `btn_import`: "Import Settings" button.

---

## 5. User Workflows & Experience

### Export Workflow

1. User opens `MainActivity` and taps **"Export Settings"**.
2. `MainActivity` reads current preferences from `SharedPreferences` (`sleep_timer` file).
3. `MainActivity` constructs the JSON payload according to Schema Version 1.
4. The JSON string is copied to the system clipboard using `ClipboardManager`.
5. A Toast message ("Settings copied to clipboard") confirms success.
6. An entry is added to `EventLogger` (`"Exported settings to clipboard"`).

### Import Workflow

1. User opens `MainActivity` and taps **"Import Settings"**.
2. A modal dialog (`AlertDialog`) appears featuring an input text field (`EditText`).
   - *Convenience Feature*: If valid JSON is detected on the clipboard, it is pre-filled into the text field automatically.
3. User pastes or verifies the JSON configuration string and taps **"Import"**.
4. The application validates the string:
   - Verifies valid JSON syntax.
   - Verifies the `"version"` field is supported.
   - Checks that numerical parameters fall within valid ranges (`duration_minutes` 1-1440, `wake_up_goal_hour` 0-23, `wake_up_goal_minute` 0-59, `min_sleep_duration_minutes` 1-1440).
5. **On Valid Import**:
   - Updates settings in `SharedPreferences`.
   - Sends an intent (`ACTION_REDRAW_NOTIFICATION`) to `SleepTimerService` to immediately apply new timer/goal settings and redraw the notification shade.
   - Displays a Toast message ("Settings imported successfully").
   - Logs the event to `EventLogger` (`"Imported settings from string"`).
6. **On Invalid Import**:
   - Existing settings in `SharedPreferences` remain completely unchanged.
   - Displays an error Toast message ("Invalid settings format").
   - Logs a warning to `EventLogger` (`"Failed to import settings: invalid format"`).

---

## 6. Technical Architecture & Data Synchronization

### Data Flow Diagram

```text
[ SharedPreferences ] <==== Read/Write ====> [ MainActivity ]
                                                    |
                                          (ClipboardManager)
                                                    |
                                                    v
                                         [ System Clipboard ]

[ MainActivity ] ---- Intent (ACTION_REDRAW_NOTIFICATION) ----> [ SleepTimerService ]
                                                                        |
                                                                        v
                                                           [ AlarmManager & Notification ]
```

### Key Android APIs
- `android.content.ClipboardManager` and `android.content.ClipData` for clipboard interaction.
- `org.json.JSONObject` for JSON parsing and serialization.
- `android.app.AlertDialog` for the import input dialog.
- `android.widget.Toast` for user feedback bottom bubbles.
- `SharedPreferences` for loading and persisting configuration values.

---

## 7. Summary

The Import and Export Settings feature provides a clean, dependency-free mechanism for users to back up, restore, or share their Auto Sleep Droid configuration via a standardized JSON string. Placed directly below the logs in `MainActivity`, it combines convenient zero-friction clipboard actions with safety validation.
