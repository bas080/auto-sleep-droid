# Auto Sleep Droid Specification

## Product goal
Provide an Android sleep timer controlled via notification shade controls. The main UI displays a live event log (one line per event) for debugging.

## System states
- Off: The timer is manually disabled. Media continues playing normally, and the current volume remains entirely unchanged.
- Waiting: A duration is configured and auto-sleep is turned on. The app sits passively listening for active media playback via playback state listeners.
- Active: Triggered by media playback, the timer actively counts down from the configured duration.
- Fading: The timer reaches zero, initiating a 30-second volume fade along a curve that starts steep and flattens out. Completing this fade pauses media, restores pre-fade volume, and returns the app back to the Waiting state.

## Notification states and content
- Off:
  - Text: "Sleep timer is off"
  - Buttons: "Set Timer" (Inline reply to change duration) and "Turn On" (Enables timer without changing configured duration).
- Waiting: 
  - Text: "Waiting for media playback"
  - Duration: Displays the configured duration.
  - Buttons: "Set Timer" (Inline reply to change duration) and "Turn Off" (Disables timer and transitions to Off state).
- Active: 
  - Text: "Timer running"
  - Duration: Displays the target fade-out clock time and configured duration (e.g. "Fades out at 11:15 PM (20m configured)").
  - Buttons: "Set Timer" (Inline reply to change duration) and "Turn Off" (Disables timer and transitions to Off state).
- Fading: 
  - Text: "Fading volume"
  - Duration: Displays the original configured duration.
  - Buttons: "Set Timer" (Inline reply to change duration) and "Turn Off" (Cancels the fade, disables timer, and transitions to Off state).

## User interface
- Main Application Screen (`MainActivity`):
  - A top header section contains controls and status for the System Wake-Up Alarm feature above the main content:
    - **"Set Wake-Up Alarm" button**: Opens an input dialog to configure $N$ hours and $M$ minutes wake-up duration.
    - **"Clear Alarm" button**: Disables the system wake-up alarm and clears configured offset values.
    - **Wake-Up Alarm Status View**: A text element positioned directly above the log view summarizing the current alarm status (e.g., `"Wake-Up Alarm: 7h 0m after sleep timer completion"` or `"Wake-Up Alarm: Disabled"`).
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

## System Wake-Up Alarm Configuration ("auto-sleep-droid")
- **Purpose**: Allow the user to configure a wake-up system clock alarm relative to their sleep timer, ensuring they wake up at the desired time after falling asleep.
- **Alarm Label & Type**: The system alarm is non-recurring and strictly named `"auto-sleep-droid"`.
- **UI Location & Dialog Interaction**:
  - Configured directly within the main app UI (`MainActivity`), positioned at the top above the event log.
  - Tapping **"Set Wake-Up Alarm"** launches a dialog with two input fields: Hours ($N$, 0 to 24) and Minutes ($M$, 0 to 59).
  - The dialog pre-fills with the currently configured alarm offset or the default value (7 hours 0 minutes).
  - Tapping **"Clear Alarm"** clears the alarm offset, disables automatic scheduling, and updates the status text view to `"Wake-Up Alarm: Disabled"`.
- **Upsert Behavior**:
  - If an alarm named `"auto-sleep-droid"` does not exist in the system Clock app, create it for the calculated alarm time.
  - If an alarm named `"auto-sleep-droid"` already exists, update its scheduled time rather than creating duplicate alarms.
- **Configuration & Inputs**:
  - Users can configure the duration offset in $N$ hours (0 to 24) and $M$ minutes (0 to 59).
  - Total combined wake-up offset range: 1 minute to 24 hours.
  - Default offset: 7 hours 0 minutes when enabled without user modification.
  - **Invalid Inputs**: Non-numeric or out-of-range hour/minute entries fall back safely to the previously configured duration offset or default (7 hours 0 minutes).
- **Dynamic Recalculation**:
  - **Timer Expiration**: When the sleep timer completes its fade-out, the system alarm `"auto-sleep-droid"` is scheduled or updated to fire $N$ hours and $M$ minutes from that moment.
  - **Timer Resets (Volume / Flip gesture)**: If the sleep timer is reset during active countdown via physical volume buttons or flip gestures, the system alarm target timestamp is recalculated accordingly.
  - **Timer Turn Off / Cancellation**: Turning off the sleep timer or tapping "Clear Alarm" cancels any pending `"auto-sleep-droid"` system wake-up alarm.
- **Event Logging**:
  - Every system alarm creation, update, recalculation, and cancellation event is logged line-by-line in the main activity event log.

## Acceptance criteria
- The main activity prints a list of events, one per line, for debugging.
- The complete timer workflow is possible from the notification bar, system volume buttons, and phone flip gesture.
- Volume-up and volume-down both reset an active timer while preserving their normal volume behavior.
- Expiration pauses active media after a 30-second fade-out, restores pre-fade volume after pausing media, and successfully reverts to the Waiting state.
- Disabling the timer does not pause media or change the current volume.
- Invalid inline reply inputs gracefully default to the last valid or default duration.
- Post-reboot behavior respects the last saved state (preserving Off status or returning running timers to Waiting).
- The "Set Timer" action is available across all states, the "Turn Off" button is present whenever the timer is enabled, and the "Turn On" button is present when the timer is Off.
- The main activity UI presents top header controls ("Set Wake-Up Alarm", "Clear Alarm", and status text) to configure, display, and clear the system wake-up alarm offset.
- Users can configure an $N$ hours and $M$ minutes wake-up alarm offset via a main UI dialog that creates or updates a non-recurring system clock alarm labeled `"auto-sleep-droid"`.
- If an `"auto-sleep-droid"` alarm already exists, its scheduled time is updated; if it does not exist, a new alarm is created.
- Timer resets recalculate the target wake-up time, and disabling the timer or tapping "Clear Alarm" cancels the scheduled `"auto-sleep-droid"` alarm.
