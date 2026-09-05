# Auto Sleep Droid Specification

## Terminology
- **Sleep Timer**: The application feature that counts down while media is playing and fades volume down to zero to pause playback upon expiration.
- **Sleep Timer Duration**: The user-configured duration in minutes (default 20 minutes, min 1 minute, max 24 hours) that the sleep timer counts down before fading and pausing media.
- **Fade-Out / Fading**: The 30-second volume fade at sleep timer expiration where music volume gradually decreases along an ease-out curve down to zero before media playback is paused.
- **Wake-Up Alarm ("Auto Sleep")**: The background wake-up alarm scheduled via AlarmManager by Auto Sleep Droid that plays the system default alarm tone and updates the ongoing status notification to display the wake-up alarm status. Users can snooze the alarm with a phone flip gesture or dismiss it with a hardware volume button click.
- **Target Goal Time**: The user's desired daily wake-up clock time (e.g., `06:30 AM`).
- **Minimum Sleep Duration**: The user-configured minimum sleep safeguard duration in hours (default 7.5 hours) ensuring that the wake-up alarm is set no earlier than `timerStartTime + sleepTimerDuration + minimumSleepDuration`.

## Product goal
Provide an Android sleep timer app configured directly from a single main UI screen with full-screen Manual and Event Logs views, simplified notification shade actions, and zero intrusive UI dialogs.

## System states
- Off: The timer is manually disabled. Media continues playing normally, and the current volume remains entirely unchanged.
- Waiting: A duration is configured and auto-sleep is turned on. The app sits passively listening for active media playback via playback state listeners.
- Active: Triggered by media playback, the timer actively counts down from the configured duration towards expiration. Pausing media while active does not pause or send the timer back to Waiting; the active countdown continues towards expiration and can be reset to the configured duration via volume changes, flip gestures, or duration updates.
- Fading: The timer reaches zero, initiating a 30-second volume fade along a curve that starts steep and flattens out. Completing this fade pauses media, restores pre-fade volume, and returns the app back to the Waiting state.

## Notification states and content
All notification content is concise and directly visible in the notification body without hiding text in expanded views:

- Off: "Timer off (20m) • Wake at 6:15 AM" (Alarm detail shown when wake-up goal is enabled) • Button: "Enable"
- Waiting: "Waiting for playback (20m) • Wake at 6:15 AM" (Alarm detail shown when wake-up goal is enabled) • Button: "Disable"
- Active: "Fades out at 11:15 PM (20m) • Wake at 6:15 AM" (Alarm detail shown when wake-up goal is enabled) • Button: "Disable"
- Fading: "Fading volume" • Button: "Disable"
- Wake-up Alarm Ringing: "Flip to snooze • Press volume button to dismiss" • Buttons: "Dismiss", "Snooze"
- Wake-up Alarm Snoozed: "Snoozed 9m • Press volume button to dismiss" • Button: "Dismiss"

Only the action button lives in the expanded shade. All information text is directly visible in the main notification view.
If the "Show notification" setting is disabled by the user (disabled by default), the ongoing sleep timer notification is hidden in all timer states (Off, Waiting, Active, Fading).
Toggling "Show notification" to ON prompts the user for notification permission if not already granted.

## User interface
- Main Application Screen (`MainActivity`):
  - Provides a complete single-screen configuration UI for all settings:
    - Nap alarm section at top featuring a Nap button ("Nap" or "Cancel Nap" when active).
    - Sleep timer enable/disable switch.
    - Sleep timer duration input using hour and minute wheel pickers with unit labels ("hours" and "mins"). Timer duration controls remain enabled when the sleep timer switch is OFF.
    - Auto sleep timer (DND) enable/disable switch (optional automation; manual toggle always available).
    - Wake-up alarm enable switch ("Wake-up alarm"), target wake-up time picker button, and minimum sleep duration input.
    - Section headings (Nap, Timer, Alarm, About) remain fully visible and opaque at all times.
  - Action links at the bottom of the form under a "Links" header: Manual, Logs, Feedback, Donate, Export, and Import arranged in a FlowLayout inline wrapping layout separated by middle dots.
  - Full-screen non-dialog overlay views for Manual and Event Logs featuring a Back button pinned to the bottom right corner.
