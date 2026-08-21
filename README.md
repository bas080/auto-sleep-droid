# auto-sleep-droid

Auto Sleep Droid is a simple Android sleep timer for media playback. It works entirely from the notification shade and has no separate app screen.

## How to use it

1. Open the Auto Sleep Droid notification.
2. Turn the sleep timer on.
3. Enter the duration in minutes using the inline notification reply.
4. Leave the notification active while you listen.

The duration can be between 1 minute and 24 hours. The default duration is 20 minutes. The notification reply suggests the default or your last configured duration, which you can change before submitting. The notification stays visible while the timer is active and cannot be dismissed.

For the timer to pause all active media apps when it expires, tap `Allow media control` in the notification and enable Auto Sleep Droid in Android's notification-access settings. The app does not show a custom settings screen.

## During the timer

- Press volume up or volume down as usual. The volume changes normally, and the timer starts over at the original duration.
- Once a duration has been configured, changing the volume while the timer is off starts the timer automatically.
- Starting media playback also starts the timer automatically when notification access is enabled.
- If you change the volume while the fade-out is running, the fade-out is cancelled, your new volume is kept, and the timer starts over.
- Turn the timer off from the notification at any time. Media keeps playing and the current volume is left unchanged.

## When time runs out

The current volume fades down to halfway to zero over 15 seconds. All active media playback is then paused, and the volume is restored to the level it had before the fade-out began.

## After a reboot

Auto Sleep Droid remembers whether the timer was on or off. If it was on, the timer starts again from the full configured duration. If it was off, it remains off.

## Project documentation

- [SPEC.md](SPEC.md): product requirements and acceptance criteria.
- [IMPLEMENTATION.md](IMPLEMENTATION.md): architecture, runtime flows, persistence, permissions, build/release details, and guidance for future developers and AI agents.

## Building

Open this project in Android Studio with an Android SDK installed, then build the `app` module. From a terminal with `ANDROID_HOME` or `ANDROID_SDK_ROOT` configured, run:

```sh
./gradlew assembleDebug
```

## Developer instructions

### Prerequisites

- Android Studio or a JDK 17+ installation.
- Android SDK Platform 35 and Build Tools 35.0.0.
- An Android device or emulator running Android 8.0 (API 26) or newer for manual testing.
- Enable USB debugging when testing on a physical device.

If the SDK is not discovered automatically, create a local, untracked `local.properties` file with:

```properties
sdk.dir=/path/to/android-sdk
```

### Common commands

```sh
# Build the debug APK
./gradlew assembleDebug

# Install it on a connected device or emulator
./gradlew installDebug

# Remove generated build output
./gradlew clean
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Runtime setup

After installation, launch the app once to create the ongoing notification and grant notification permission when Android asks. Use the notification's `Allow media control` action to grant notification access; this is required to pause all active media sessions.

The app intentionally has no custom settings or timer screen. Changes to timer behavior belong in `SleepTimerService`, reboot restoration belongs in `BootReceiver`, and media-session control belongs in `MediaControlNotificationListener`.

### Testing notes

Manual testing requires a connected Android device or emulator because the development container does not provide one. Verify notification permission, notification access, duration validation, volume-button resets, 15-second fade-out, media pause, volume restoration, timer disable behavior, and reboot persistence.

The debug build is the primary automated validation command. Android lint may require a JDK version supported by the installed Android lint tooling.
