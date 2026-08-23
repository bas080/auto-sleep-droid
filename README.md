# Auto Sleep Droid

Auto Sleep Droid is a simple Android sleep timer for media playback.

## Overview

- **Never be startled awake:** Fall asleep peacefully to podcasts, YouTube playlists, audiobooks, or music without waking up hours later to blaring audio or autoplay videos.
- **Universal compatibility:** Controls playback via standard Android system media controls, making it compatible with YouTube, Spotify, podcast players, browsers, and most audio or video apps.
- **Gentle volume fade:** Volume slowly fades down before pausing playback so you aren't jolted awake by abrupt silence.
- **Screen-free resets:** Simply press a physical volume button or flip your phone over while in bed to reset the timer to full duration without looking at a bright screen.

## How It Works

1. **Set your timer:** Enter a duration directly from the notification shade.
2. **Automatic detection:** Sits passively until you start playing media, then automatically begins counting down.
3. **Fade & pause:** At expiry, volume fades halfway down over 30 seconds, media playback pauses via system media controls, and original volume is restored.

## Permissions Used

Auto Sleep Droid uses minimal permissions required to function reliably as a background sleep timer:

- **Notifications (`POST_NOTIFICATIONS`)**: Displays live timer status and controls (Set Timer, Turn Off/On) directly in your notification shade.
- **Foreground Service (`FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_MEDIA_PLAYBACK`)**: Keeps the timer service running reliably in the background while media plays.
- **Audio Settings (`MODIFY_AUDIO_SETTINGS`)**: Fades music volume down to zero at expiry and restores pre-fade volume after pausing.
- **Vibration (`VIBRATE`)**: Provides faint haptic feedback confirming your actions (setting timer, turning off, volume button resets, and phone flips).
- **Alarms & Reminders (`SCHEDULE_EXACT_ALARM`)**: Schedules exact backup alarms so the timer expires on time even when Android enters Doze mode or battery saver.
- **Run at Startup (`RECEIVE_BOOT_COMPLETED`)**: Restores your timer state automatically when your device reboots.

## Usage Instructions

1. Launch Auto Sleep Droid and grant notification access when prompted.
2. Open the Auto Sleep Droid notification to set or control the timer.
3. Tap **"Set Timer"** and enter your desired duration in minutes (between 1 minute and 24 hours, default 20 minutes).
4. Start playing your podcast, music, or video app. The timer automatically begins counting down.
5. If you're still awake, press your phone's volume buttons or flip your phone over at any time to reset the timer to full duration.
6. Tap **"Turn Off"** in the notification whenever you want to disable the sleep timer.

## Donate

If you find Auto Sleep Droid helpful, you can support development via [Liberapay](https://liberapay.com/bas080).

## Issues

You can report new bugs, request features, or find existing open issues on the project's [GitHub Issues](https://github.com/bas080/auto-sleep-droid/issues) page.

## Building

From a terminal with `ANDROID_HOME` or `ANDROID_SDK_ROOT` configured, run:

```sh
./gradlew assembleDebug
```

## Developer Instructions

### Prerequisites

- JDK 17+ installation.
- Android SDK Platform 35 and Build Tools 35.0.0.

### Common Commands

```sh
# Run unit tests
./gradlew test

# Build the debug APK
./gradlew assembleDebug

# Install it on a connected device or emulator
./gradlew installDebug

# Remove generated build output
./gradlew clean
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Creating a New Release

To publish a new release of the app, run the release script with the target version name:

```sh
./scripts/release.sh 0.2
```

The script automatically:
1. Increments `versionCode` in `app/build.gradle` and updates `versionName`.
2. Commits the changes and creates a Git tag `v0.2`.

After running the script, push the commit and tag to trigger the automated GitHub Actions release build:

```sh
git push origin master
git push origin v0.2
```

Pushing a tag matching `v*` triggers the automated GitHub Actions release workflow (`.github/workflows/android-release.yml`), which runs unit tests, builds the versioned APK, and creates a new GitHub Release. Release notes and changelogs are published automatically on the [GitHub Releases](https://github.com/bas080/auto-sleep-droid/releases) page.