- Notification Shade Controls:
  - The notification features toggle actions ("Disable" when enabled, or "Enable" when disabled) and a "Nap" / "Cancel Nap" action button.
  - Tapping "Nap" when no nap is active opens a duration dialog prefilled with the previously used nap duration. Starting a nap schedules a nap wake alarm. Tapping "Cancel Nap" while a nap is active cancels the nap alarm.
  - Tapping/clicking the notification body opens `MainActivity`.

## Timer configuration
- The user can turn the sleep timer on or off and configure all options from the main screen UI or toggle state from the notification.
- The duration is entered in minutes, supporting natural duration input strings (e.g., plain integers default to minutes like `30`, hours `1h`, hours and minutes `2h15m`, while seconds specifiers like `2h10m5s` or `15m30s` ignore seconds).
- Minimum duration: 1 minute.
- Maximum duration: 24 hours.
- Default duration: 20 minutes when the user has not configured a duration.
- Store the original configured duration while the timer is active.
- **Invalid inputs:** If the user enters an invalid or malformed duration string (e.g. `10x10h4m`, `10m10`, `abc`, or out of range values), fall back safely to the already configured time or the default duration.
- Use a playback listener API while in the Waiting state to detect when audio playback starts automatically.
- When in the Waiting state, communicate that the timer is waiting for playback rather than stopped.
- Show the configured duration in waiting, active, and fade states.

## Timer behavior
- When enabled, count down from the configured duration while media is playing.
- Detect phone flip gestures and volume changes ONLY during Active (media playback) and Fading states to reset the timer. Ignore phone flip gestures and volume changes in Waiting and Off states.
- When volume-up or volume-down is pressed during Active state: allow the system volume to change and reset the timer to the original configured duration.
- When the phone is flipped (face-up to face-down, or face-down to face-down, detected via motion sensor) during Active state: reset the timer to the original configured duration.
- If a phone flip gesture occurs during fade-out: cancel the fade-out, restore the volume to pre-fade level, and reset the timer.
- If volume-up or volume-down is pressed during fade-out: cancel the fade-out, restore the volume to pre-fade level, and reset the timer.
- When the timer expires: fade to zero over 30 seconds (starting fast and slowing down along a curve), pause all active media apps, restore the pre-fade volume after pausing media, and return to the Waiting state.
- When the timer is turned off: leave the current volume unchanged, display the Off notification (if notification display is enabled), and allow media to continue playing.
- Provide haptic feedback (a short, faint vibration) to confirm user actions (turning off/on, volume button resets, and flip gestures).

## Auto Sleep Timer (Do Not Disturb)
- **Purpose**: Optionally turn on the sleep timer when Android's Do Not Disturb mode is activated and turn it off when DND is deactivated.
- **Behavior**:
  - Toggling ON opens Android Do Not Disturb settings.
  - Optional automation feature; manual sleep timer toggling remains available at all times regardless of whether this setting is enabled.
  - When `auto_timer_enabled` is true, Auto Sleep Droid listens for DND filter change events (`NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED`).
  - When DND becomes active (interruption filter is not `INTERRUPTION_FILTER_ALL`), the sleep timer is automatically turned ON.
  - When DND becomes inactive (interruption filter returns to `INTERRUPTION_FILTER_ALL`), the sleep timer is automatically turned OFF.

## Reboot behavior & Alarm persistence
- Persist whether the timer was running (Waiting, Active, Fading) versus explicitly **Off**, along with the target expiration timestamp.
- Use exact system alarms as a backup trigger to ensure timer expiration fires reliably even if Doze mode or battery saver restricts background service polling.
- Prompt the user in the main app screen when launched to grant Alarms & Reminders permission on Android 12+. If denied, fall back gracefully to standard background service callbacks without crashing.
- If the app process was terminated or the device was rebooted during an active timer countdown, restore the exact remaining countdown (or trigger immediate fade if the timestamp passed).
- If the app was explicitly in the **Off** state prior to reboot, keep it in the **Off** state.

