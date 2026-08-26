package com.bas080.autosleepdroid;

public class SleepTimerStateMachine {

    public enum State {
        OFF,
        WAITING,
        ACTIVE,
        FADING
    }

    public interface Callback {
        void onStateChanged(State newState);
        void onSetStreamVolume(int volume);
        void onScheduleAlarm(long triggerAtMillis);
        void onCancelAlarm();
        void onPauseMedia();
        void onTriggerVibration();
        void onPersistState(boolean enabled, int durationMinutes, long timerEndsAt);
        void onUpdateNotification();
        void onTimerRescheduled();
    }

    public static final long FADE_DURATION_MS = 30_000L;
    public static final long FADE_STEP_INTERVAL_MS = 1_000L;
    public static final int TOTAL_FADE_STEPS = (int) (FADE_DURATION_MS / FADE_STEP_INTERVAL_MS);
    public static final int DEFAULT_DURATION_MINUTES = 20;
    public static final int MINUTES_MIN = 1;
    public static final int MINUTES_MAX = 24 * 60;

    private State state = State.OFF;
    private int configuredDurationMinutes = DEFAULT_DURATION_MINUTES;
    private long timerEndsAt = 0L;
    private int volumeBeforeFade = 0;
    private int fadeStep = 0;
    private int lastFadeVolume = 0;
    private int lastObservedVolume = 0;
    private boolean lastObservedMediaActive = false;
    private boolean suppressVolumeReset = false;

    private Callback callback;

    public SleepTimerStateMachine(Callback callback) {
        this.callback = callback;
    }

    public State getState() {
        return state;
    }

    public int getConfiguredDurationMinutes() {
        return configuredDurationMinutes;
    }

    public long getTimerEndsAt() {
        return timerEndsAt;
    }

    public boolean isEnabled() {
        return state != State.OFF;
    }

    public boolean isActive() {
        return state == State.ACTIVE || state == State.FADING;
    }

    public boolean isFading() {
        return state == State.FADING;
    }

    public int getLastObservedVolume() {
        return lastObservedVolume;
    }

    public static boolean isValidDuration(int minutes) {
        return minutes >= MINUTES_MIN && minutes <= MINUTES_MAX;
    }

    public void initialize(boolean savedEnabled, int savedDurationMinutes, long savedEndsAt, int initialVolume, boolean musicActive, long now) {
        this.configuredDurationMinutes = isValidDuration(savedDurationMinutes) ? savedDurationMinutes : DEFAULT_DURATION_MINUTES;
        this.lastObservedVolume = initialVolume;
        this.lastObservedMediaActive = false;

        if (savedEnabled && savedEndsAt > now) {
            startTimer(configuredDurationMinutes, savedEndsAt, now, false);
        } else if (savedEnabled && savedEndsAt > 0L && savedEndsAt <= now) {
            beginFadeOut(initialVolume);
        } else if (savedEnabled && musicActive) {
            startTimer(configuredDurationMinutes, now + configuredDurationMinutes * 60_000L, now, true);
        } else if (savedEnabled) {
            transitionTo(State.WAITING);
        } else {
            transitionTo(State.OFF);
        }
    }

    private void transitionTo(State newState) {
        this.state = newState;
        if (callback != null) {
            callback.onStateChanged(newState);
        }
    }

    public void handleTurnOff(boolean triggerVibration) {
        if (triggerVibration && callback != null) {
            callback.onTriggerVibration();
        }
        timerEndsAt = 0L;
        if (callback != null) {
            callback.onCancelAlarm();
            callback.onPersistState(false, configuredDurationMinutes, 0L);
        }
        transitionTo(State.OFF);
    }

    public void handleTurnOn(boolean musicActive, long now, boolean triggerVibration) {
        if (triggerVibration && callback != null) {
            callback.onTriggerVibration();
        }
        if (callback != null) {
            callback.onPersistState(true, configuredDurationMinutes, timerEndsAt);
        }
        if (musicActive) {
            startTimer(configuredDurationMinutes, now + configuredDurationMinutes * 60_000L, now, true);
        } else {
            transitionTo(State.WAITING);
        }
    }

