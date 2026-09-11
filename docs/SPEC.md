# Auto Sleep Droid Specification

## Terminology
- **Sleep Timer**: The application feature that counts down while media is playing and fades volume down to zero to pause playback upon expiration.
- **Sleep Timer Duration**: The user-configured duration in minutes (default 20 minutes, min 1 minute, max 24 hours) that the sleep timer counts down before fading and pausing media.
- **Fade-Out / Fading**: The 30-second volume fade at sleep timer expiration where music volume gradually decreases along an ease-out curve down to zero before media playback is paused.
- **Wake-Up Alarm ("Auto Sleep")**: The background wake-up alarm scheduled via AlarmManager by Auto Sleep Droid that plays the system default alarm tone with a gentle crescendo. Users can snooze the alarm with a hardware volume button click (for 9 minutes) or stop/dismiss it solely by pressing "I'm Awake".
- **Target Goal Time**: The user's desired daily wake-up clock time (e.g., `06:30 AM`).
- **Minimum Sleep Duration**: The user-configured minimum sleep safeguard duration in hours (default 7.5 hours) ensuring that the wake-up alarm is set no earlier than `timerStartTime + minimumSleepDuration`. When media pauses after timer expiration, the effective remaining sleep safeguard is `Math.max(0, minimumSleepDuration - sleepTimerDuration)`.

## Product goal
Provide an Android sleep timer app configured directly from a single main UI screen with full-screen Manual and Event Logs views, simplified notification shade actions, and zero intrusive UI dialogs.

## System states
- Off: The timer is manually disabled. Media continues playing normally, and the current volume remains entirely unchanged.
- Waiting: A duration is configured and auto-sleep is turned on. The app sits passively listening for active media playback via playback state listeners.
- Active: Triggered by media playback, the timer actively counts down from the configured duration towards expiration. Pausing media while active does not pause or send the timer back to Waiting; the active countdown continues towards expiration and can be reset to the configured duration via volume changes or duration updates.
- Fading: The timer reaches zero, initiating a 30-second volume fade along a curve that starts steep and flattens out. Completing this fade pauses media, restores pre-fade volume, and returns the app back to the Waiting state.

## Notification states and content
All notification content is concise and directly visible in the notification body without hiding text in expanded views:

- Off: "Timer off (20m) • Wake at 6:15 AM" (Alarm detail shown when wake-up goal is enabled) • Button: "Enable"
- Waiting: "Waiting for playback (20m) • Wake at 6:15 AM" (Alarm detail shown when wake-up goal is enabled) • Button: "Disable"
- Active: "Fades out at 11:15 PM (20m) • Wake at 6:15 AM" (Alarm detail shown when wake-up goal is enabled) • Button: "Disable"
- Fading: "Fading volume" • Button: "Disable"
- Wake-up Alarm Ringing: "Press volume button to snooze • Press I'm Awake to stop" • Buttons: "I'm Awake", "Snooze"
- Wake-up Alarm Snoozed: "Snoozed 9m • Press I'm Awake to stop" • Button: "I'm Awake"

Only the action button lives in the shade. All information text is directly visible in the main notification view.
If the "Show notification" setting is disabled by the user (disabled by default), the ongoing sleep timer notification is hidden in all timer states (Off, Waiting, Active, Fading).

## User interface
- Main Application Screen (`MainActivity`):
  - Provides a complete single-screen configuration UI for all settings:
    - Sleep timer enable/disable switch and timer duration input (0-12h with 5m steps using hour and minute wheel pickers). Timer duration controls remain enabled when the sleep timer switch is OFF.
    - Wake-up alarm enable switch ("Wake-up alarm"), target wake-up time picker button, current wake-up time button, and minimum sleep duration input.
    - Health Connect synchronization switch and minimum session duration input.
    - Do Not Disturb section featuring Auto sleep timer switch.
    - Backup section featuring Export settings row and Import settings row.
    - About section featuring Version row, Feedback row (prompts user whether to include event logs in their email), and Links row.
    - Section headings (Timer, Alarm, Health Connect, Do Not Disturb, Backup, About) remain fully visible and opaque at all times.
  - Action links under a "Links" header: Manual, Logs, and Donate.
  - Full-screen non-dialog overlay views for Manual and Event Logs featuring a Back button pinned to the bottom right corner.
