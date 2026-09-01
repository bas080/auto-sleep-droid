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
    public void testGoalSettingsDialogActivityWithFloatAndDurationStrings() {
        org.robolectric.android.controller.ActivityController<GoalSettingsDialogActivity> activityController =
                Robolectric.buildActivity(GoalSettingsDialogActivity.class);
        GoalSettingsDialogActivity activity = activityController.create().start().resume().get();
        assertNotNull(activity);

        android.widget.EditText inputMinSleep = null;

        android.app.AlertDialog dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);

        // Locate inputMinSleep from dialog view hierarchy
        java.util.ArrayList<android.widget.EditText> editTexts = new java.util.ArrayList<>();
        if (dialog.getWindow() != null) {
            findViewsOfType(dialog.getWindow().getDecorView(), android.widget.EditText.class, editTexts);
        }
        assertFalse(editTexts.isEmpty());
        inputMinSleep = editTexts.get(0);

        // Verify initial text is formatted duration string (e.g., 7h 30m for default 450 min)
        assertEquals("7h 30m", inputMinSleep.getText().toString());

        // Test float hours input "0.5" -> 30 min
        inputMinSleep.setText("0.5");

        android.widget.Button okButton = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        assertNotNull(okButton);
        okButton.performClick();

        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals(30, preferences.getInt("min_sleep_duration_minutes", -1));
        assertTrue(preferences.getBoolean("wake_up_goal_enabled", false));
    }

    private <T extends android.view.View> void findViewsOfType(android.view.View root, Class<T> clazz, java.util.List<T> outList) {
        if (clazz.isInstance(root)) {
            outList.add(clazz.cast(root));
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                findViewsOfType(group.getChildAt(i), clazz, outList);
            }
        }
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
    public void testNotificationActionTogglesWhenEnabledAndDisabled() {
        // When timer is off/disabled: action 0 (duration) opens RemoteInput to set duration
        preferences.edit().putBoolean("active", false).commit();
        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadowNotificationManager = Shadows.shadowOf(notificationManager);
        android.app.Notification notificationOff = shadowNotificationManager.getNotification(1001);
        assertNotNull(notificationOff);
        assertEquals(2, notificationOff.actions.length);
        assertEquals(SleepTimerService.ACTION_SET_DURATION, Shadows.shadowOf(notificationOff.actions[0].actionIntent).getSavedIntent().getAction());

        // When timer is active/enabled: action 0 (duration) toggles timer off
        preferences.edit().putBoolean("active", true).commit();
        service.onStartCommand(new Intent(context, SleepTimerService.class).setAction(SleepTimerService.ACTION_TURN_ON), 0, 1);
        android.app.Notification notificationOn = shadowNotificationManager.getNotification(1001);
        assertNotNull(notificationOn);
        assertEquals(SleepTimerService.ACTION_TURN_OFF, Shadows.shadowOf(notificationOn.actions[0].actionIntent).getSavedIntent().getAction());
    }

    @Test
    public void testNotificationGoalActionClearsGoalWhenGoalEnabled() {
        preferences.edit()
                .putBoolean("wake_up_goal_enabled", true)
                .putInt("wake_up_goal_hour", 6)
                .putInt("wake_up_goal_minute", 30)
                .commit();

        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadowNotificationManager = Shadows.shadowOf(notificationManager);
        android.app.Notification notification = shadowNotificationManager.getNotification(1001);
        assertNotNull(notification);
        assertEquals(2, notification.actions.length);
        assertEquals(SleepTimerService.ACTION_CLEAR_GOAL, Shadows.shadowOf(notification.actions[1].actionIntent).getSavedIntent().getAction());

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

        service.onSensorChanged(faceUpEvent);

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
        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadowNotificationManager = Shadows.shadowOf(notificationManager);

        // Trigger wake-up alarm
        Intent triggerIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_WAKEUP_ALARM_EXPIRY);
        service.onStartCommand(triggerIntent, 0, 1);

        android.app.Notification wakeUpNotification = shadowNotificationManager.getNotification(1002);
        assertNotNull(wakeUpNotification);

        // Snooze wake-up alarm
        Intent snoozeIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_SNOOZE_WAKEUP_ALARM);
        service.onStartCommand(snoozeIntent, 0, 1);

        // Notification should STILL be present after snooze
        android.app.Notification snoozedNotification = shadowNotificationManager.getNotification(1002);
        assertNotNull("Wake-up alarm notification must remain open when snoozed", snoozedNotification);

        // Dismiss wake-up alarm
        Intent dismissIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_DISMISS_WAKEUP_ALARM);
        service.onStartCommand(dismissIntent, 0, 1);

        // Notification should be cancelled after dismiss
        android.app.Notification dismissedNotification = shadowNotificationManager.getNotification(1002);
        assertEquals(null, dismissedNotification);
    }

    @Test
    public void testWakeUpAlarmFlipSnoozeKeepsNotificationOpen() throws Exception {
        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadowNotificationManager = Shadows.shadowOf(notificationManager);

        // Trigger wake-up alarm expiry
        Intent triggerIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_WAKEUP_ALARM_EXPIRY);
        service.onStartCommand(triggerIntent, 0, 1);

        assertNotNull(shadowNotificationManager.getNotification(1002));

        // Simulate flip gesture via onSensorChanged
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

        service.onSensorChanged(faceUpEvent);

        android.hardware.SensorEvent faceDownEvent = constructor.newInstance(3);
        faceDownEvent.values[0] = 0f;
        faceDownEvent.values[1] = 0f;
        faceDownEvent.values[2] = -9.8f;
        faceDownEvent.sensor = accelerometer;

        service.onSensorChanged(faceDownEvent);

        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Notification should STILL be present after snooze via flip
        android.app.Notification snoozedNotification = shadowNotificationManager.getNotification(1002);
        assertNotNull("Wake-up alarm notification must remain open when snoozed via flip", snoozedNotification);
    }

    @Test
    public void testVolumeKeyDismissesRingingWakeUpAlarm() {
        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadowNotificationManager = Shadows.shadowOf(notificationManager);

        // Trigger wake-up alarm
        Intent triggerIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_WAKEUP_ALARM_EXPIRY);
        service.onStartCommand(triggerIntent, 0, 1);

        assertNotNull(shadowNotificationManager.getNotification(1002));

        // Send volume change broadcast while ringing
        context.sendBroadcast(new Intent("android.media.VOLUME_CHANGED_ACTION"));
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // Notification should be cancelled after volume button click dismiss
        android.app.Notification dismissedNotification = shadowNotificationManager.getNotification(1002);
        assertEquals(null, dismissedNotification);
    }

    @Test
    public void testVolumeKeyDismissesSnoozedWakeUpAlarm() {
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

        assertNotNull(shadowNotificationManager.getNotification(1002));

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

        // Notification should be cancelled after volume button click dismiss
        android.app.Notification dismissedNotification = shadowNotificationManager.getNotification(1002);
        assertEquals(null, dismissedNotification);
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
    public void testWakeUpAlarmDismissalStoresLastWakeUpTimeAndSchedulesNextAlarm() throws Exception {
        java.util.Calendar alarmCal = java.util.Calendar.getInstance();
        long now = System.currentTimeMillis();
        alarmCal.setTimeInMillis(now);
        alarmCal.add(java.util.Calendar.HOUR_OF_DAY, 2); // 2 hours from now (< 12h)
        int expectedHour = alarmCal.get(java.util.Calendar.HOUR_OF_DAY);
        int expectedMinute = alarmCal.get(java.util.Calendar.MINUTE);
        long scheduledTimeMs = alarmCal.getTimeInMillis();

        preferences.edit()
                .putBoolean("wake_up_goal_enabled", true)
                .putInt("wake_up_goal_hour", expectedHour)
                .putInt("wake_up_goal_minute", expectedMinute)
                .putLong(SleepTimerService.KEY_WAKEUP_LAST_SCHEDULED_MS, scheduledTimeMs)
                .commit();

        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        Intent dismissIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_DISMISS_WAKEUP_ALARM);
        service.onStartCommand(dismissIntent, 0, 1);

        assertEquals(expectedHour, preferences.getInt("wake_up_goal_hour", -1));
        assertEquals(expectedMinute, preferences.getInt("wake_up_goal_minute", -1));
        assertEquals(expectedHour, preferences.getInt("last_wake_up_hour", -1));
        assertEquals(expectedMinute, preferences.getInt("last_wake_up_minute", -1));

        // Alarm for the next day should now be scheduled in preferences
        assertTrue(preferences.contains(SleepTimerService.KEY_WAKEUP_LAST_SCHEDULED_MS));
    }

    @Test
    public void testCalculateScheduledAlarmWithoutTimerActivity() {
        java.util.Calendar nowCal = java.util.Calendar.getInstance();
        nowCal.set(2025, java.util.Calendar.JANUARY, 10, 22, 0, 0); // 10:00 PM
        long now = nowCal.getTimeInMillis();

        preferences.edit()
                .putBoolean("wake_up_goal_enabled", true)
                .putInt("wake_up_goal_hour", 6)
                .putInt("wake_up_goal_minute", 30)
                .putInt("min_sleep_duration_minutes", 450)
                .commit();

        // No timer activity (timerEndsAt == 0)
        java.util.Calendar result = SleepTimerService.calculateScheduledAlarm(context, now, 0L);
        assertNotNull(result);
        assertEquals(6, result.get(java.util.Calendar.HOUR_OF_DAY));
        assertEquals(30, result.get(java.util.Calendar.MINUTE));
    }

    @Test
    public void testCalculateScheduledAlarmPushesAlarmWithin12hWindow() {
        java.util.Calendar nowCal = java.util.Calendar.getInstance();
        nowCal.set(2025, java.util.Calendar.JANUARY, 10, 23, 0, 0); // 11:00 PM
        long now = nowCal.getTimeInMillis();

        preferences.edit()
                .putBoolean("wake_up_goal_enabled", true)
                .putInt("wake_up_goal_hour", 6)
                .putInt("wake_up_goal_minute", 30) // Target wake time: Jan 11, 06:30 AM
                .putInt("min_sleep_duration_minutes", 480) // 8 hours min sleep
                .commit();

        // Timer ends late at 00:30 AM (Jan 11) within the 12h window
        java.util.Calendar timerEndsCal = java.util.Calendar.getInstance();
        timerEndsCal.set(2025, java.util.Calendar.JANUARY, 11, 0, 30, 0);
        long timerEndsAt = timerEndsCal.getTimeInMillis();

        java.util.Calendar result = SleepTimerService.calculateScheduledAlarm(context, now, timerEndsAt);
        assertNotNull(result);

        // Wake alarm should be pushed to 00:30 AM + 8h = 08:30 AM
        assertEquals(8, result.get(java.util.Calendar.HOUR_OF_DAY));
        assertEquals(30, result.get(java.util.Calendar.MINUTE));
    }

    @Test
    public void testCalculateScheduledAlarmIgnoresTimerActivityOutside12hWindow() {
        java.util.Calendar nowCal = java.util.Calendar.getInstance();
        nowCal.set(2025, java.util.Calendar.JANUARY, 10, 10, 0, 0); // 10:00 AM
        long now = nowCal.getTimeInMillis();

        preferences.edit()
                .putBoolean("wake_up_goal_enabled", true)
                .putInt("wake_up_goal_hour", 6)
                .putInt("wake_up_goal_minute", 30) // Target goal time: Jan 11, 06:30 AM (20h away)
                .putInt("min_sleep_duration_minutes", 450)
                .commit();

        // Midday timer activity ending at 11:00 AM (Jan 10) - way outside 12h window of Jan 11, 06:30 AM
        java.util.Calendar timerEndsCal = java.util.Calendar.getInstance();
        timerEndsCal.set(2025, java.util.Calendar.JANUARY, 10, 11, 0, 0);
        long timerEndsAt = timerEndsCal.getTimeInMillis();

        // Calculate alarm at 10:00 AM - target wake time is >12h away so returns null
        java.util.Calendar result = SleepTimerService.calculateScheduledAlarm(context, now, timerEndsAt);
        assertEquals("Target alarm > 12h away should return null for current scheduling", null, result);

        // Now test at 10:00 PM (Jan 10), within 12h window of 06:30 AM (Jan 11)
        java.util.Calendar eveningCal = java.util.Calendar.getInstance();
        eveningCal.set(2025, java.util.Calendar.JANUARY, 10, 22, 0, 0);
        long eveningNow = eveningCal.getTimeInMillis();

        // The morning timer activity (11:00 AM Jan 10) is outside the 12h window (Jan 10 18:30 to Jan 11 06:30)
        java.util.Calendar eveningResult = SleepTimerService.calculateScheduledAlarm(context, eveningNow, timerEndsAt);
        assertNotNull(eveningResult);
        // Wake alarm time should NOT be pushed by morning timer activity; it should stay at target goal 06:30 AM
        assertEquals(6, eveningResult.get(java.util.Calendar.HOUR_OF_DAY));
        assertEquals(30, eveningResult.get(java.util.Calendar.MINUTE));
    }
}