    public void handleDurationReply(int duration, boolean musicActive, long now, boolean triggerVibration) {
        if (triggerVibration && callback != null) {
            callback.onTriggerVibration();
        }

        if (isValidDuration(duration)) {
            configuredDurationMinutes = duration;
        } else if (!isValidDuration(configuredDurationMinutes)) {
            configuredDurationMinutes = DEFAULT_DURATION_MINUTES;
        }

        if (musicActive) {
            startTimer(configuredDurationMinutes, now + configuredDurationMinutes * 60_000L, now, true);
        } else {
            if (callback != null) {
                callback.onPersistState(true, configuredDurationMinutes, 0L);
            }
            transitionTo(State.WAITING);
        }
    }

    public void startTimer(int durationMinutes, long endsAt, long now, boolean persist) {
        if (callback != null) {
            callback.onCancelAlarm();
        }
        boolean wasActive = state == State.ACTIVE;
        configuredDurationMinutes = isValidDuration(durationMinutes) ? durationMinutes : DEFAULT_DURATION_MINUTES;
        timerEndsAt = endsAt;
        if (persist && callback != null) {
            callback.onPersistState(true, configuredDurationMinutes, timerEndsAt);
        }
        if (callback != null) {
            callback.onScheduleAlarm(timerEndsAt);
        }
        if (wasActive) {
            if (callback != null) {
                callback.onTimerRescheduled();
                callback.onUpdateNotification();
            }
        } else {
            transitionTo(State.ACTIVE);
        }
    }

    public void handleAlarmExpiry(int currentVolume) {
        handleAlarmExpiry(currentVolume, System.currentTimeMillis());
    }

    public void handleAlarmExpiry(int currentVolume, long now) {
        if (isEnabled() && state == State.ACTIVE) {
            if (timerEndsAt > 0L && now < timerEndsAt - 1000L) {
                return;
            }
            beginFadeOut(currentVolume);
        }
    }

    public void beginFadeOut(int currentVolume) {
        if (!isEnabled() || state == State.FADING) {
            return;
        }
        volumeBeforeFade = currentVolume;
        lastFadeVolume = currentVolume;
        lastObservedVolume = currentVolume;
        fadeStep = 0;
        transitionTo(State.FADING);
    }

    public boolean runFadeStep(int currentVolume, boolean flipDetected) {
        if (state != State.FADING) {
            return false;
        }

        if (flipDetected) {
            cancelFadeForFlip();
            return false;
        }

        if (currentVolume != lastFadeVolume) {
            cancelFadeForVolumeChange(currentVolume);
            return false;
        }

        fadeStep++;
        int targetVolume = 0;
        float progress = (float) fadeStep / TOTAL_FADE_STEPS;
        float fraction = 1.0f - (1.0f - progress) * (1.0f - progress);
        int nextVolume = Math.round(volumeBeforeFade - (volumeBeforeFade - targetVolume) * fraction);

        lastFadeVolume = nextVolume;
        lastObservedVolume = nextVolume;

        suppressVolumeReset = true;
        if (callback != null) {
            callback.onSetStreamVolume(nextVolume);
        }
        suppressVolumeReset = false;

        if (fadeStep >= TOTAL_FADE_STEPS) {
            finishExpiry();
            return false;
        }
        return true;
    }

    public void finishExpiry() {
        if (callback != null) {
            callback.onPauseMedia();
        }
    }

    public void restoreVolumeAfterPause() {
        if (callback != null) {
            suppressVolumeReset = true;
            callback.onSetStreamVolume(volumeBeforeFade);
            suppressVolumeReset = false;
        }
        lastObservedVolume = volumeBeforeFade;
        if (callback != null) {
            callback.onPersistState(true, configuredDurationMinutes, 0L);
        }
        EventLogger.log("Pre-fade volume restored");
        transitionTo(State.WAITING);
    }

