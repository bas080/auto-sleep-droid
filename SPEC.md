# Auto Sleep Droid Specification

## Product goal

Provide an Android sleep timer controlled entirely from the notification bar. The app must not require a standalone user interface.

## User interface

- Use notification buttons for sleep-timer controls.
- Use notifications for timer state and feedback.
- Keep the notification ongoing so the user cannot dismiss it.
- Enter the duration through a minimal inline notification reply.

## Timer configuration

- The user can turn the sleep timer on or off from the notification.
- The duration is entered in minutes when the timer is turned on.
- Minimum duration: 1 minute.
- Maximum duration: 24 hours.
- Store the original configured duration while the timer is active.

## Timer behavior

- When enabled, count down from the configured duration.
- When volume-up is pressed:
  - Allow the system volume to change.
  - Reset the timer to the original configured duration.
- When volume-down is pressed:
  - Allow the system volume to change.
  - Reset the timer to the original configured duration.
- If volume-up or volume-down is pressed during fade-out:
  - Cancel the fade-out and timer expiry.
  - Keep the new user-selected volume unchanged.
  - Reset the timer to the original configured duration.
- When the timer expires:
  - Fade from the current volume to halfway to zero over 15 seconds.
  - Pause all active media apps.
  - Restore the volume captured immediately before the fade-out started.
- When the timer is turned off:
  - Leave the current volume unchanged.
  - Allow media to continue playing.

## Reboot behavior

- Persist whether the timer was active or inactive.
- Restore that active/inactive state after reboot.
- If the timer was active before reboot, reset it to the original configured duration.
- If the timer was inactive before reboot, leave it inactive.

## Acceptance criteria

- The complete user workflow is possible from the notification bar and system volume buttons only.
- No custom app screen is required for setup or operation.
- A duration below 1 minute or above 24 hours is rejected.
- Volume-up and volume-down both reset an active timer while preserving their normal volume behavior.
- Expiration pauses all active media after a 15-second fade-out and restores the pre-fade volume.
- Disabling the timer does not pause media or change the current volume.
- Reboot restores the previous active/inactive state, and an active timer starts again at its full configured duration.
