# Auto Sleep Droid Specification

## Terminology
- **Sleep Timer**: The application feature that counts down while media is playing and fades volume down to zero to pause playback upon expiration.
- **Sleep Timer Duration**: The user-configured duration in minutes (default 20 minutes, min 1 minute, max 24 hours) that the sleep timer counts down before fading and pausing media.
- **Fade-Out / Fading**: The 30-second volume fade at sleep timer expiration where music volume gradually decreases along an ease-out curve down to zero before media playback is paused.
- **Wake-Up Alarm ("Auto Sleep")**: The background wake-up alarm scheduled via AlarmManager by Auto Sleep Droid that plays the system default alarm tone and displays a high-priority notification with Dismiss and Snooze (9 minutes) action buttons to wake the user up at or after their target goal time. The notification remains open when snoozed so users can dismiss the alarm at any time.
- **Target Goal Time**: The user's desired daily wake-up clock time (e.g., `06:30 AM`).
- **Minimum Sleep Duration**: The user-configured minimum sleep safeguard duration in hours (default 7.5 hours) ensuring that the wake-up alarm is set no earlier than `timerStartTime + sleepTimerDuration + minimumSleepDuration`.

## Product goal
Provide an Android sleep timer controlled via notification shade controls. The main UI displays a live event log (one line per event) for debugging.

## System states
- Off: The timer is manually disabled. Media continues playing normally, and the current volume remains entirely unchanged.
- Waiting: A duration is configured and auto-sleep is turned on. The app sits passively listening for active media playback via playback state listeners.
- Active: Triggered by media playback, the timer actively counts down from the configured duration towards expiration. Pausing media while active does not pause or send the timer back to Waiting; the active countdown continues towards expiration and can be reset to the configured duration via volume changes, flip gestures, or duration updates.
- Fading: The timer reaches zero, initiating a 30-second volume fade along a curve that starts steep and flattens out. Completing this fade pauses media, restores pre-fade volume, and returns the app back to the Waiting state.

## Notification states and content
Notification text is kept compact and concise when collapsed, displaying detailed contextual information only when expanded:

- Off:
  - Collapsed Text: "Timer off"
  - Expanded Text: "Sleep timer is off"
  - Buttons: "Set Timer" (Inline reply to change duration) and "Set Goal" (Opens Wake-Up Goal settings dialog).
- Waiting: 
  - Collapsed Text: "Waiting for playback"
  - Expanded Text: "Waiting for media playback • Alarm at 6:15 AM" (Alarm detail shown only when wake-up alarm is enabled).
  - Buttons: "Set Timer" (Inline reply to change duration) and "Set Goal" / "Goal HH:MM" (Opens Wake-Up Goal settings dialog).
- Active: 
  - Collapsed Text: "Fades out at 11:15 PM"
  - Expanded Text: "Fades out at 11:15 PM • Alarm at 6:15 AM" (Alarm detail shown only when wake-up alarm is enabled).
  - Buttons: "Set Timer" (Inline reply to change duration) and "Set Goal" / "Goal HH:MM" (Opens Wake-Up Goal settings dialog).
- Fading: 
  - Collapsed Text: "Fading volume"
  - Expanded Text: "Fading volume down to pause media"
  - Buttons: "Set Timer" (Inline reply to change duration) and "Set Goal" / "Goal HH:MM" (Opens Wake-Up Goal settings dialog).

## User interface
- Main Application Screen (`MainActivity`):
  - A scrollable, line-by-line list of timestamped events fills the main UI for debugging purposes.
  - Action links for "Export" and "Import" are rendered on the main UI in the header action link list alongside Releases, GitHub, Issues, and Donate.
- Notification Shade Controls:
  - Use notification buttons for sleep-timer controls ("Set Timer" inline reply to change duration, "Turn Off" action button when enabled, "Turn On" action button when Off, and "Set Goal" / "Goal HH:MM" action button to configure target wake-up goal).
  - Tapping/clicking the notification body opens MainActivity.
  - Keep the notification ongoing across all states (including when Off) so the user cannot swipe it away or accidentally dismiss it.
  - Enter sleep timer duration through a minimal inline notification reply using Android's native text input mechanism.

## Timer configuration
- The user can turn the sleep timer on or off using notification controls.
- The duration is entered in minutes when configured, supporting natural duration input strings (e.g., plain integers default to minutes like `30`, hours `1h`, hours and minutes `2h15m`, while seconds specifiers like `2h10m5s` or `15m30s` ignore seconds).
- Minimum duration: 1 minute.
- Maximum duration: 24 hours.
- Default duration: 20 minutes when the user has not configured a duration.
- Store the original configured duration while the timer is active.
- Prefill or suggest the default or last configured duration in the inline notification reply.
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
- If volume-up or volume-down is pressed during fade-out: cancel the fade-out, keep the new user-selected volume, and reset the timer.
- When the timer expires: fade to zero over 30 seconds (starting fast and slowing down along a curve), pause all active media apps, restore the pre-fade volume after pausing media, and return to the Waiting state.
- When the timer is turned off: leave the current volume unchanged, display the Off notification, and allow media to continue playing.
- Provide haptic feedback (a short, faint vibration) to confirm user actions (setting duration reply, turning off, volume button resets, and flip gestures).

## Reboot behavior & Alarm persistence
- Persist whether the timer was running (Waiting, Active, Fading) versus explicitly **Off**, along with the target expiration timestamp.
- Use exact system alarms as a backup trigger to ensure timer expiration fires reliably even if Doze mode or battery saver restricts background service polling.
- Prompt the user in the main app screen when launched to grant Alarms & Reminders permission on Android 12+. If denied, fall back gracefully to standard background service callbacks without crashing.
- If the app process was terminated or the device was rebooted during an active timer countdown, restore the exact remaining countdown (or trigger immediate fade if the timestamp passed).
- If the app was explicitly in the **Off** state prior to reboot, keep it in the **Off** state.

