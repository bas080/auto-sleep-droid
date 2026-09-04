package com.bas080.autosleepdroid;

import android.app.NotificationManager;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.test.core.app.ApplicationProvider;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowNotificationManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34})
public class SleepTimerServiceTest {

    private Context context;
    private SharedPreferences preferences;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        preferences = context.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE);
        preferences.edit().clear().commit();

    }

    @Test
    public void testServiceCreation() {
        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();
        assertNotNull(service);
    }

    @Test
    public void testServiceCreationAlwaysPostsForegroundNotification() {
        preferences.edit()
                .putBoolean("active", true)
                .commit();

        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();
        assertNotNull(service);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadowNotificationManager = Shadows.shadowOf(notificationManager);
        assertNotNull("Ongoing notification should always be posted", shadowNotificationManager.getNotification(1001));
    }

    @Test
    public void testTurnOnActionPersistsState() {
        preferences.edit()
                .putBoolean("active", false)
                .putInt("duration_minutes", 30)
                .commit();

        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        Intent turnOnIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_TURN_ON);

        service.onStartCommand(turnOnIntent, 0, 1);

        assertTrue(preferences.getBoolean("active", false));
    }

    @Test
    public void testAlarmExpiryActionTriggersFade() {
        preferences.edit()
                .putBoolean("active", true)
                .putInt("duration_minutes", 10)
                .putLong("timer_ends_at", System.currentTimeMillis() - 1000L)
                .commit();

        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        Intent alarmIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_ALARM_EXPIRY);

        service.onStartCommand(alarmIntent, 0, 1);
        assertNotNull(service);
    }

    @Test
    public void testTurnOffActionPersistsState() {
        preferences.edit()
                .putBoolean("active", true)
                .putInt("duration_minutes", 30)
                .commit();

        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        Intent turnOffIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_TURN_OFF);

        service.onStartCommand(turnOffIntent, 0, 1);

        assertFalse(preferences.getBoolean("active", true));
    }

    @Test
    public void testParseDurationMinutes() {
        assertEquals(30, SleepTimerService.parseDurationMinutes("30"));
        assertEquals(60, SleepTimerService.parseDurationMinutes("1h"));
        assertEquals(120, SleepTimerService.parseDurationMinutes("2H"));
        assertEquals(135, SleepTimerService.parseDurationMinutes("2h15m"));
        assertEquals(450, SleepTimerService.parseDurationMinutes("7h30m"));
        assertEquals(450, SleepTimerService.parseDurationMinutes("7h 30m"));
        assertEquals(75, SleepTimerService.parseDurationMinutes("1 h 15 m"));
        assertEquals(130, SleepTimerService.parseDurationMinutes("2h10m5s"));
        assertEquals(15, SleepTimerService.parseDurationMinutes("15m30s"));
        assertEquals(-1, SleepTimerService.parseDurationMinutes("10x10h4m"));
        assertEquals(-1, SleepTimerService.parseDurationMinutes("10m10"));
        assertEquals(-1, SleepTimerService.parseDurationMinutes("10h20h"));
        assertEquals(-1, SleepTimerService.parseDurationMinutes("abc"));
        assertEquals(-1, SleepTimerService.parseDurationMinutes(null));
        assertEquals(-1, SleepTimerService.parseDurationMinutes("  "));
    }

    @Test
    public void testSetFlexibleDurationViaRemoteInput() {
        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        Intent setIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_SET_DURATION);

        Bundle results = new Bundle();
        results.putCharSequence("duration_minutes", "2h15m");
        RemoteInput.addResultsToIntent(new RemoteInput[]{
                new RemoteInput.Builder("duration_minutes").build()
        }, setIntent, results);

        service.onStartCommand(setIntent, 0, 1);

        assertEquals(135, preferences.getInt("duration_minutes", -1));
        assertTrue(preferences.getBoolean("active", false));
    }

    @Test
    public void testSetValidDurationViaRemoteInput() {
        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        Intent setIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_SET_DURATION);

        Bundle results = new Bundle();
        results.putCharSequence("duration_minutes", "45");
        RemoteInput.addResultsToIntent(new RemoteInput[]{
                new RemoteInput.Builder("duration_minutes").build()
        }, setIntent, results);

        service.onStartCommand(setIntent, 0, 1);

        assertEquals(45, preferences.getInt("duration_minutes", -1));
        assertTrue(preferences.getBoolean("active", false));
    }

    @Test
    public void testSetInvalidDurationFallsBackToPreviousValidOrDefault() {
        preferences.edit()
                .putBoolean("active", true)
                .putInt("duration_minutes", 25)
                .commit();

        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        Intent setIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_SET_DURATION);

        Bundle results = new Bundle();
        results.putCharSequence("duration_minutes", "invalid_number");
        RemoteInput.addResultsToIntent(new RemoteInput[]{
                new RemoteInput.Builder("duration_minutes").build()
        }, setIntent, results);

        service.onStartCommand(setIntent, 0, 1);

        // Previous valid duration was 25
        assertEquals(25, preferences.getInt("duration_minutes", -1));
        assertTrue(preferences.getBoolean("active", false));
        assertEquals(context.getString(R.string.toast_duration_invalid), org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void testSetOutOfRangeDurationFallsBack() {
        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        Intent setIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_SET_DURATION);

        Bundle results = new Bundle();
        results.putCharSequence("duration_minutes", "99999"); // > 1440
        RemoteInput.addResultsToIntent(new RemoteInput[]{
                new RemoteInput.Builder("duration_minutes").build()
        }, setIntent, results);

        service.onStartCommand(setIntent, 0, 1);

        // Falls back to default duration 20
        assertEquals(20, preferences.getInt("duration_minutes", -1));
        assertTrue(preferences.getBoolean("active", false));
    }

    @Test
    public void testRedrawNotificationActionReloadsSettingsAndUpdatesStateMachine() throws Exception {
        // Initially active with 20 minutes
        preferences.edit()
                .putBoolean("active", true)
                .putBoolean("show_notification", true)
                .putInt("duration_minutes", 20)
                .commit();

        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        java.lang.reflect.Field stateMachineField = SleepTimerService.class.getDeclaredField("stateMachine");
        stateMachineField.setAccessible(true);
        SleepTimerStateMachine stateMachine = (SleepTimerStateMachine) stateMachineField.get(service);

        assertEquals(20, stateMachine.getConfiguredDurationMinutes());
        assertTrue(stateMachine.isEnabled());

        // Update preferences to inactive with 45 minutes duration and goal enabled
        preferences.edit()
                .putBoolean("active", false)
                .putBoolean("show_notification", true)
                .putInt("duration_minutes", 45)
                .putBoolean("wake_up_goal_enabled", true)
                .putInt("wake_up_goal_hour", 7)
                .putInt("wake_up_goal_minute", 0)
                .commit();

        Intent redrawIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_REDRAW_NOTIFICATION);

        service.onStartCommand(redrawIntent, 0, 1);

        // Verify state machine reloaded duration and updated active/enabled state
        assertFalse(stateMachine.isEnabled());
        assertEquals(45, stateMachine.getConfiguredDurationMinutes());

        // Now test turning timer on via preferences reload
        preferences.edit()
                .putBoolean("active", true)
                .putInt("duration_minutes", 60)
                .commit();

        service.onStartCommand(redrawIntent, 0, 1);

        assertTrue(stateMachine.isEnabled());
        assertEquals(60, stateMachine.getConfiguredDurationMinutes());

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadowNotificationManager = Shadows.shadowOf(notificationManager);
        android.app.Notification notification = shadowNotificationManager.getNotification(1001);
        assertNotNull(notification);
        assertNotNull(notification.contentIntent);
    }

    @Test
    public void testNotificationActionDisplaysNapAndCancelNap() {
        preferences.edit().putBoolean("active", false).putBoolean("show_notification", true).commit();
        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadowNotificationManager = Shadows.shadowOf(notificationManager);
        android.app.Notification notificationOff = shadowNotificationManager.getNotification(1001);
        assertNotNull(notificationOff);
        assertEquals(2, notificationOff.actions.length);
        assertEquals("Enable", notificationOff.actions[0].title.toString());
        assertEquals("Nap", notificationOff.actions[1].title.toString());
    }

    @Test
    public void testStartAndCancelNapAlarm() {
        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        Intent startNapIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_START_NAP)
                .putExtra(SleepTimerService.EXTRA_NAP_DURATION_MINUTES, 30);

        service.onStartCommand(startNapIntent, 0, 1);

        assertEquals(30, preferences.getInt("nap_duration_minutes", -1));
        assertTrue(preferences.getLong("nap_alarm_ends_at", 0L) > System.currentTimeMillis());

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadowNotificationManager = Shadows.shadowOf(notificationManager);
        android.app.Notification notificationNapActive = shadowNotificationManager.getNotification(1001);
        assertNotNull(notificationNapActive);
        assertEquals(2, notificationNapActive.actions.length);
        assertEquals("Cancel Nap", notificationNapActive.actions[1].title.toString());
        String activeContentText = notificationNapActive.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString();
        assertTrue("Notification content text must communicate active nap state", activeContentText.contains("Nap at"));

        Intent cancelNapIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_CANCEL_NAP);
        service.onStartCommand(cancelNapIntent, 0, 1);

        assertFalse(preferences.contains("nap_alarm_ends_at"));

        android.app.Notification notificationNapCancelled = shadowNotificationManager.getNotification(1001);
        assertNotNull(notificationNapCancelled);
        assertEquals("Nap", notificationNapCancelled.actions[1].title.toString());
        String cancelledContentText = notificationNapCancelled.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString();
        assertFalse("Notification content text must not contain nap state after cancel", cancelledContentText.contains("Nap at"));
    }

    @Test
    public void testTogglingTimerOffKeepsNapAlarmRunning() {
        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        Intent startNapIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_START_NAP)
                .putExtra(SleepTimerService.EXTRA_NAP_DURATION_MINUTES, 20);
        service.onStartCommand(startNapIntent, 0, 1);

        assertTrue("Nap alarm must be active in preferences", preferences.contains("nap_alarm_ends_at"));

        // Toggle/turn off the sleep timer
        Intent turnOffIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_TURN_OFF);
        service.onStartCommand(turnOffIntent, 0, 1);

        assertFalse(preferences.getBoolean("active", true));
        assertTrue("Toggling sleep timer off must keep active nap alarm running", preferences.contains("nap_alarm_ends_at"));
    }

    @Test
    public void testNapAlarmExpiryTriggersWakeUpAlarmSoundAndNotification() {
        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        Intent napExpiryIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_NAP_EXPIRY);

        service.onStartCommand(napExpiryIntent, 0, 1);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadowNotificationManager = Shadows.shadowOf(notificationManager);
        android.app.Notification ringingNotification = shadowNotificationManager.getNotification(1001);
        assertNotNull(ringingNotification);
        assertEquals(context.getString(R.string.wakeup_alarm_title), ringingNotification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE));
    }

    @Test
    public void testSleepTimerResetPushesActiveNapAlarmForward() throws Exception {
        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        Intent startNapIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_START_NAP)
                .putExtra(SleepTimerService.EXTRA_NAP_DURATION_MINUTES, 20);
        service.onStartCommand(startNapIntent, 0, 1);

        long initialNapEndsAt = preferences.getLong("nap_alarm_ends_at", 0L);
        assertTrue(initialNapEndsAt > 0L);

        java.lang.reflect.Field stateMachineField = SleepTimerService.class.getDeclaredField("stateMachine");
        stateMachineField.setAccessible(true);
        SleepTimerStateMachine stateMachine = (SleepTimerStateMachine) stateMachineField.get(service);

        long now = System.currentTimeMillis();
        // Initialize timer
        stateMachine.initialize(true, 20, now + 10000L, 10, true, now);

        java.lang.reflect.Field lastTimerEndsAtField = SleepTimerService.class.getDeclaredField("lastTimerEndsAt");
        lastTimerEndsAtField.setAccessible(true);
        lastTimerEndsAtField.setLong(service, now + 10000L);

        // Reset timer 15 minutes forward (new ends at = now + 10000 + 15m)
        stateMachine.startTimer(20, now + 10000L + 15 * 60_000L, now, true);

        long shiftedNapEndsAt = preferences.getLong("nap_alarm_ends_at", 0L);
        assertEquals(initialNapEndsAt + 15 * 60_000L, shiftedNapEndsAt);
    }

    @Test
    public void testClearGoalIntentClearsGoal() {
        preferences.edit()
                .putBoolean("wake_up_goal_enabled", true)
                .putInt("wake_up_goal_hour", 6)
                .putInt("wake_up_goal_minute", 30)
                .commit();

        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        // Triggering CLEAR_GOAL action clears preference
        service.onStartCommand(new Intent(context, SleepTimerService.class).setAction(SleepTimerService.ACTION_CLEAR_GOAL), 0, 1);
        assertFalse(preferences.getBoolean("wake_up_goal_enabled", true));
    }


    @Test
    public void testPhoneFlipSensorEventDetection() throws Exception {
        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        java.lang.reflect.Field stateMachineField = SleepTimerService.class.getDeclaredField("stateMachine");
        stateMachineField.setAccessible(true);
        SleepTimerStateMachine stateMachine = (SleepTimerStateMachine) stateMachineField.get(service);

        long now = System.currentTimeMillis();
        stateMachine.initialize(true, 10, now + 60000L, 10, true, now);
        assertEquals(SleepTimerStateMachine.State.ACTIVE, stateMachine.getState());

        java.lang.reflect.Constructor<android.hardware.SensorEvent> constructor =
                android.hardware.SensorEvent.class.getDeclaredConstructor(int.class);
        constructor.setAccessible(true);
        android.hardware.SensorEvent faceUpEvent = constructor.newInstance(3);
        faceUpEvent.values[0] = 0f;
        faceUpEvent.values[1] = 0f;
        faceUpEvent.values[2] = 9.8f;

        java.lang.reflect.Constructor<android.hardware.Sensor> sensorConstructor =
                android.hardware.Sensor.class.getDeclaredConstructor();
        sensorConstructor.setAccessible(true);
        android.hardware.Sensor accelerometer = sensorConstructor.newInstance();
        java.lang.reflect.Field typeField = android.hardware.Sensor.class.getDeclaredField("mType");
        typeField.setAccessible(true);
        typeField.setInt(accelerometer, android.hardware.Sensor.TYPE_ACCELEROMETER);
        faceUpEvent.sensor = accelerometer;

        java.lang.reflect.Field lastTimeField = SleepTimerService.class.getDeclaredField("lastSensorEventTimeMs");
        lastTimeField.setAccessible(true);

        service.onSensorChanged(faceUpEvent);

        lastTimeField.setLong(service, 0L);

        android.hardware.SensorEvent faceDownEvent = constructor.newInstance(3);
        faceDownEvent.values[0] = 0f;
        faceDownEvent.values[1] = 0f;
        faceDownEvent.values[2] = -9.8f;
        faceDownEvent.sensor = accelerometer;

        service.onSensorChanged(faceDownEvent);

        // Verify flip event resets timer in ACTIVE state
        assertEquals(SleepTimerStateMachine.State.ACTIVE, stateMachine.getState());
    }

    @Test
    public void testServiceHandlesNullAccelerometerGracefully() {
        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        // Calling onSensorChanged with null event or null sensor should not crash
        service.onSensorChanged(null);

        // Verify notification commands still work as expected
        Intent setIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_SET_DURATION);
        Bundle results = new Bundle();
        results.putCharSequence("duration_minutes", "30");
        RemoteInput.addResultsToIntent(new RemoteInput[]{
                new RemoteInput.Builder("duration_minutes").build()
        }, setIntent, results);
        service.onStartCommand(setIntent, 0, 1);

        assertEquals(30, preferences.getInt("duration_minutes", -1));
        assertTrue(preferences.getBoolean("active", false));

        // Verify turn off command still works as expected
        Intent turnOffIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_TURN_OFF);
        service.onStartCommand(turnOffIntent, 0, 1);

        assertFalse(preferences.getBoolean("active", true));
    }

    @Test
    public void testFadeOutStepDoesNotCancelFadeWhenVolumeUpdates() throws Exception {
        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        java.lang.reflect.Field audioManagerField = SleepTimerService.class.getDeclaredField("audioManager");
        audioManagerField.setAccessible(true);
        android.media.AudioManager audioManager = (android.media.AudioManager) audioManagerField.get(service);
        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 10, 0);

        java.lang.reflect.Field stateMachineField = SleepTimerService.class.getDeclaredField("stateMachine");
        stateMachineField.setAccessible(true);
        SleepTimerStateMachine stateMachine = (SleepTimerStateMachine) stateMachineField.get(service);

        stateMachine.beginFadeOut(10);

        java.lang.reflect.Method runFadeStepMethod = SleepTimerService.class.getDeclaredMethod("runFadeStep");
        runFadeStepMethod.setAccessible(true);

        // Execute runFadeStep
        runFadeStepMethod.invoke(service);

        assertTrue(stateMachine.isFading());
    }

    @Test
    public void testWakeUpAlarmSnoozeKeepsNotificationOpen() {
        preferences.edit().putBoolean("show_notification", true).putBoolean("wake_alarm_enabled", true).commit();
        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadowNotificationManager = Shadows.shadowOf(notificationManager);

        // Trigger wake-up alarm
        Intent triggerIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_WAKEUP_ALARM_EXPIRY);
        service.onStartCommand(triggerIntent, 0, 1);

        android.app.Notification wakeUpNotification = shadowNotificationManager.getNotification(1001);
        assertNotNull(wakeUpNotification);
        assertEquals(context.getString(R.string.wakeup_alarm_title), wakeUpNotification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE));

        // Snooze wake-up alarm
        Intent snoozeIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_SNOOZE_WAKEUP_ALARM);
        service.onStartCommand(snoozeIntent, 0, 1);

        // Notification should STILL be present after snooze
        android.app.Notification snoozedNotification = shadowNotificationManager.getNotification(1001);
        assertNotNull("Wake-up alarm notification must remain open when snoozed", snoozedNotification);
        assertEquals(context.getString(R.string.toast_alarm_snoozed), snoozedNotification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT));

        // Dismiss wake-up alarm
        Intent dismissIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_DISMISS_WAKEUP_ALARM);
        service.onStartCommand(dismissIntent, 0, 1);

        android.app.Notification dismissedNotification = shadowNotificationManager.getNotification(1001);
        assertNotNull(dismissedNotification);
        assertFalse(context.getString(R.string.wakeup_alarm_title).equals(dismissedNotification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)));
    }

    @Test
    public void testWakeUpAlarmFlipSnoozeKeepsNotificationOpen() throws Exception {
        preferences.edit().putBoolean("show_notification", true).putBoolean("wake_alarm_enabled", true).commit();
        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadowNotificationManager = Shadows.shadowOf(notificationManager);

        // Trigger wake-up alarm expiry
        Intent triggerIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_WAKEUP_ALARM_EXPIRY);
        service.onStartCommand(triggerIntent, 0, 1);

        assertNotNull(shadowNotificationManager.getNotification(1001));

        // Set initial orientation to FACE_UP
        java.lang.reflect.Field orientationField = SleepTimerService.class.getDeclaredField("lastOrientation");
        orientationField.setAccessible(true);
        orientationField.setInt(service, 1); // ORIENTATION_FACE_UP

        // Simulate flip gesture via onSensorChanged (face-down event)
        java.lang.reflect.Constructor<android.hardware.SensorEvent> constructor =
                android.hardware.SensorEvent.class.getDeclaredConstructor(int.class);
        constructor.setAccessible(true);

        java.lang.reflect.Constructor<android.hardware.Sensor> sensorConstructor =
                android.hardware.Sensor.class.getDeclaredConstructor();
        sensorConstructor.setAccessible(true);
        android.hardware.Sensor accelerometer = sensorConstructor.newInstance();
        java.lang.reflect.Field typeField = android.hardware.Sensor.class.getDeclaredField("mType");
        typeField.setAccessible(true);
        typeField.setInt(accelerometer, android.hardware.Sensor.TYPE_ACCELEROMETER);

        android.hardware.SensorEvent faceDownEvent = constructor.newInstance(3);
        faceDownEvent.values[0] = 0f;
        faceDownEvent.values[1] = 0f;
        faceDownEvent.values[2] = -9.8f;
        faceDownEvent.sensor = accelerometer;

        service.onSensorChanged(faceDownEvent);

        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Notification should STILL be present after snooze via flip
        android.app.Notification snoozedNotification = shadowNotificationManager.getNotification(1001);
        assertNotNull("Wake-up alarm notification must remain open when snoozed via flip", snoozedNotification);
        assertEquals(context.getString(R.string.toast_alarm_snoozed), snoozedNotification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT));
    }

    @Test
    public void testVolumeKeyDismissesRingingWakeUpAlarm() {
        preferences.edit().putBoolean("show_notification", true).commit();
        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadowNotificationManager = Shadows.shadowOf(notificationManager);

        // Trigger wake-up alarm
        Intent triggerIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_WAKEUP_ALARM_EXPIRY);
        service.onStartCommand(triggerIntent, 0, 1);

        assertNotNull(shadowNotificationManager.getNotification(1001));

        // Send volume change broadcast while ringing
        context.sendBroadcast(new Intent("android.media.VOLUME_CHANGED_ACTION"));
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Notification should be reverted from alarm state
        android.app.Notification dismissedNotification = shadowNotificationManager.getNotification(1001);
        assertNotNull(dismissedNotification);
        assertFalse(context.getString(R.string.wakeup_alarm_title).equals(dismissedNotification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)));
    }

    @Test
    public void testVolumeKeyDismissesSnoozedWakeUpAlarm() {
        preferences.edit().putBoolean("show_notification", true).commit();
        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadowNotificationManager = Shadows.shadowOf(notificationManager);

        // Trigger wake-up alarm
        Intent triggerIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_WAKEUP_ALARM_EXPIRY);
        service.onStartCommand(triggerIntent, 0, 1);

        // Snooze wake-up alarm
        Intent snoozeIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_SNOOZE_WAKEUP_ALARM);
        service.onStartCommand(snoozeIntent, 0, 1);

        assertNotNull(shadowNotificationManager.getNotification(1001));

        // Trigger volume button change broadcast while snoozed
        try {
            java.lang.reflect.Field receiverField = SleepTimerService.class.getDeclaredField("volumeReceiver");
            receiverField.setAccessible(true);
            android.content.BroadcastReceiver receiver = (android.content.BroadcastReceiver) receiverField.get(service);
            assertNotNull(receiver);
            receiver.onReceive(service, new Intent("android.media.VOLUME_CHANGED_ACTION"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Notification should be reverted from alarm state
        android.app.Notification dismissedNotification = shadowNotificationManager.getNotification(1001);
        assertNotNull(dismissedNotification);
        assertFalse(context.getString(R.string.wakeup_alarm_title).equals(dismissedNotification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)));
    }

    @Test
    public void testDisablingSleepTimerDismissesActiveAndFutureAlarms() {
        preferences.edit()
                .putBoolean("active", true)
                .putBoolean("show_notification", true)
                .putBoolean("wake_up_goal_enabled", true)
                .putInt("wake_up_goal_hour", 6)
                .putInt("wake_up_goal_minute", 30)
                .commit();

        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        // Trigger wake-up alarm
        Intent triggerIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_WAKEUP_ALARM_EXPIRY);
        service.onStartCommand(triggerIntent, 0, 1);

        assertTrue(preferences.contains(SleepTimerService.KEY_WAKEUP_LAST_SCHEDULED_MS));

        // Disable sleep timer via TURN_OFF action
        Intent turnOffIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_TURN_OFF);
        service.onStartCommand(turnOffIntent, 0, 1);

        assertFalse(preferences.getBoolean("active", true));
        assertFalse(preferences.contains(SleepTimerService.KEY_WAKEUP_LAST_SCHEDULED_MS));
    }

    @Test
    public void testFadeVolumeStateConsistencyDuringStreamVolumeSet() throws Exception {
        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        java.lang.reflect.Field audioManagerField = SleepTimerService.class.getDeclaredField("audioManager");
        audioManagerField.setAccessible(true);
        android.media.AudioManager audioManager = (android.media.AudioManager) audioManagerField.get(service);
        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 10, 0);

        java.lang.reflect.Field stateMachineField = SleepTimerService.class.getDeclaredField("stateMachine");
        stateMachineField.setAccessible(true);
        SleepTimerStateMachine stateMachine = (SleepTimerStateMachine) stateMachineField.get(service);

        stateMachine.beginFadeOut(10);

        java.lang.reflect.Method runFadeStepMethod = SleepTimerService.class.getDeclaredMethod("runFadeStep");
        runFadeStepMethod.setAccessible(true);

        // Start fade step 1
        runFadeStepMethod.invoke(service);

        int currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);

        assertEquals("lastObservedVolume must match updated stream volume", currentVolume, stateMachine.getLastObservedVolume());
        assertTrue("Fade should not be cancelled during fade step volume update", stateMachine.isFading());
    }

    @Test
    public void testWakeUpAlarmTriggersVolumeCrescendo() throws Exception {
        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        // Inject a non-null Ringtone instance via reflection to simulate system returning a valid Ringtone
        java.lang.reflect.Field ringtoneField = SleepTimerService.class.getDeclaredField("currentAlarmRingtone");
        ringtoneField.setAccessible(true);
        java.lang.reflect.Constructor<android.media.Ringtone> constructor =
                android.media.Ringtone.class.getDeclaredConstructor(Context.class, boolean.class);
        constructor.setAccessible(true);
        android.media.Ringtone mockRingtone = constructor.newInstance(context, false);
        ringtoneField.set(service, mockRingtone);

        // Invoke startWakeUpAlarmCrescendo
        java.lang.reflect.Method crescendoMethod = SleepTimerService.class.getDeclaredMethod("startWakeUpAlarmCrescendo");
        crescendoMethod.setAccessible(true);
        crescendoMethod.invoke(service);

        java.lang.reflect.Field runnableField = SleepTimerService.class.getDeclaredField("alarmCrescendoRunnable");
        runnableField.setAccessible(true);
        assertNotNull("Crescendo runnable should be scheduled when crescendo starts", runnableField.get(service));

        // Dismiss wake-up alarm
        Intent dismissIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_DISMISS_WAKEUP_ALARM);
        service.onStartCommand(dismissIntent, 0, 1);

        assertEquals("Ringtone should be cleared after dismiss", null, ringtoneField.get(service));
        assertEquals("Crescendo runnable should be cancelled after dismiss", null, runnableField.get(service));
    }

    @Test
    public void testEnsureAudibleAlarmStreamVolume() throws Exception {
        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        android.media.AudioManager am = (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            am.setStreamVolume(android.media.AudioManager.STREAM_ALARM, 0, 0);

            java.lang.reflect.Method ensureVolMethod = SleepTimerService.class.getDeclaredMethod("ensureAudibleAlarmStreamVolume");
            ensureVolMethod.setAccessible(true);
            ensureVolMethod.invoke(service);

            int maxVol = am.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM);
            int expectedMinVol = Math.max(1, (int) Math.round(maxVol * 0.3));
            assertEquals("STREAM_ALARM volume should be raised to minimum audible volume if muted",
                    expectedMinVol, am.getStreamVolume(android.media.AudioManager.STREAM_ALARM));
        }
    }

    @Test
    public void testRunWakeUpAlarmCrescendoStepAppliesQuadraticGain() throws Exception {
        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        java.lang.reflect.Field ringtoneField = SleepTimerService.class.getDeclaredField("currentAlarmRingtone");
        ringtoneField.setAccessible(true);
        java.lang.reflect.Constructor<android.media.Ringtone> constructor =
                android.media.Ringtone.class.getDeclaredConstructor(Context.class, boolean.class);
        constructor.setAccessible(true);
        android.media.Ringtone mockRingtone = constructor.newInstance(context, false);
        ringtoneField.set(service, mockRingtone);

        java.lang.reflect.Field startTimeField = SleepTimerService.class.getDeclaredField("alarmCrescendoStartTimeMs");
        startTimeField.setAccessible(true);
        // Set crescendo start time to 90 seconds ago (out of 180 seconds total) -> linear progress = 0.5
        long ninetySecondsAgo = System.currentTimeMillis() - 90_000L;
        startTimeField.setLong(service, ninetySecondsAgo);

        java.lang.reflect.Method crescendoStepMethod = SleepTimerService.class.getDeclaredMethod("runWakeUpAlarmCrescendoStep");
        crescendoStepMethod.setAccessible(true);
        crescendoStepMethod.invoke(service);

        // At 50% linear progress (0.5), quadratic gain should be 0.5 * 0.5 = 0.25
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            assertEquals("Ringtone volume at 50% time should be 0.25 (quadratic gain)", 0.25f, mockRingtone.getVolume(), 0.05f);
        }
    }

    @Test
    public void testWakeUpAlarmTriggersNextDailyAlarm() {
        preferences.edit()
                .putBoolean("wake_up_goal_enabled", true)
                .putInt("wake_up_goal_hour", 6)
                .putInt("wake_up_goal_minute", 30)
                .putInt("min_sleep_duration_minutes", 450)
                .commit();

        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        long beforeMs = System.currentTimeMillis();

        // Trigger wake-up alarm
        Intent triggerIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_WAKEUP_ALARM_EXPIRY);
        service.onStartCommand(triggerIntent, 0, 1);

        long scheduledMs = preferences.getLong(SleepTimerService.KEY_WAKEUP_LAST_SCHEDULED_MS, 0L);
        assertTrue("Next daily alarm must be scheduled after current alarm rings", scheduledMs > beforeMs);
    }

    @Test
    public void testDailyRecurringAlarmSnoozeDoesNotOverwriteNextDailyAlarm() {
        preferences.edit()
                .putBoolean("wake_up_goal_enabled", true)
                .putInt("wake_up_goal_hour", 6)
                .putInt("wake_up_goal_minute", 30)
                .putInt("min_sleep_duration_minutes", 450)
                .commit();

        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        // Trigger wake-up alarm
        Intent triggerIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_WAKEUP_ALARM_EXPIRY);
        service.onStartCommand(triggerIntent, 0, 1);

        long dailyAlarmMs = preferences.getLong(SleepTimerService.KEY_WAKEUP_LAST_SCHEDULED_MS, 0L);
        assertTrue(dailyAlarmMs > 0L);

        // Snooze wake-up alarm
        Intent snoozeIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_SNOOZE_WAKEUP_ALARM);
        service.onStartCommand(snoozeIntent, 0, 1);

        // Verify the scheduled timestamp for tomorrow's daily alarm in preferences is unchanged
        assertEquals("Snoozing must not overwrite scheduled daily recurring alarm time",
                dailyAlarmMs, preferences.getLong(SleepTimerService.KEY_WAKEUP_LAST_SCHEDULED_MS, 0L));
    }

    @Test
    public void testCalculateScheduledAlarmAllowsNextDayGoal() {
        preferences.edit()
                .putBoolean("wake_up_goal_enabled", true)
                .putInt("wake_up_goal_hour", 6)
                .putInt("wake_up_goal_minute", 30)
                .putInt("min_sleep_duration_minutes", 450)
                .commit();

        long now = System.currentTimeMillis();
        java.util.Calendar cal = SleepTimerService.calculateScheduledAlarm(context, now, 0L);
        assertNotNull("calculateScheduledAlarm should return scheduled Calendar for daily goal", cal);
        assertTrue("Scheduled alarm must be in the future", cal.getTimeInMillis() > now);
    }
}