## Import & Export Settings
- **Purpose**: Enable users to back up, restore, or transfer app configuration (sleep timer duration, timer state, show notification preference, Smart Wake-Up Goal preferences, and minimum sleep safeguard) across devices.
- **Export Settings**: Tapping "Export" serializes current settings into a standardized configuration string, launches Android's native system share action (`ACTION_SEND`) allowing the user to copy or send settings, and logs the action to the event log.
- **Import Settings**: Tapping "Import" opens an instructional dialog guiding the user on pasting or editing a configuration string (pre-filling valid clipboard content automatically). Applying a valid configuration updates application settings, refreshes ongoing notifications and scheduled alarms, displays a Toast confirmation message, and logs the event.
- **Invalid Input Safeguard**: If an imported string is invalid, malformed, or contains out-of-range parameters, existing settings remain completely unchanged, an error Toast message is shown, and the failure is logged to the event log.

## Smart Target Wake-Up Goal ("Auto Sleep")
- **Purpose**: Automatically manage your daily wake-up alarm based on your target wake-up goal time and current wake-up time while ensuring you always get enough sleep, operating independently of whether the sleep timer is turned on.
- **How It Works**:
  1. **Independent Operation with Sleep Timer Adjustments**: The wake alarm operates independently of whether the sleep timer is turned on or off. Disabling or turning off the sleep timer does not cancel or disable scheduled wake alarms, and the Alarm section in `MainActivity` remains accessible whenever the wake-up goal feature is enabled. However, user interactions with the sleep timer (starting, resetting, or updating duration) can adjust the current wake alarm forward if necessary to enforce the minimum sleep safeguard.
  2. **Daily Recurring Alarm & Current Wake-Up Time**:
     - **Current Wake-Up Time**: Represents the exact clock time when the upcoming wake alarm will ring. Users can view and manually adjust the current wake-up time directly via a dedicated picker row on `MainActivity`.
     - When configured and enabled, the wake alarm is set daily. When the current wake alarm is dismissed or triggered, the next wake-up alarm for the following day is set to `currentWakeUpTime - 15 minutes` (or target goal time if `currentWakeUpTime - 15m` is earlier than target goal time).
  3. **Minimum Sleep Safeguard & Push-Forward Behavior**:
     - Any user interactions with the sleep timer (starting or resetting the timer, or updating timer duration) calculate `requiredWakeUpTime = timerStartTime + minimumSleepDuration` (simply adding minimum sleep duration to timer start time, without including sleep timer countdown duration).
     - If `requiredWakeUpTime` is later than `currentWakeUpTime`, `currentWakeUpTime` is automatically pushed forward to `requiredWakeUpTime` to respect the minimum sleep safeguard.
  4. **Alarm Trigger & Audio**:
     - Upon expiration, the app gradually increases the default system alarm tone volume over 3 minutes along a gentle psychoacoustic crescendo curve and updates the ongoing status notification to display the ringing alarm status.
  5. **Single Alarm Creation**: The app maintains only one wake-up alarm named `"Auto Sleep"`.
  6. **Wake-Up Alarm Gestures & Persistence**:
     - **Flip to Snooze**: Flipping the phone while the wake-up alarm is ringing snoozes the alarm for 9 minutes and updates the notification text.
     - **Volume Button to Dismiss**: Pressing a hardware volume button while the wake-up alarm is ringing or snoozed dismisses the alarm and reverts the notification back to standard status.
     - **Sleep Timer Toggle Independence**: Turning off or disabling the sleep timer does not affect scheduled wake alarms.
- **Disabled by Default**: The feature is off by default until enabled in `MainActivity`.
- **User Inputs**:
  - **Target Goal Time** (e.g., `06:30 AM`).
  - **Current Wake-Up Time** (e.g., `06:30 AM`, editable clock time for next alarm).
  - **Minimum Sleep Duration** (default `7.5 hours`).
