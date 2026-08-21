# auto-sleep-droid

Auto Sleep Droid is a simple Android sleep timer for media playback. It works entirely from the notification shade and has no separate app screen.

## How to use it

1. Open the Auto Sleep Droid notification.
2. Turn the sleep timer on.
3. Enter the duration in minutes using the inline notification reply.
4. Leave the notification active while you listen.

The duration can be between 1 minute and 24 hours. The notification stays visible while the timer is active and cannot be dismissed.

For the timer to pause all active media apps when it expires, grant Auto Sleep Droid notification access in Android Settings. The app does not show a custom settings screen.

## During the timer

- Press volume up or volume down as usual. The volume changes normally, and the timer starts over at the original duration.
- Turn the timer off from the notification at any time. Media keeps playing and the current volume is left unchanged.

## When time runs out

The volume fades out over 15 seconds. All active media playback is then paused, and the volume is restored to the level it had before the fade-out began.

## After a reboot

Auto Sleep Droid remembers whether the timer was on or off. If it was on, the timer starts again from the full configured duration. If it was off, it remains off.

For the complete product specification, see [SPEC.md](SPEC.md).

## Building

Open this project in Android Studio with an Android SDK installed, then build the `app` module. From a terminal with `ANDROID_HOME` or `ANDROID_SDK_ROOT` configured, run:

```text
gradle assembleDebug
```
