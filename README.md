# Auto Sleep Droid

Auto Sleep Droid puts your phone to sleep when you fall asleep, and wakes you up when you're ready. It is a low-friction Android sleep timer and wake-up safeguard for media playback.

## Overview

- **Single-screen control:** Configure sleep timer duration, notification settings, Nap alarms, and Smart Wake-Up Goal options directly from the main screen with clear descriptions explaining each setting.
- **Quick Nap Alarm:** Easily set a quick nap alarm duration or cancel an active nap directly from the main screen or notification shade. Resetting the sleep timer automatically pushes active nap alarms forward by the same duration increment.
- **Never be startled awake:** Fall asleep peacefully to podcasts, YouTube playlists, audiobooks, or music without waking up hours later to blaring audio or autoplay videos.
- **Universal compatibility:** Controls playback via standard Android system media controls, making it compatible with YouTube, Spotify, podcast players, browsers, and most audio or video apps.
- **Gentle volume fade:** Volume slowly fades down before pausing playback so you aren't jolted awake by abrupt silence.
- **Screen-free resets & alarm controls:** Simply press a physical volume button or flip your phone over while in bed to reset the sleep timer, snooze the wake-up alarm (flip), or dismiss the alarm (volume button) without looking at a bright screen.
- **Smart Wake-Up Goal:** Set your target wake-up time and minimum required sleep duration. The app automatically calculates and schedules your wake-up alarm whenever you start your sleep timer, ensuring you always get enough rest.
- **Import & Export Configuration:** Easily back up, restore, or transfer settings using standardized configuration JSON strings.
- **Dedicated Event Logs & Manual:** View real-time timestamped system event logs or full-screen user documentation directly within full-screen overlay views.

## How It Works

1. **Configure from the main screen:** Open Auto Sleep Droid to turn on the timer, enter your desired sleep duration, launch quick nap alarms, toggle notifications, or enable Smart Wake-Up Goal.
2. **Automatic detection:** Sits passively until you start playing media, then automatically begins counting down.
3. **Notification controls:** Control ongoing timer state with **Disable** / **Enable** actions or launch/cancel naps with **Nap** / **Cancel Nap** directly in the notification shade.
4. **Fade & pause:** At expiry, volume fades down over 30 seconds, media playback pauses via system media controls, and original volume is restored.
5. **Smart wake-up alarm:** If enabled, the app automatically calculates and sets your daily wake-up alarm for your target time (or later if you went to bed late) to protect your minimum sleep duration. Flip or tap **Snooze** to snooze for 9 minutes (keeping the notification open in the shade), or press a volume button / tap **Dismiss** to dismiss the alarm.

## Usage Instructions

1. Launch Auto Sleep Droid and grant notification access when prompted.
2. Configure your sleep timer duration (from 1 minute up to 24 hours), show notification preference, and wake-up goal settings on the main screen. Each input and toggle includes a clear description explaining its effect.
3. Start playing your podcast, music, or video app. The timer automatically begins counting down.
4. If you're still awake, press your phone's volume buttons or flip your phone over at any time to reset the timer to full duration. Resetting the sleep timer also pushes any active nap alarm forward by the same duration increment.
5. Tap **"Nap"** on the main screen or notification shade to launch a quick nap alarm dialog, or **"Cancel Nap"** to cancel an active nap.
6. Tap **"Disable"** or **"Enable"** in the notification shade to toggle the sleep timer at any time.
7. Access action links at the bottom of the main screen (**Manual**, **Logs**, **Feedback**, **Donate**, **Export**, and **Import**) arranged inline with middle dots.

### Smart Wake-Up Goal ("Auto Sleep")

1. Enable the **Target wake-up goal** switch on the main screen.
2. Pick your desired target wake-up goal time (e.g., `06:30 AM`) and set your minimum required sleep duration (e.g., `7h 30m`).
3. When you start your sleep timer, Auto Sleep Droid calculates and schedules your wake-up alarm automatically.
4. When the alarm rings or is snoozed, flip your phone over or tap **"Snooze"** on the notification to snooze for 9 minutes. The wake-up alarm notification remains open in the notification shade so you can dismiss the alarm whenever you want. Pressing any physical volume button or tapping **"Dismiss"** on the notification dismisses the alarm.
5. Toggle off the wake-up goal switch on the main screen whenever you want to disable the wake-up alarm goal feature.

> **Note:** Auto Sleep Droid is not a conventional alarm clock. Going to bed late will push the alarm till later to safeguard your minimum sleep duration, which might overshoot your required wake-up time. It is suggested to keep using a normal alarm and use Auto Sleep Droid to help you wake up at a time before your actual alarm goes off.

## Permissions Used

Auto Sleep Droid uses minimal permissions required to function reliably as a background sleep timer:

- **Notifications (`POST_NOTIFICATIONS`)** *(Required)*: Displays ongoing timer status and single-tap controls in your notification shade, ensuring Android keeps the foreground timer service active and does not kill or put the app to sleep.
- **Foreground Service (`FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_MEDIA_PLAYBACK`)** *(Required)*: Keeps the timer service running reliably in the background while media plays.
- **Audio Settings (`MODIFY_AUDIO_SETTINGS`)** *(Required)*: Fades music volume down to zero at expiry and restores pre-fade volume after pausing.
- **Vibration (`VIBRATE`)** *(Optional)*: Provides faint haptic feedback confirming your actions (turning off/on, volume button resets, and phone flips).
- **Alarms & Reminders (`SCHEDULE_EXACT_ALARM` & `SET_ALARM`)** *(Required)*: Schedules exact backup alarms so the timer expires on time and wake alarms ring reliably even when Android enters Doze mode or battery saver.
- **Run at Startup (`RECEIVE_BOOT_COMPLETED`)** *(Optional)*: Restores your timer state automatically when your device reboots.
- **Health Connect Write Sleep (`android.permission.health.WRITE_SLEEP`)** *(Optional)*: Persists sleep, nap, and wake times as sleep sessions to Android Health Connect when Health Connect synchronization is enabled.

## Donate

If you find Auto Sleep Droid helpful, you can support development via [Liberapay](https://liberapay.com/bas080).

## Issues

You can report bugs, request features, or view open issues on [GitHub Issues](https://github.com/bas080/auto-sleep-droid/issues). For developer and build instructions, see [AGENTS.md](AGENTS.md).