- Notification Shade Controls:
  - The notification features primary toggle actions ("Disable" when enabled, or "Enable" when disabled) and an optional secondary action button ("I'm Awake").
  - The secondary action displays "I'm Awake" during the awake window (`currentWakeTime +/- (minSleepDuration / 2)`) or during the alarm phase (when an alarm is ringing or snoozed).
  - Tapping "I'm Awake" stops any ringing or snoozed alarm, completes/logs the active sleep session to Health Connect (if enabled), updates current wake-up time to `max(targetGoalTime, T - 15m)` where `T` is system time when pressed, and reschedules the wake alarm for tomorrow.
  - Tapping/clicking the notification body opens `MainActivity`.

## Timer configuration
- The user can turn the sleep timer on or off and configure all options from the main screen UI or toggle state from the notification.
- The duration is entered in minutes, supporting natural duration input strings (e.g., plain integers default to minutes like `30`, hours `1h`, hours and minutes `2h15m`).
- Minimum duration: 1 minute.
- Maximum duration: 24 hours.
- Default duration: 20 minutes when the user has not configured a duration.
- Store the original configured duration while the timer is active.
- **Invalid inputs:** If the user enters an invalid or malformed duration string, fall back safely to the already configured time or default duration.
- Use a playback listener API while in the Waiting state to detect when audio playback starts automatically.
- When in the Waiting state, communicate that the timer is waiting for playback rather than stopped.
- Show the configured duration in waiting, active, and fade states.

## Timer behavior
- When enabled, count down from the configured duration while media is playing.
- Detect volume changes during Active (media playback) and Fading states to reset the timer to full duration.
- When volume-up or volume-down is pressed during Active state: allow the system volume to change and reset the timer to the original configured duration.
- If volume-up or volume-down is pressed during fade-out: cancel the fade-out, restore the volume to pre-fade level, and reset the timer.
- When the timer expires: fade to zero over 30 seconds (starting fast and slowing down along a curve), pause all active media apps, restore pre-fade volume after pausing media, and return to the Waiting state.
- When the timer is turned off: leave the current volume unchanged, display the Off notification (if notification display is enabled), and allow media to continue playing.
- Provide haptic feedback (a short, faint vibration) to confirm user actions (turning off/on, volume button resets).

## Auto Sleep Timer (Do Not Disturb)
- **Purpose**: Optionally turn on the sleep timer when Android's Do Not Disturb mode is activated and turn it off when DND is deactivated.
- **Behavior**:
  - Toggling ON enables automated DND state tracking without automatically redirecting away from the app.
  - Optional automation feature; manual sleep timer toggling remains available at all times.
  - When `auto_timer_enabled` is true, Auto Sleep Droid listens for DND filter change events (`NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED`).
  - When DND becomes active (interruption filter is not `INTERRUPTION_FILTER_ALL`), the sleep timer is automatically turned ON.
  - When DND becomes inactive (interruption filter returns to `INTERRUPTION_FILTER_ALL`), the sleep timer is automatically turned OFF.

## Reboot behavior & Alarm persistence
- Persist whether the timer was running (Waiting, Active, Fading) versus explicitly **Off**, along with the target expiration timestamp.
- Use exact system alarms as a backup trigger to ensure timer expiration fires reliably.
- If the app process was terminated or the device was rebooted during an active timer countdown, restore the exact remaining countdown (or trigger immediate fade if timestamp passed).
- If the app was explicitly in the **Off** state prior to reboot, keep it in the **Off** state.

## Import & Export Settings
- **Purpose**: Enable users to back up, restore, or transfer app configuration across devices.
- **Export Settings**: Tapping "Export" serializes current settings into a standardized configuration string, launches Android's native system share action (`ACTION_SEND`).
- **Import Settings**: Tapping "Import" opens an instructional dialog guiding the user on pasting or editing a configuration string. Applying a valid configuration updates application settings, refreshes ongoing notifications and scheduled alarms.
- **Invalid Input Safeguard**: If an imported string is invalid, existing settings remain unchanged.

