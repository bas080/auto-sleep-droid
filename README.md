# auto-sleep-droid

Auto Sleep Droid is a simple Android sleep timer for media playback. It works entirely from the notification shade and has no separate app screen.

## How to use it

1. Launch Auto Sleep Droid and grant notification access when Android opens Settings.
2. Open the Auto Sleep Droid notification.
3. Enter or change the duration in minutes using the inline notification reply.
4. Leave the notification active while you listen.

The duration can be between 1 minute and 24 hours. The default duration is 20 minutes. The notification reply suggests the default or your last configured duration, which you can change before submitting. Notification access is required before the timer can operate, because it is used to pause active media at expiry. The notification stays visible and cannot be dismissed.

Notification access is requested through Android Settings before the timer notification is created. It is required to pause all active media apps when the timer expires. The app does not show a custom settings screen.

## During the timer

- Press volume up or volume down as usual. The volume changes normally, and the timer starts over at the original duration.
- Once a duration has been configured, changing the volume while the timer is off starts the timer automatically. The polling implementation checks for this once per minute.
- Starting media playback also starts the timer automatically. Playback state is checked once per minute.
- If you change the volume while the fade-out is running, the fade-out is cancelled, your new volume is kept, and the timer starts over.
- After expiry, the notification says the timer is waiting and shows the configured duration. It starts again when media plays or the volume changes.

## When time runs out

The current volume fades down to halfway to zero over 15 seconds. All active media playback is then paused, and the volume is restored to the level it had before the fade-out began.

## After a reboot

Auto Sleep Droid keeps your configured duration across reboots. If a countdown was active, it starts again from the full configured duration. Otherwise, the notification returns to its waiting state and starts a new countdown when media plays or the volume changes.

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

After installation, launch the app once. Grant notification permission and notification access when Android asks; both are required before the timer notification and timer controls become available.

The app intentionally has no custom settings or timer screen. Changes to timer behavior belong in `SleepTimerService`, reboot restoration belongs in `BootReceiver`, and media-session control belongs in `MediaSessionAccessService`.

### Testing notes

Manual testing requires a connected Android device or emulator because the development container does not provide one. Verify notification permission, notification access, duration validation, volume-button resets, 15-second fade-out, media pause, volume restoration, timer disable behavior, and reboot persistence.

The debug build is the primary automated validation command. Android lint may require a JDK version supported by the installed Android lint tooling.
