# Auto Sleep Droid

Auto Sleep Droid is a simple, smart Android sleep timer for media playback.

## Why Auto Sleep Droid?

- **Never be startled awake:** Fall asleep peacefully to your favorite podcasts, YouTube playlists, audiobooks, or music without waking up hours later to blaring audio or unexpected autoplay videos.
- **Works with almost any app:** Uses Android's universal media controls to pause playback, making it compatible with YouTube, Spotify, podcast players, web browsers, and most audio or video apps.
- **Gentle sleep transition:** When time runs out, the volume slowly fades down before pausing playback, preventing abrupt silence from waking you up.
- **Screen-free resets:** If you're still awake, simply press a volume button on your phone to reset the timer back to full duration without opening your phone or staring at a bright screen.

## How Simply It Works

1. **Set your timer:** Enter your desired sleep duration directly from the notification shade.
2. **Automatic playback detection:** The timer waits passively and starts counting down automatically as soon as you start playing media.
3. **Gentle fade & pause:** At expiry, volume fades halfway down over 15 seconds, media playback is paused via system media controls, and your original volume level is restored.

## How to use it

1. Launch Auto Sleep Droid and grant notification access when prompted or by tapping the "Setup required" notification.
2. Open the main app screen to view a real-time event log for debugging.
3. Open the Auto Sleep Droid notification to set or control the timer.
4. Enter or change the duration in minutes using the **"Set Timer"** inline reply (a numeric keypad is automatically presented).
5. Tap **"Turn Off"** at any time to disable the timer.

The duration can be between 1 minute and 24 hours. The default duration is 20 minutes. The notification reply suggests the default or your last configured duration. If an invalid duration is entered, the app gracefully falls back to the last valid or default duration. Notification access is required before the timer can control playback and pause media at expiry. The notification stays visible and cannot be dismissed.

## System states

- **Permissions Pending:** Initial state when setup is required. Tap the notification to open System Settings and grant notification access.
- **Off:** The sleep timer is disabled ("Sleep timer is off"). Media continues playing and volume remains unchanged. Tap "Set Timer" to enable and configure a duration.
- **Waiting:** Duration is configured ("Waiting for media playback"). Sits passively listening for playback.
- **Active:** Triggered when media playback starts ("Timer running"). Counts down from the configured duration.
- **Fading:** Timer reaches zero ("Fading volume"). Fades volume halfway over 15 seconds, pauses media, restores pre-fade volume, and reverts to Waiting.

## During the timer

- Press volume up or volume down as usual. The volume changes normally, and an active or fading timer resets to the full configured duration.
- Starting media playback automatically transitions the timer from Waiting to Active.
- Tap **"Turn Off"** to disable the timer without changing volume or pausing playback.

## When time runs out

The current volume fades down to halfway to zero over 15 seconds. All active media playback is then paused, volume is restored to pre-fade level, and the timer returns to the Waiting state.

## After a reboot

Auto Sleep Droid remembers whether the timer was running (Waiting, Active, Fading) vs explicitly Off. If it was running, it restores to the Waiting state with the configured duration. If it was Off, it remains Off.

## Event log UI

The main activity UI prints a list of timestamped application events (one per line) for debugging. Recorded events include activity and service lifecycle transitions, timer configuration and state changes, volume and playback status updates, and boot events.

## Issues

You can report new bugs, request features, or find existing open issues on the project's [GitHub Issues](https://github.com/bas080/auto-sleep-droid/issues) page.

## Project documentation

- [SPEC.md](SPEC.md): product requirements and acceptance criteria.
- [IMPLEMENTATION.md](IMPLEMENTATION.md): architecture, runtime flows, persistence, permissions, build/release details, and guidance for future developers and AI agents.

## Building

From a terminal with `ANDROID_HOME` or `ANDROID_SDK_ROOT` configured, run:

```sh
./gradlew assembleDebug
```

## Developer instructions

### Prerequisites

- JDK 17+ installation.
- Android SDK Platform 35 and Build Tools 35.0.0.

### Common commands

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

### Creating a new release

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