## Smart Target Wake-Up Goal ("Auto Sleep")
- **Purpose**: Automatically manage your daily wake-up alarm based on your target wake-up goal time and current wake-up time while ensuring you always get enough sleep, operating independently of whether the sleep timer is turned on.
- **How It Works**:
  1. **Independent Operation**: Disabling or turning off the sleep timer does not cancel or disable scheduled wake alarms. However, sleep timer reschedules push current wake time forward if needed to enforce minimum sleep duration safeguards.
  2. **Daily Recurring Alarm & Current Wake-Up Time**:
     - **Current Wake-Up Time**: Represents the exact clock time when the upcoming wake alarm will ring. Users can view and manually adjust current wake-up time directly via a dedicated picker row on `MainActivity`.
     - When the current wake alarm is dismissed or triggered via "I'm Awake", the next wake-up alarm for tomorrow is reset to target goal time.
  3. **Minimum Sleep Safeguard & Push-Forward Behavior**:
     - When an active sleep session exists or sleep timer is active, `requiredWakeUpTime` ensures minimum sleep duration is respected.
     - If `requiredWakeUpTime` is later than `currentWakeUpTime`, `currentWakeUpTime` is pushed forward.
     - When going to bed early (within `1.2 * minimumSleepDuration` window before `currentWakeUpTime`), starting or rescheduling the sleep timer automatically moves `currentWakeUpTime` earlier toward `Math.max(targetGoalTime, requiredWakeUpTime)`.
  4. **Alarm Trigger & Audio**:
     - Upon expiration, the app explicitly unmutes `STREAM_ALARM` if muted, ensures audible volume, gradually increases the default system alarm tone volume over 3 minutes along a crescendo curve, and updates the ongoing notification.
  5. **Single Alarm Creation**: The app maintains only one wake-up alarm named `"Auto Sleep"`.
  6. **Wake-Up Alarm Controls**:
     - **Volume Button to Snooze**: Pressing any hardware volume button while the wake-up alarm is ringing or snoozed snoozes the alarm for 9 minutes.
     - **"I'm Awake" to Stop**: Tapping **I'm Awake** in the notification shade or main UI is the sole action to stop a ringing or snoozed alarm.
     - **Awake Window & Current Wake Time Shift**: Tapping **I'm Awake** during your awake window (`currentWakeTime +/- (minSleepDuration / 2)`) or while ringing/snoozed stops the alarm, sets current wake-up time to `max(targetGoalTime, T - 15m)` where `T` is current system time, completes/logs the active sleep session to Health Connect (if enabled), and reschedules tomorrow's alarm.
- **Disabled by Default**: The feature is off by default until enabled in `MainActivity`.

## Health Connect Integration
- **Purpose**: Automatically save sleep and wake timestamps as sleep sessions to Health Connect when enabled.
- **Behavior**:
  - A toggle setting on the main screen allows enabling or disabling Health Connect synchronization.
  - Configurable minimum session threshold (`hc_min_duration_minutes`, default 15 minutes). Sessions shorter than this threshold are ignored.
  - When enabled, night sleep start time uses the sleep timer start time (`timer_start_time_ms`), falling back to `wakeTime - minimumSleepDuration` if no sleep timer was run. Wake time is captured when **I'm Awake** is tapped.
  - Valid sleep sessions exceeding threshold duration are automatically persisted to Health Connect.

## Acceptance criteria
- The main activity presents a single-screen configuration UI for all timer, goal, notification, and event log settings.
- Hardware volume button presses snooze ringing or snoozed wake-up alarms for 9 minutes.
- Tapping **"I'm Awake"** is the sole action to stop a ringing or snoozed wake alarm, setting current wake time to `max(targetGoalTime, T - 15m)`.
- The "I'm Awake" action is visible during the time range `currentWakeTime +/- (minSleepDuration / 2)` or when alarms are ringing/snoozed.
- Nap feature and phone flip gesture sensors are completely removed.
- All 121 unit tests pass 100% and release APK builds cleanly.
