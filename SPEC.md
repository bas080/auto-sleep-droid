# Auto Sleep Droid Specification

## Product goal

Provide an Android sleep timer controlled entirely from the notification bar. The app must not require a standalone user interface.

## User interface

- Use notification buttons for sleep-timer controls.
- Use notifications for timer state and feedback.
- Keep the notification ongoing so the user cannot dismiss it.
- Enter the duration through a minimal inline notification reply.
- Require notification access before starting the timer service or accepting timer input.
- Do not provide a notification action for media permission; request it through Android Settings before the notification is created.

## Timer configuration

- The user can turn the sleep timer on or off from the notification.
- The duration is entered in minutes when the timer is turned on.
- Minimum duration: 1 minute.
- Maximum duration: 24 hours.
- Default duration: 20 minutes when the user has not configured a duration.
- Store the original configured duration while the timer is active.
- Prefill or suggest the default or last configured duration in the inline notification reply.
- Once a duration has been configured, start the timer automatically when polling detects a volume change while it is inactive.
- Start the timer automatically when polling detects active music playback.
- When inactive, communicate that the timer is waiting for playback or a volume change rather than stopped.
- Show the configured duration in waiting, active, and fade states.
- After the timer pauses media at expiry, starting media playback again starts the timer automatically.

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
- The notification provides only the duration input action; timer state is controlled automatically by playback, volume changes, expiry, and duration replies.

## Reboot behavior

## Reboot behavior

- Persist the configured duration across reboots.
- If a countdown was active before reboot, start again from the full configured duration.
- If the timer was waiting before reboot, restore the waiting state and start a countdown when polling detects playback or a volume change.

Reboot preserves the configured duration; an active countdown starts again at its full duration, while a waiting timer resumes automatic playback and volume detection.

- The complete user workflow is possible from the notification bar and system volume buttons only.
- No custom app screen is required for setup or operation.
- A duration below 1 minute or above 24 hours is rejected.
- Volume-up and volume-down both reset an active timer while preserving their normal volume behavior.
- Expiration pauses all active media after a 15-second fade-out and restores the pre-fade volume.
- Disabling the timer does not pause media or change the current volume.
- Reboot restores the previous active/inactive state, and an active timer starts again at its full configured duration.