## Import & Export Settings
- **Purpose**: Enable users to back up, restore, or transfer app configuration (sleep timer duration, timer state, Smart Wake-Up Goal preferences, and minimum sleep safeguard) across devices.
- **Export Settings**: Tapping "Export" serializes current settings into a standardized configuration string, launches Android's native system share action (`ACTION_SEND`) allowing the user to copy or send settings, and logs the action to the event log.
- **Import Settings**: Tapping "Import" opens an instructional dialog guiding the user on pasting or editing a configuration string (pre-filling valid clipboard content automatically). Applying a valid configuration updates application settings, refreshes ongoing notifications and scheduled alarms, displays a Toast confirmation message, and logs the event.
- **Invalid Input Safeguard**: If an imported string is invalid, malformed, or contains out-of-range parameters, existing settings remain completely unchanged, an error Toast message is shown, and the failure is logged to the event log.

## Smart Target Wake-Up Goal ("Auto Sleep")
- **Purpose**: Automatically set your daily wake-up alarm to your target wake-up goal time while ensuring you always get enough sleep.
- **How It Works**:
  1. **Alarm Calculation at Timer Start**: When the sleep timer starts or is reset, the wake-up alarm is set to the maximum of target goal time and timer start time plus sleep timer duration plus minimum sleep duration safeguard. Upon expiration, the app plays the default system alarm tone and shows a high-priority notification with Dismiss and Snooze (9 minutes) options.
  2. **12-Hour Window Safeguard**: The alarm is scheduled only when the timer starts within 12 hours prior to the target goal time.
  3. **Single Alarm Creation**: The app maintains only one wake-up alarm named `"Auto Sleep"`.
  4. **Wake-Up Alarm Gestures & Persistence**:
     - **Flip to Snooze**: Flipping the phone while the wake-up alarm is ringing snoozes the alarm for 9 minutes.
     - **Volume Button to Dismiss**: Pressing a hardware volume button while the wake-up alarm is ringing or snoozed dismisses the alarm and removes the notification.
     - **Notification Persistence**: Snoozing the alarm (via flip or notification action) stops the alarm sound but keeps the notification open in the notification shade so the user can dismiss the alarm when desired.
     - **Dismiss**: Tapping the Dismiss button on the wake-up alarm notification dismisses the alarm and removes the notification.
- **Disabled by Default**: The feature is off by default until configured via the "Set Goal" notification action button. Tapping "Alarm HH:MM" in the notification turns it off and removes the alarm.
- **User Inputs**:
  - **Target Goal Time** (e.g., `06:30 AM`).
  - **Minimum Sleep Duration** (default `7.5 hours`).
- **Goal Settings Dialog (`GoalSettingsDialogActivity`)**:
  - Accessed via the notification action button ("Set Goal" / "Goal HH:MM"), presenting a modal overlay containing a target goal time picker, minimum sleep duration safeguard input, prefilled with configured preferences or defaults, and "OK" and "Cancel" buttons.
- **Event Logging**:
  - Every calculation and alarm update is logged line-by-line in the debug event log.

## Acceptance criteria
- The main activity prints a list of events, one per line, for debugging.
- The complete timer workflow is possible from the notification bar, system volume buttons, and phone flip gesture.
- Volume-up and volume-down both reset an active timer while preserving their normal volume behavior.
- Expiration pauses active media after a 30-second fade-out, restores pre-fade volume after pausing media, and successfully reverts to the Waiting state.
- Disabling the timer does not pause media or change the current volume.
- Invalid inline reply inputs gracefully default to the last valid or default duration.
- Post-reboot behavior respects the last saved state (preserving Off status or returning running timers to Waiting).
- Toggling the timer on/off and managing the wake-up goal are performed via notification action clicks. When the timer is enabled, tapping the duration action ("Sleep <duration>") turns the timer off; when disabled, tapping it opens inline duration edit. When a wake-up goal is set, tapping the goal action ("Alarm HH:MM") disables/clears the goal; when disabled, tapping it opens the goal settings dialog.
- The Smart Wake-Up Goal feature is disabled by default until explicitly configured by the user.
- The notification shade provides a "Set Goal" / "Goal HH:MM" action button that opens a dialog overlay (`GoalSettingsDialogActivity`) to configure, display, stop, or enable the target wake-up goal and minimum sleep duration.
- Notifications remain minimal and compact when collapsed, expanding to show full details (fade target and scheduled wake-up alarm time).
- Starting the sleep timer within 12 hours of the target goal time schedules/updates the `"Auto Sleep"` wake-up alarm (when enabled) using `Math.max(targetGoalTime, timerStartTime + sleepTimerDuration + minimumSleepDuration)` while enforcing a minimum sleep duration safeguard (default 7.5h) via background `AlarmManager.setAlarmClock`.
- Disabling the timer or tapping the goal notification action ("Alarm HH:MM") cancels the scheduled `"Auto Sleep"` alarm in the background without launching external Clock app UI activities.
- Flipping the phone while the wake-up alarm is ringing snoozes the alarm for 9 minutes.
- The main screen includes "Export" and "Import" action links rendered in the header link list.
- Tapping "Export" serializes configuration settings and launches a system share action (`ACTION_SEND`).
- Tapping "Import" presents an instructional dialog for pasting configuration strings, updating preferences and notifications upon valid input, or preserving existing preferences when given invalid input.
