package com.bas080.autosleepdroid;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SleepTimerStateMachineTest {

    private SleepTimerStateMachine stateMachine;
    private TestCallback callback;

    private static class TestCallback implements SleepTimerStateMachine.Callback {
        SleepTimerStateMachine.State lastState;
        int lastSetVolume = -1;
        long scheduledAlarmAt = -1L;
        boolean alarmCancelled = false;
        boolean mediaPaused = false;
        boolean vibrationTriggered = false;
        boolean persistedEnabled = false;
        int persistedDuration = -1;
        long persistedEndsAt = -1L;
        int notificationUpdateCount = 0;

        @Override
        public void onStateChanged(SleepTimerStateMachine.State newState) {
            this.lastState = newState;
        }

        @Override
        public void onSetStreamVolume(int volume) {
            this.lastSetVolume = volume;
        }

        @Override
        public void onScheduleAlarm(long triggerAtMillis) {
            this.scheduledAlarmAt = triggerAtMillis;
        }

        @Override
        public void onCancelAlarm() {
            this.alarmCancelled = true;
        }

        @Override
        public void onPauseMedia() {
            this.mediaPaused = true;
        }

        @Override
        public void onTriggerVibration() {
            this.vibrationTriggered = true;
        }

        @Override
        public void onPersistState(boolean enabled, int durationMinutes, long timerEndsAt) {
            this.persistedEnabled = enabled;
            this.persistedDuration = durationMinutes;
            this.persistedEndsAt = timerEndsAt;
        }

        @Override
        public void onUpdateNotification() {
            this.notificationUpdateCount++;
        }

        @Override
        public void onTimerRescheduled() {
        }
    }

    @Before
    public void setUp() {
        callback = new TestCallback();
        stateMachine = new SleepTimerStateMachine(callback);
    }

    @Test
    public void testInitializationActiveTimer() {
        long now = System.currentTimeMillis();
        stateMachine.initialize(true, 30, now + 60000L, 10, false, now);

        assertEquals(SleepTimerStateMachine.State.ACTIVE, stateMachine.getState());
        assertEquals(30, stateMachine.getConfiguredDurationMinutes());
        assertTrue(stateMachine.isActive());
    }

    @Test
    public void testTurnOffAction() {
        long now = System.currentTimeMillis();
        stateMachine.initialize(true, 20, now + 60000L, 10, false, now);

        stateMachine.handleTurnOff(true);

        assertEquals(SleepTimerStateMachine.State.OFF, stateMachine.getState());
        assertFalse(stateMachine.isEnabled());
        assertTrue(callback.vibrationTriggered);
        assertTrue(callback.alarmCancelled);
        assertFalse(callback.persistedEnabled);
    }

    @Test
    public void testTurnOnAction() {
        long now = System.currentTimeMillis();
        stateMachine.initialize(false, 20, 0L, 10, true, now);

        stateMachine.handleTurnOn(true, now, true);

        assertEquals(SleepTimerStateMachine.State.ACTIVE, stateMachine.getState());
        assertTrue(stateMachine.isEnabled());
        assertTrue(callback.vibrationTriggered);
        assertTrue(callback.persistedEnabled);
    }

    @Test
    public void testFadeStepVolumeStateConsistency() {
        long now = System.currentTimeMillis();
        stateMachine.initialize(true, 10, now + 60000L, 10, true, now);
        stateMachine.beginFadeOut(10);

        assertTrue(stateMachine.isFading());

        // Execute fade step
        boolean continues = stateMachine.runFadeStep(10, false);

        assertTrue(continues);

        // Immediately poll inputs after fade step (current volume matches lastFadeVolume)
        stateMachine.pollInputs(callback.lastSetVolume, true, false, now);

        assertTrue(stateMachine.isFading());
    }

    @Test
    public void testManualVolumeChangeCancelsFade() {
        long now = System.currentTimeMillis();
        stateMachine.initialize(true, 10, now + 60000L, 10, true, now);
        stateMachine.beginFadeOut(10);

        assertTrue(stateMachine.isFading());

        // Execute fade step
        stateMachine.runFadeStep(10, false);

        // Manual volume button pressed by user changing volume to 15
        stateMachine.pollInputs(15, true, false, now);

        assertEquals(SleepTimerStateMachine.State.ACTIVE, stateMachine.getState());
        assertFalse(stateMachine.isFading());
    }

    @Test
    public void testPrematureAlarmExpiryIgnoredWhenTimerActive() {
        long now = System.currentTimeMillis();
        long futureEndsAt = now + 10 * 60_000L; // Timer ends in 10 minutes

        stateMachine.initialize(true, 10, futureEndsAt, 10, true, now);
        assertEquals(SleepTimerStateMachine.State.ACTIVE, stateMachine.getState());

        // Simulate stale or early alarm intent received 9 minutes before expiry
        stateMachine.handleAlarmExpiry(10, now + 60_000L);

        // Verify state remains ACTIVE and does not transition to FADING prematurely
        assertEquals(SleepTimerStateMachine.State.ACTIVE, stateMachine.getState());
        assertFalse(stateMachine.isFading());
    }
}
