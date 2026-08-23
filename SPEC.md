# Auto Sleep Droid Specification

## Product goal
Provide an Android sleep timer controlled from the notification bar. The main UI displays a live event log (one line per event) for debugging.

## System states
- Off: The timer is manually disabled. Media continues playing normally, and the current volume remains entirely unchanged.
- Waiting: A duration is configured. The app sits passively and listens for active media playback.
- Active: Triggered by media playback, the timer actively counts down from the configured duration.
- Fading: The timer reaches zero, initiating a 30-second volume fade along a curve that starts steep and flattens out. Completing this fade pauses media, restores pre-fade volume, and returns the app back to the Waiting state.

## Notification states and content
- Off: 
  - Text: "Sleep timer is off"
  - Buttons: "Set Timer" (Opens inline text reply to input duration) and "Turn On" (Enables timer without changing configured duration).
- Waiting: 
  - Text: "Waiting for media playback"
  - Duration: Displays the configured duration.
  - Buttons: "Set Timer" (Inline reply to change duration) and "Turn Off" (Disables timer and transitions to Off state).
- Active: 
  - Text: "Timer running"
  - Duration: Displays the live countdown of time remaining.
  - Buttons: "Set Timer" (Inline reply to change duration) and "Turn Off" (Disables timer and transitions to Off state).
- Fading: 
  - Text: "Fading volume"
  - Duration: Displays the original configured duration.
  - Buttons: "Set Timer" (Inline reply to change duration) and "Turn Off" (Cancels the fade, disables timer, and transitions to Off state).

## User interface
- Display a line-by-line list of timestamped events in the main UI for debugging purposes.
- Use notification buttons for sleep-timer controls (including the "Set Timer" inline reply available in all states, "Turn Off" button available when enabled, and "Turn On" button available when Off).
- Use notifications for timer state and feedback.
- Tapping/clicking the notification body expands or collapses the notification in all states.
- Keep the notification ongoing across all states (including when Off) so the user cannot swipe it away or accidentally dismiss it.
- Enter the duration through a minimal inline notification reply using Android's native text input mechanism.

## Timer configuration
- The user can turn the sleep timer on or off from the notification.
- The duration is entered in minutes when the timer is turned on.
- Minimum duration: 1 minute.
- Maximum duration: 24 hours.
- Default duration: 20 minutes when the user has not configured a duration.
- Store the original configured duration while the timer is active.
- Prefill or suggest the default or last configured duration in the inline notification reply.
- **Invalid inputs:** If the user enters an invalid duration (e.g., non-numeric, out of range), fall back safely to the already configured time or the default duration.
- Start the timer automatically when polling detects active music playback.
- When in the Waiting state, communicate that the timer is waiting for playback rather than stopped.
- Show the configured duration in waiting, active, and fade states.

## Timer behavior
- When enabled, count down from the configured duration.
- When volume-up or volume-down is pressed: allow the system volume to change and reset the timer to the original configured duration.
- When the phone is flipped (face-up to face-down, or face-down to face-up, detected via motion sensor): reset the timer to the original configured duration.
- If a phone flip gesture occurs during fade-out: cancel the fade-out, restore the volume to pre-fade level, and reset the timer.
- If volume-up or volume-down is pressed during fade-out: cancel the fade-out, keep the new user-selected volume, and reset the timer.
- When the timer expires: fade to zero over 30 seconds (starting fast and slowing down along a curve), pause all active media apps, restore the pre-fade volume after pausing media, and return to the Waiting state.
- When the timer is turned off: leave the current volume unchanged and allow media to continue playing.
- The notification provides duration input and turn-off actions; timer state is controlled automatically by playback, volume button resets, expiry, and duration replies.
- Provide haptic feedback (a short, faint vibration) to confirm user actions (setting duration reply, turning off, volume button resets, and flip gestures).

## Reboot behavior & Alarm persistence
- Persist whether the timer was running (Waiting, Active, Fading) versus explicitly **Off**, along with the target expiration timestamp.
- Use exact system alarms as a backup trigger to ensure timer expiration fires reliably even if Doze mode or battery saver restricts background service polling.
- Prompt the user in the main app screen when launched to grant Alarms & Reminders permission on Android 12+. If denied, fall back gracefully to standard background service callbacks without crashing.
- If the app process was terminated or the device was rebooted during an active timer countdown, restore the exact remaining countdown (or trigger immediate fade if the timestamp passed).
- If the app was explicitly in the **Off** state prior to reboot, keep it in the **Off** state.

## Acceptance criteria
- The main activity prints a list of events, one per line, for debugging.
- The complete timer workflow is possible from the notification bar, system volume buttons, and phone flip gesture.
- Volume-up and volume-down both reset an active timer while preserving their normal volume behavior.
- Expiration pauses active media after a 30-second fade-out, restores pre-fade volume after pausing media, and successfully reverts to the Waiting state.
- Disabling the timer does not pause media or change the current volume.
- Invalid inline reply inputs gracefully default to the last valid or default duration.
- Post-reboot behavior respects the last saved state (preserving Off status or returning running timers to Waiting).
- The "Set Timer" action is available across all states, the "Turn Off" button is present whenever the timer is enabled, and the "Turn On" button is present when the timer is Off.
