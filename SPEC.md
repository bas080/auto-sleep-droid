# Auto Sleep Droid Specification

## Product goal
Provide an Android sleep timer controlled from the notification bar. The main UI displays a live event log (one line per event) for debugging.

## System states
- Permissions Pending: The initial state upon first launch or if permissions are revoked. A notification invites the user to tap it to grant the required system permissions.
- Off: The timer is manually disabled. Media continues playing normally, and the current volume remains entirely unchanged.
- Waiting: A duration is configured. The app sits passively and listens for active media playback.
- Active: Triggered by media playback, the timer actively counts down from the configured duration.
- Fading: The timer reaches zero, initiating a 30-second volume fade along a curve that starts steep and flattens out. Completing this fade pauses media, restores pre-fade volume, and returns the app back to the Waiting state.

## Notification states and content
- Permissions Pending: 
  - Text: "Setup required Tap to grant permissions"
  - Action: Tapping the notification directs the user to the Android Settings page for media control / notification listener permissions.
  - Buttons: None (relies on notification tap).
- Off: 
  - Text: "Sleep timer is off"
  - Buttons: "Set Timer" (Opens inline text reply to input duration, pre-filled with the default or last used duration).
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
- Use notification buttons for sleep-timer controls (including the "Set Timer" inline reply available in all states except Permissions Pending, and the "Turn Off" button available when the timer is not already stopped/Off).
- Use notifications for timer state and feedback.
- Tapping/clicking the notification body expands or collapses the notification in all states except Permissions Pending (where notification listener access / media controls permission has not been granted and tapping directs the user to system settings).
- Keep the notification ongoing so the user cannot dismiss it.
- Enter the duration through a minimal inline notification reply using Android's native text input mechanism.
- When permissions are missing, display a preliminary notification that invites the user to click it to start the permission granting process.
- Direct the user to the appropriate Android Settings page for media control when the pending notification is tapped.
- Upon successfully granting permissions, transition directly to the **Waiting** state using the default duration.

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
- If volume-up or volume-down is pressed during fade-out: cancel the fade-out, keep the new user-selected volume unchanged, and reset the timer.
- When the timer expires: fade halfway to zero over 30 seconds (starting fast and slowing down along a curve), pause all active media apps, restore the pre-fade volume after pausing media, and return to the Waiting state.
- When the timer is turned off: leave the current volume unchanged and allow media to continue playing.
- The notification provides duration input and turn-off actions; timer state is controlled automatically by playback, volume button resets, expiry, and duration replies.

## Reboot behavior
- Persist whether the timer was running (Waiting, Active, Fading) versus explicitly **Off**.
- If the app was in an active/waiting state prior to reboot, restore it to the **Waiting** state using the configured or default duration.
- If the app was explicitly in the **Off** state prior to reboot, keep it in the **Off** state.

## Acceptance criteria
- The main activity prints a list of events, one per line, for debugging.
- The complete timer workflow is possible from the notification bar and system volume buttons.
- Volume-up and volume-down both reset an active timer while preserving their normal volume behavior.
- Expiration pauses active media after a 30-second fade-out, restores pre-fade volume after pausing media, and successfully reverts to the Waiting state.
- Disabling the timer does not pause media or change the current volume.
- Invalid inline reply inputs gracefully default to the last valid or default duration.
- Post-reboot behavior respects the last saved state (preserving Off status or returning running timers to Waiting).
- The "Set Timer" action is available across all states except Permissions Pending, and the "Turn Off" button is present whenever the timer is not already stopped.