    public void cancelFadeForVolumeChange(int currentVolume) {
        if (callback != null) {
            callback.onTriggerVibration();
            callback.onCancelAlarm();
        }
        lastObservedVolume = currentVolume;
        EventLogger.log("Volume changed during fade: timer reset (" + configuredDurationMinutes + "m)");
        if (isValidDuration(configuredDurationMinutes)) {
            startTimer(configuredDurationMinutes, System.currentTimeMillis() + configuredDurationMinutes * 60_000L, System.currentTimeMillis(), true);
        } else {
            transitionTo(State.WAITING);
        }
    }

    public void cancelFadeForFlip() {
        if (callback != null) {
            callback.onTriggerVibration();
            callback.onCancelAlarm();
            suppressVolumeReset = true;
            callback.onSetStreamVolume(volumeBeforeFade);
            suppressVolumeReset = false;
        }
        lastObservedVolume = volumeBeforeFade;
        EventLogger.log("Phone flipped during fade: timer reset (" + configuredDurationMinutes + "m)");
        if (isValidDuration(configuredDurationMinutes)) {
            startTimer(configuredDurationMinutes, System.currentTimeMillis() + configuredDurationMinutes * 60_000L, System.currentTimeMillis(), true);
        } else {
            transitionTo(State.WAITING);
        }
    }

    public void onPlaybackStateChanged(boolean musicActive, long now) {
        boolean playbackStopped = !musicActive && lastObservedMediaActive;
        lastObservedMediaActive = musicActive;

        if (isEnabled()) {
            if (state == State.WAITING && musicActive) {
                EventLogger.log("Music started: timer started (" + configuredDurationMinutes + "m)");
                startTimer(configuredDurationMinutes, now + configuredDurationMinutes * 60_000L, now, true);
            } else if (state == State.ACTIVE && playbackStopped) {
                if (callback != null) {
                    callback.onCancelAlarm();
                }
                EventLogger.log("Music stopped: timer paused");
                transitionTo(State.WAITING);
            }
        }
    }

    public void onVolumeChanged(int currentVolume, long now) {
        if (suppressVolumeReset || !isActive()) {
            lastObservedVolume = currentVolume;
            return;
        }

        int expectedVolume = state == State.FADING ? lastFadeVolume : lastObservedVolume;
        boolean volumeChanged = currentVolume != expectedVolume;
        lastObservedVolume = currentVolume;

        if (volumeChanged) {
            if (state == State.FADING) {
                cancelFadeForVolumeChange(currentVolume);
            } else if (state == State.ACTIVE) {
                EventLogger.log("Volume changed: timer reset (" + configuredDurationMinutes + "m)");
                resetTimer(now);
            }
        }
    }

    public void onPhoneFlipped(long now) {
        if (!isActive()) {
            return;
        }

        if (state == State.FADING) {
            cancelFadeForFlip();
        } else if (state == State.ACTIVE) {
            EventLogger.log("Phone flipped: timer reset (" + configuredDurationMinutes + "m)");
            resetTimer(now);
        }
    }

    public void pollInputs(int currentVolume, boolean musicActive, boolean flipDetected, long now) {
        if (musicActive != lastObservedMediaActive) {
            onPlaybackStateChanged(musicActive, now);
        }
        if (flipDetected) {
            onPhoneFlipped(now);
        }
        int expectedVolume = state == State.FADING ? lastFadeVolume : lastObservedVolume;
        if (currentVolume != expectedVolume) {
            onVolumeChanged(currentVolume, now);
        }

        if (isEnabled() && state == State.ACTIVE && timerEndsAt > 0L && now >= timerEndsAt) {
            beginFadeOut(currentVolume);
        }
    }

    private void resetTimer(long now) {
        if (state != State.FADING && isValidDuration(configuredDurationMinutes)) {
            if (callback != null) {
                callback.onTriggerVibration();
            }
            startTimer(configuredDurationMinutes, now + configuredDurationMinutes * 60_000L, now, true);
        }
    }
}