- **Event Logging**:
  - Every calculation and alarm update is logged line-by-line in the debug event log on `MainActivity`.

## Nap Timer
- **Purpose**: A minimal, quick way to start or cancel a nap directly from the main screen or status notification shade.
- **UI & Notification Actions**:
  - Main Screen (`MainActivity`): Features a dedicated Nap section with a Nap button (`btn_nap`). Tapping **Nap** launches `NapDialogActivity` prefilled with previously used nap duration; if active, tapping **Cancel Nap** cancels the active nap alarm.
  - Nap Dialog: Presented using standard system alert dialog styling with DurationInputView and standard positive ("Nap") / negative ("Cancel") buttons, styled consistently with all other dialogs.
  - Notification Shade: Features a **Nap** / **Cancel Nap** action button. Tapping **Nap** launches `NapDialogActivity` without pulling `MainActivity` or the main UI to the foreground; tapping **Cancel Nap** cancels the nap alarm.
- **Nap Alarm & Reset Behavior**:
  - Uses existing wake alarm behavior (alarm tone with 3-minute volume crescendo, flip gesture snooze, volume button dismiss).
  - When the sleep timer is reset (via flip gesture, volume button press, or duration update), an active nap alarm is pushed forward by the same reset increment.

## Health Connect Integration
- **Purpose**: Automatically save sleep and wake timestamps as sleep sessions (both nightly sleep and naps) to Health Connect when enabled.
- **Behavior**:
  - A toggle setting on the main screen allows enabling or disabling Health Connect synchronization.
  - When enabled, the app captures the start time when the sleep timer expires or a nap is started, and records the wake time when the wake alarm or nap alarm is dismissed.
  - Valid sleep sessions and naps are automatically persisted to Health Connect.
  - If Health Connect is unavailable or permissions are not granted, the user is notified via a message and the setting remains off.

## Acceptance criteria
- The main activity presents a single-screen configuration UI for all timer, goal, notification, and event log settings.
- Real-time timestamped event logs are displayed directly on `MainActivity`.
- The complete timer workflow is configurable from `MainActivity` and toggleable from the notification bar, system volume buttons, and phone flip gesture.
- The notification action button contains a single action: "Disable" when enabled or "Enable" when disabled.
- The "Show notification" setting toggles ongoing notification shade notification visibility across all timer states.
- Volume-up and volume-down both reset an active timer while preserving their normal volume behavior.
- Expiration pauses active media after a 30-second fade-out, restores pre-fade volume after pausing media, and successfully reverts to the Waiting state.
- Disabling the timer does not pause media or change the current volume.
- Invalid duration inputs gracefully default to the last valid or default duration.
- Post-reboot behavior respects the last saved state (preserving Off status or returning running timers to Waiting).
- The Smart Wake-Up Goal feature is disabled by default until explicitly enabled by the user in `MainActivity`.
- Notifications remain minimal and compact when collapsed, expanding to show full details (fade target and scheduled wake-up alarm time).
- The wake-up alarm is daily recurring, automatically scheduling the next alarm for the same target goal time when the current alarm rings.
- Starting the sleep timer schedules/updates the `"Auto Sleep"` wake-up alarm (when enabled) using `Math.max(targetGoalTime, timerStartTime + sleepTimerDuration + minimumSleepDuration)` while enforcing a minimum sleep duration safeguard (default 7.5h) via background `AlarmManager.setAlarmClock`.
- Disabling the timer does not cancel scheduled wake alarms, allowing the wake alarm to operate independently of the sleep timer.
- Flipping the phone while the wake-up alarm is ringing snoozes the alarm for 9 minutes.
- The main screen includes "Export" and "Import" action links rendered in the bottom action link list.
- Tapping "Export" serializes configuration settings and launches a system share action (`ACTION_SEND`).
- Tapping "Import" presents an instructional dialog for pasting configuration strings, updating preferences and notifications upon valid input, or preserving existing preferences when given invalid input.
- Health Connect synchronization can be toggled from the main screen UI and automatically records sleep sessions when sleep and wake events occur.
