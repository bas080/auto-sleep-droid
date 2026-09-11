# User Persona and Needs

This document describes the target user persona, pain points, and product needs for Auto Sleep Droid.

## Target User Persona: "The Nighttime Media Listener"
- **Profile**: Listens to podcasts, music, audiobooks, or videos in bed to fall asleep.
- **Goal**: Wants media playback to pause automatically after falling asleep without having to manually turn off media or wake up in the middle of the night to turn off a blaring phone.
- **Key Pain Points**:
  - Waking up hours later with media still playing and battery drained.
  - Abrupt audio stops waking them up right as they drift off to sleep.
  - Complex sleep timer apps with bloated features, invasive pop-ups, ads, or difficult screen controls in the dark.
  - Waking up groggy because an alarm went off too early after getting to sleep late.

## Solution & Key Needs

1. **Low-Friction Sleep Timer**:
   - Single-screen main configuration.
   - Screen-free resets via physical volume buttons in bed without looking at the screen.
   - Smooth 30-second volume fade before pausing media so playback stops imperceptibly.

2. **Smart Wake-Up Safeguard**:
   - Daily wake alarms with automatic minimum sleep duration safeguards.
   - Hardware volume button snoozes wake alarms for 9 minutes.
   - Tapping "I'm Awake" is the sole action to stop alarms, adjusting current wake time to 15 minutes before current time (capped at target goal time).
   - "I'm Awake" action button available during awake window (`currentWakeTime +/- (minSleepDuration / 2)`) or when alarms ring/snooze.

3. **Privacy & Offline Integration**:
   - 100% offline functionality.
   - Optional Health Connect integration to log sleep sessions.
