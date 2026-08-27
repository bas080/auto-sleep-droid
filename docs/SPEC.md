# Auto Sleep Droid Specification

## Product goal
Provide an Android sleep timer controlled via notification shade controls. The main UI displays a live event log (one line per event) for debugging.

## System states
- Off: The timer is manually disabled. Media continues playing normally, and the current volume remains entirely unchanged.
- Waiting: A duration is configured and auto-sleep is turned on. The app sits passively listening for active media playback via playback state listeners.
- Active: Triggered by media playback, the timer actively counts down from the configured duration.
- Fading: The timer reaches zero, initiating a 30-second volume fade along a curve that starts steep and flattens out. Completing this fade pauses media, restores pre-fade volume, and returns the app back to the Waiting state.

## Notification states and content
Notification text is kept compact and concise when collapsed, displaying detailed contextual information only when expanded:

- Off:
  - Collapsed Text: "Timer off"
  - Expanded Text: "Sleep timer is off"
  - Buttons: "Set Timer" (Inline reply to change duration) and "Turn On" (Enables timer without changing configured duration).
- Waiting: 
  - Collapsed Text: "Waiting for playback"
  - Expanded Text: "Waiting for media playback (20m configured)"
  - Buttons: "Set Timer" (Inline reply to change duration) and "Turn Off" (Disables timer and transitions to Off state).
- Active: 
  - Collapsed Text: "Timer running (11:15 PM)"
  - Expanded Text: "Fades out at 11:15 PM (20m configured) • Alarm set for 6:15 AM" (Alarm detail shown only when wake-up alarm is enabled).
  - Buttons: "Set Timer" (Inline reply to change duration) and "Turn Off" (Disables timer and transitions to Off state).
- Fading: 
  - Collapsed Text: "Fading volume"
  - Expanded Text: "Fading volume down to pause media"
  - Buttons: "Set Timer" (Inline reply to change duration) and "Turn Off" (Cancels the fade, disables timer, and transitions to Off state).

## User interface
- Main Application Screen (`MainActivity`):
  - A top header section contains controls and status for the Smart Wake-Up Goal feature above the main content:
    - **"Set Wake-Up Goal" button**: Opens an input dialog (with Minimum Sleep Duration input placed above the target goal time picker) to configure Target Wake-Up Goal Time (e.g., `06:30 AM`) and Minimum Sleep Duration (default 7.5 hours).
    - **"Clear Goal" button**: Disables the smart wake-up goal feature and cancels scheduled wake-up alarms.
    - **Wake-Up Goal Status View**: A text element positioned directly above the log view summarizing goal progress (e.g., `"Goal: 06:30 AM • Tonight's Alarm: 07:15 AM"` or `"Wake-Up Goal: Disabled"`).
  - A scrollable, line-by-line list of timestamped events fills the rest of the main UI for debugging purposes.
- Notification Shade Controls:
  - Use notification buttons for sleep-timer controls ("Set Timer" inline reply to change duration, "Turn Off" action button when enabled, and "Turn On" action button when Off).
  - Tapping/clicking the notification body expands or collapses the notification in all states.
  - Keep the notification ongoing across all states (including when Off) so the user cannot swipe it away or accidentally dismiss it.
  - Enter sleep timer duration through a minimal inline notification reply using Android's native text input mechanism.

## Timer configuration
- The user can turn the sleep timer on or off using notification controls.
- The duration is entered in minutes when configured.
- Minimum duration: 1 minute.
- Maximum duration: 24 hours.
- Default duration: 20 minutes when the user has not configured a duration.
- Store the original configured duration while the timer is active.
- Prefill or suggest the default or last configured duration in the inline notification reply.
- **Invalid inputs:** If the user enters an invalid duration (e.g., non-numeric, out of range), fall back safely to the already configured time or the default duration.
- Use a playback listener API to detect when audio playback starts or stops automatically.
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
- When the timer is turned off: leave the current volume unchanged, display the Off notification with "Turn On" button, and allow media to continue playing.
- Provide haptic feedback (a short, faint vibration) to confirm user actions (setting duration reply, turning off, volume button resets, and flip gestures).

## Reboot behavior & Alarm persistence
- Persist whether the timer was running (Waiting, Active, Fading) versus explicitly **Off**, along with the target expiration timestamp.
- Use exact system alarms as a backup trigger to ensure timer expiration fires reliably even if Doze mode or battery saver restricts background service polling.
- Prompt the user in the main app screen when launched to grant Alarms & Reminders permission on Android 12+. If denied, fall back gracefully to standard background service callbacks without crashing.
- If the app process was terminated or the device was rebooted during an active timer countdown, restore the exact remaining countdown (or trigger immediate fade if the timestamp passed).
- If the app was explicitly in the **Off** state prior to reboot, keep it in the **Off** state.

## Smart Target Wake-Up Goal ("Auto Sleep")
- **Purpose**: Automatically set your daily wake-up alarm to your target wake-up goal time while ensuring you always get enough sleep.
- **How It Works**:
  1. **Timer Start & Expected Expiration**: When the sleep timer starts or is reset, expected timer completion time is determined (`timerStartTime + configuredTimerDuration`).
  2. **12-Hour Window Safeguard**: The alarm is scheduled only when the timer starts within 12 hours prior to the target goal time.
  3. **Alarm Calculation**: The wake-up alarm is set to `Math.max(targetGoalTime, expectedTimerCompletionTime + minimumSleepDuration)`.
  4. **Single Alarm Creation**: The app maintains only one alarm in your Clock app named `"Auto Sleep"`.
- **Disabled by Default**: The feature is off by default until you tap "Set Wake-Up Goal". Tapping "Clear Goal" turns it off and removes the alarm.
- **User Inputs**:
  - **Target Goal Time** (e.g., `06:30 AM`).
  - **Minimum Sleep Duration** (default `7.5 hours`).
- **Header Controls & Display**:
  - Located at the top of the screen (`MainActivity`) showing current goal status (e.g., `"Goal: 06:30 AM • Tonight's Alarm: 07:15 AM"`).
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
- The "Set Timer" action is available across all states, the "Turn Off" button is present whenever the timer is enabled, and the "Turn On" button is present when the timer is Off.
- The Smart Wake-Up Goal feature is disabled by default until explicitly configured by the user.
- The main activity UI presents top header controls ("Set Wake-Up Goal", "Clear Goal", and status text) to configure, display, and clear the target wake-up goal and minimum sleep duration.
- Notifications remain minimal and compact when collapsed, expanding to show full details (configured duration, fade target, and scheduled wake-up alarm time).
- Starting the sleep timer within 12 hours of the target goal time schedules/updates the `"Auto Sleep"` system clock alarm (when enabled) using `Math.max(targetGoalTime, expectedTimerCompletionTime + minimumSleepDuration)` while enforcing a minimum sleep duration safeguard (default 7.5h).
- Disabling the timer or tapping "Clear Goal" cancels the scheduled `"Auto Sleep"` alarm in the background.
