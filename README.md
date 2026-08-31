# Auto Sleep Droid

Auto Sleep Droid is a simple Android sleep timer for media playback.

## Overview

- **Never be startled awake:** Fall asleep peacefully to podcasts, YouTube playlists, audiobooks, or music without waking up hours later to blaring audio or autoplay videos.
- **Universal compatibility:** Controls playback via standard Android system media controls, making it compatible with YouTube, Spotify, podcast players, browsers, and most audio or video apps.
- **Gentle volume fade:** Volume slowly fades down before pausing playback so you aren't jolted awake by abrupt silence.
- **Screen-free resets:** Simply press a physical volume button or flip your phone over while in bed to reset the timer to full duration without looking at a bright screen.
- **Smart Wake-Up Goal:** Set your target wake-up time and minimum required sleep duration. The app automatically calculates and schedules your wake-up alarm whenever you start your sleep timer, ensuring you always get enough rest.

## How It Works

1. **Set your timer:** Enter a duration directly from the notification shade.
2. **Automatic detection:** Sits passively until you start playing media, then automatically begins counting down.
3. **Fade & pause:** At expiry, volume fades down over 30 seconds, media playback pauses via system media controls, and original volume is restored.
4. **Smart wake-up alarm:** If enabled, the app automatically calculates and sets your wake-up alarm for your target time (or later if you went to bed late) to protect your minimum sleep duration.

> **Note on Naps:** The sleep timer does not have specific use cases for naps—just use your device's alarm or timer app. However, if you decide to listen to something when going for a nap, Auto Sleep Droid will still fade out your audio.

## Usage Instructions

1. Launch Auto Sleep Droid and grant notification access when prompted.
2. Open the Auto Sleep Droid notification to set or control the timer.
3. Tap **"Sleep 20m"** (or **"Set Timer"**) to enter your desired sleep duration in minutes (from 1 minute up to 24 hours).
4. Start playing your podcast, music, or video app. The timer automatically begins counting down.
5. If you're still awake, press your phone's volume buttons or flip your phone over at any time to reset the timer to full duration.
6. Tap **"Sleep <duration>"** in the notification whenever you want to turn off the sleep timer. Tapping it while disabled opens inline edit or input.

### Smart Wake-Up Goal ("Auto Sleep")

1. Tap **"Set Goal"** (or **"Alarm HH:MM"**) in the notification shade.
2. Pick your target wake-up goal time (e.g., `06:30 AM`) and set your minimum required sleep duration in hours (default 7.5 hours).
3. Tap **"OK"** to enable. When you start your sleep timer, Auto Sleep Droid calculates and schedules your wake-up alarm automatically.
4. When the alarm rings, flip your phone over or tap **"Snooze"** on the notification to snooze for 9 minutes. Pressing a physical volume button or tapping **"Dismiss"** on the notification shade dismisses the alarm.
5. Tap **"Alarm HH:MM"** in the notification whenever you want to disable the wake-up alarm goal feature.

> **Note:** Auto Sleep Droid is not a conventional alarm clock. Going to bed late will push the alarm till later to safeguard your minimum sleep duration, which might overshoot your required wake-up time. It is suggested to keep using a normal alarm and use Auto Sleep Droid to help you wake up at a time before your actual alarm goes off.

## Permissions Used

Auto Sleep Droid uses minimal permissions required to function reliably as a background sleep timer:

- **Notifications (`POST_NOTIFICATIONS`)**: Displays live timer status and controls (Set Timer, Goal Settings) directly in your notification shade.
- **Foreground Service (`FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_MEDIA_PLAYBACK`)**: Keeps the timer service running reliably in the background while media plays.
- **Audio Settings (`MODIFY_AUDIO_SETTINGS`)**: Fades music volume down to zero at expiry and restores pre-fade volume after pausing.
- **Vibration (`VIBRATE`)**: Provides faint haptic feedback confirming your actions (setting timer, turning off, volume button resets, and phone flips).
- **Alarms & Reminders (`SCHEDULE_EXACT_ALARM`)**: Schedules exact backup alarms so the timer expires on time even when Android enters Doze mode or battery saver.
- **Run at Startup (`RECEIVE_BOOT_COMPLETED`)**: Restores your timer state automatically when your device reboots.

## Donate

If you find Auto Sleep Droid helpful, you can support development via [Liberapay](https://liberapay.com/bas080).

## Issues

You can report bugs, request features, or view open issues on [GitHub Issues](https://github.com/bas080/auto-sleep-droid/issues). For developer and build instructions, see [AGENTS.md](AGENTS.md).
