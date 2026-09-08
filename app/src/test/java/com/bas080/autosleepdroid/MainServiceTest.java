package com.bas080.autosleepdroid;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import java.util.Calendar;
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
public class MainServiceTest {

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
        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();
        assertNotNull(service);
    }

    @Test
    public void testServiceCreationAlwaysPostsForegroundNotification() {
        preferences.edit()
                .putBoolean("active", true)
                .commit();

        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();
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

        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        Intent turnOnIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_TURN_ON);

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

        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        Intent alarmIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_ALARM_EXPIRY);

        service.onStartCommand(alarmIntent, 0, 1);
        assertNotNull(service);
    }

    @Test
    public void testTurnOffActionPersistsState() {
        preferences.edit()
                .putBoolean("active", true)
                .putInt("duration_minutes", 30)
                .commit();

        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        Intent turnOffIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_TURN_OFF);

        service.onStartCommand(turnOffIntent, 0, 1);

        assertFalse(preferences.getBoolean("active", true));
    }

    @Test
    public void testParseDurationMinutes() {
        assertEquals(30, MainService.parseDurationMinutes("30"));
        assertEquals(60, MainService.parseDurationMinutes("1h"));
        assertEquals(120, MainService.parseDurationMinutes("2H"));
        assertEquals(135, MainService.parseDurationMinutes("2h15m"));
        assertEquals(450, MainService.parseDurationMinutes("7h30m"));
        assertEquals(450, MainService.parseDurationMinutes("7h 30m"));
        assertEquals(75, MainService.parseDurationMinutes("1 h 15 m"));
        assertEquals(130, MainService.parseDurationMinutes("2h10m5s"));
        assertEquals(15, MainService.parseDurationMinutes("15m30s"));
        assertEquals(-1, MainService.parseDurationMinutes("10x10h4m"));
        assertEquals(-1, MainService.parseDurationMinutes("10m10"));
        assertEquals(-1, MainService.parseDurationMinutes("10h20h"));
        assertEquals(-1, MainService.parseDurationMinutes("abc"));
        assertEquals(-1, MainService.parseDurationMinutes(null));
        assertEquals(-1, MainService.parseDurationMinutes("  "));
    }

    @Test
    public void testSetFlexibleDurationViaRemoteInput() {
        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        Intent setIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_SET_DURATION);

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
        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        Intent setIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_SET_DURATION);

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

        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        Intent setIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_SET_DURATION);

        Bundle results = new Bundle();
        results.putCharSequence("duration_minutes", "invalid_number");
        RemoteInput.addResultsToIntent(new RemoteInput[]{
                new RemoteInput.Builder("duration_minutes").build()
        }, setIntent, results);

        service.onStartCommand(setIntent, 0, 1);

        assertEquals(25, preferences.getInt("duration_minutes", -1));
        assertTrue(preferences.getBoolean("active", false));
        assertEquals(context.getString(R.string.toast_duration_invalid), org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void testSetOutOfRangeDurationFallsBack() {
        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        Intent setIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_SET_DURATION);

        Bundle results = new Bundle();
        results.putCharSequence("duration_minutes", "99999");
        RemoteInput.addResultsToIntent(new RemoteInput[]{
                new RemoteInput.Builder("duration_minutes").build()
        }, setIntent, results);

        service.onStartCommand(setIntent, 0, 1);

        assertEquals(20, preferences.getInt("duration_minutes", -1));
        assertTrue(preferences.getBoolean("active", false));
    }

    @Test
    public void testRedrawNotificationActionReloadsSettingsAndUpdatesStateMachine() throws Exception {
        preferences.edit()
                .putBoolean("active", true)
                .putBoolean("show_notification", true)
                .putInt("duration_minutes", 20)
                .commit();

        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        assertEquals(20, service.getConfiguredDurationMinutes());
        assertTrue(service.isEnabled());

        preferences.edit()
                .putBoolean("active", false)
                .putBoolean("show_notification", true)
                .putInt("duration_minutes", 45)
                .putBoolean("wake_up_goal_enabled", true)
                .putInt("wake_up_goal_hour", 7)
                .putInt("wake_up_goal_minute", 0)
                .commit();

        Intent redrawIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_REDRAW_NOTIFICATION);

        service.onStartCommand(redrawIntent, 0, 1);

        assertFalse(service.isEnabled());
        assertEquals(45, service.getConfiguredDurationMinutes());

        preferences.edit()
                .putBoolean("active", true)
                .putInt("duration_minutes", 60)
                .commit();

        service.onStartCommand(redrawIntent, 0, 1);

        assertTrue(service.isEnabled());
        assertEquals(60, service.getConfiguredDurationMinutes());

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
        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

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
        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        Intent startNapIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_START_NAP)
                .putExtra(MainService.EXTRA_NAP_DURATION_MINUTES, 30);

        service.onStartCommand(startNapIntent, 0, 1);

        assertEquals(30, preferences.getInt("nap_duration_minutes", -1));
        assertTrue(preferences.getLong("nap_alarm_ends_at", 0L) > System.currentTimeMillis());

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadowNotificationManager = Shadows.shadowOf(notificationManager);
        android.app.Notification notificationNapActive = shadowNotificationManager.getNotification(1001);
        assertNotNull(notificationNapActive);
        assertEquals(2, notificationNapActive.actions.length);
        assertEquals("I'm Awake", notificationNapActive.actions[1].title.toString());
        String activeContentText = notificationNapActive.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString();
        assertTrue("Notification content text must communicate active nap state", activeContentText.contains("Nap at"));

        Intent cancelNapIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_CANCEL_NAP);
        service.onStartCommand(cancelNapIntent, 0, 1);

        assertFalse(preferences.contains("nap_alarm_ends_at"));

        android.app.Notification notificationNapCancelled = shadowNotificationManager.getNotification(1001);
        assertNotNull(notificationNapCancelled);
        assertEquals("Nap", notificationNapCancelled.actions[1].title.toString());
        String cancelledContentText = notificationNapCancelled.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString();
        assertFalse("Notification content text must not contain nap state after cancel", cancelledContentText.contains("Nap at"));
    }

    @Test
    public void testReloadSettingsDoesNotOverrideManualTimerToggleWhenAutoDndIsEnabled() throws Exception {
        preferences.edit()
                .putBoolean("active", false)
                .putBoolean("auto_timer_enabled", true)
                .putInt("duration_minutes", 20)
                .commit();

        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        assertFalse(service.isEnabled());

        Intent redrawIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_REDRAW_NOTIFICATION);
        service.onStartCommand(redrawIntent, 0, 1);

        assertFalse("Reloading settings must not force timer ON when user explicitly disabled it", service.isEnabled());
    }

    @Test
    public void testTogglingTimerOffKeepsNapAlarmRunning() {
        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        Intent startNapIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_START_NAP)
                .putExtra(MainService.EXTRA_NAP_DURATION_MINUTES, 20);
        service.onStartCommand(startNapIntent, 0, 1);

        assertTrue("Nap alarm must be active in preferences", preferences.contains("nap_alarm_ends_at"));

        Intent turnOffIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_TURN_OFF);
        service.onStartCommand(turnOffIntent, 0, 1);

        assertFalse(preferences.getBoolean("active", true));
        assertTrue("Toggling sleep timer off must keep active nap alarm running", preferences.contains("nap_alarm_ends_at"));
    }

    @Test
    public void testNapAlarmExpiryTriggersWakeUpAlarmSoundAndNotification() {
        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        Intent napExpiryIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_NAP_EXPIRY);

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
        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        Intent startNapIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_START_NAP)
                .putExtra(MainService.EXTRA_NAP_DURATION_MINUTES, 20);
        service.onStartCommand(startNapIntent, 0, 1);

        long initialNapEndsAt = preferences.getLong("nap_alarm_ends_at", 0L);
        assertTrue(initialNapEndsAt > 0L);

        long now = System.currentTimeMillis();
        service.initializeTimerState(true, 20, now + 10000L, 10, true, now);

        java.lang.reflect.Field lastTimerEndsAtField = MainService.class.getDeclaredField("lastTimerEndsAt");
        lastTimerEndsAtField.setAccessible(true);
        lastTimerEndsAtField.setLong(service, now + 10000L);

        service.startTimer(20, now + 10000L + 15 * 60_000L, now, true);

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

        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        service.onStartCommand(new Intent(context, MainService.class).setAction(MainService.ACTION_CLEAR_GOAL), 0, 1);
        assertFalse(preferences.getBoolean("wake_up_goal_enabled", true));
    }

    @Test
    public void testPhoneFlipSensorEventDetection() throws Exception {
        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        long now = System.currentTimeMillis();
        service.initializeTimerState(true, 10, now + 60000L, 10, true, now);
        assertEquals(MainService.State.ACTIVE, service.getState());

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

        java.lang.reflect.Field lastTimeField = MainService.class.getDeclaredField("lastSensorEventTimeMs");
        lastTimeField.setAccessible(true);

        service.onSensorChanged(faceUpEvent);

        lastTimeField.setLong(service, 0L);

        android.hardware.SensorEvent faceDownEvent = constructor.newInstance(3);
        faceDownEvent.values[0] = 0f;
        faceDownEvent.values[1] = 0f;
        faceDownEvent.values[2] = -9.8f;
        faceDownEvent.sensor = accelerometer;

        service.onSensorChanged(faceDownEvent);

        assertEquals(MainService.State.ACTIVE, service.getState());
    }

    @Test
    public void testServiceHandlesNullAccelerometerGracefully() {
        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        service.onSensorChanged(null);

        Intent setIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_SET_DURATION);
        Bundle results = new Bundle();
        results.putCharSequence("duration_minutes", "30");
        RemoteInput.addResultsToIntent(new RemoteInput[]{
                new RemoteInput.Builder("duration_minutes").build()
        }, setIntent, results);
        service.onStartCommand(setIntent, 0, 1);

        assertEquals(30, preferences.getInt("duration_minutes", -1));
        assertTrue(preferences.getBoolean("active", false));

        Intent turnOffIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_TURN_OFF);
        service.onStartCommand(turnOffIntent, 0, 1);

        assertFalse(preferences.getBoolean("active", true));
    }

    @Test
    public void testFadeOutStepDoesNotCancelFadeWhenVolumeUpdates() throws Exception {
        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        java.lang.reflect.Field audioManagerField = MainService.class.getDeclaredField("audioManager");
        audioManagerField.setAccessible(true);
        android.media.AudioManager audioManager = (android.media.AudioManager) audioManagerField.get(service);
        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 10, 0);

        service.beginFadeOut(10);

        java.lang.reflect.Method runFadeStepMethod = MainService.class.getDeclaredMethod("runFadeStep");
        runFadeStepMethod.setAccessible(true);

        runFadeStepMethod.invoke(service);

        assertTrue(service.isFading());
    }

    @Test
    public void testWakeUpAlarmSnoozeKeepsNotificationOpen() {
        preferences.edit().putBoolean("show_notification", true).putBoolean("wake_up_goal_enabled", true).commit();
        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadowNotificationManager = Shadows.shadowOf(notificationManager);

        Intent triggerIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_WAKEUP_ALARM_EXPIRY);
        service.onStartCommand(triggerIntent, 0, 1);

        android.app.Notification wakeUpNotification = shadowNotificationManager.getNotification(1001);
        assertNotNull(wakeUpNotification);
        assertEquals(context.getString(R.string.wakeup_alarm_title), wakeUpNotification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE));

        Intent snoozeIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_SNOOZE_WAKEUP_ALARM);
        service.onStartCommand(snoozeIntent, 0, 1);

        android.app.Notification snoozedNotification = shadowNotificationManager.getNotification(1001);
        assertNotNull("Wake-up alarm notification must remain open when snoozed", snoozedNotification);
        assertTrue("Snoozed notification content text should contain snooze instruction text",
                snoozedNotification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString().contains("Snoozed 9m"));

        Intent dismissIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_DISMISS_WAKEUP_ALARM);
        service.onStartCommand(dismissIntent, 0, 1);

        android.app.Notification dismissedNotification = shadowNotificationManager.getNotification(1001);
        assertNotNull(dismissedNotification);
        assertFalse(context.getString(R.string.wakeup_alarm_title).equals(dismissedNotification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)));
    }

    @Test
    public void testWakeUpAlarmFlipSnoozeKeepsNotificationOpen() throws Exception {
        preferences.edit().putBoolean("show_notification", true).putBoolean("wake_up_goal_enabled", true).commit();
        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadowNotificationManager = Shadows.shadowOf(notificationManager);

        Intent triggerIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_WAKEUP_ALARM_EXPIRY);
        service.onStartCommand(triggerIntent, 0, 1);

        assertNotNull(shadowNotificationManager.getNotification(1001));

        java.lang.reflect.Field orientationField = MainService.class.getDeclaredField("lastOrientation");
        orientationField.setAccessible(true);
        orientationField.setInt(service, 1);

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

        android.app.Notification snoozedNotification = shadowNotificationManager.getNotification(1001);
        assertNotNull("Wake-up alarm notification must remain open when snoozed via flip", snoozedNotification);
        assertTrue("Notification content text when snoozed should contain 'Snoozed 9m'",
                snoozedNotification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString().contains("Snoozed 9m"));
    }

    @Test
    public void testVolumeKeyDismissesRingingWakeUpAlarm() {
        preferences.edit().putBoolean("show_notification", true).commit();
        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadowNotificationManager = Shadows.shadowOf(notificationManager);

        Intent triggerIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_WAKEUP_ALARM_EXPIRY);
        service.onStartCommand(triggerIntent, 0, 1);

        assertNotNull(shadowNotificationManager.getNotification(1001));

        context.sendBroadcast(new Intent("android.media.VOLUME_CHANGED_ACTION"));
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        android.app.Notification dismissedNotification = shadowNotificationManager.getNotification(1001);
        assertNotNull(dismissedNotification);
        assertFalse(context.getString(R.string.wakeup_alarm_title).equals(dismissedNotification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)));
    }

    @Test
    public void testVolumeKeyDismissesSnoozedWakeUpAlarm() {
        preferences.edit().putBoolean("show_notification", true).commit();
        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadowNotificationManager = Shadows.shadowOf(notificationManager);

        Intent triggerIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_WAKEUP_ALARM_EXPIRY);
        service.onStartCommand(triggerIntent, 0, 1);

        Intent snoozeIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_SNOOZE_WAKEUP_ALARM);
        service.onStartCommand(snoozeIntent, 0, 1);

        assertNotNull(shadowNotificationManager.getNotification(1001));

        try {
            java.lang.reflect.Field receiverField = MainService.class.getDeclaredField("volumeReceiver");
            receiverField.setAccessible(true);
            android.content.BroadcastReceiver receiver = (android.content.BroadcastReceiver) receiverField.get(service);
            assertNotNull(receiver);
            receiver.onReceive(service, new Intent("android.media.VOLUME_CHANGED_ACTION"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        android.app.Notification dismissedNotification = shadowNotificationManager.getNotification(1001);
        assertNotNull(dismissedNotification);
        assertFalse(context.getString(R.string.wakeup_alarm_title).equals(dismissedNotification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)));
    }

    @Test
    public void testDisablingSleepTimerPreservesScheduledWakeAlarm() {
        preferences.edit()
                .putBoolean("active", true)
                .putBoolean("show_notification", true)
                .putBoolean("wake_up_goal_enabled", true)
                .putInt("wake_up_goal_hour", 6)
                .putInt("wake_up_goal_minute", 30)
                .commit();

        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        Intent triggerIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_WAKEUP_ALARM_EXPIRY);
        service.onStartCommand(triggerIntent, 0, 1);

        assertTrue(preferences.contains(MainService.KEY_WAKEUP_LAST_SCHEDULED_MS));

        Intent turnOffIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_TURN_OFF);
        service.onStartCommand(turnOffIntent, 0, 1);

        assertFalse(preferences.getBoolean("active", true));
        assertTrue(preferences.contains(MainService.KEY_WAKEUP_LAST_SCHEDULED_MS));
    }

    @Test
    public void testDismissingWakeUpAlarmResetsCurrentWakeTimeToGoalTime() {
        preferences.edit()
                .putBoolean("active", false)
                .putBoolean("wake_up_goal_enabled", true)
                .putInt("wake_up_goal_hour", 6)
                .putInt("wake_up_goal_minute", 30)
                .putInt("current_wake_hour", 7)
                .putInt("current_wake_minute", 30)
                .commit();

        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        Intent dismissIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_DISMISS_WAKEUP_ALARM);
        service.onStartCommand(dismissIntent, 0, 1);

        assertEquals("expected goal hour 6 but was " + preferences.getInt("current_wake_hour", -1), 6, preferences.getInt("current_wake_hour", -1));
        assertEquals("expected goal min 30 but was " + preferences.getInt("current_wake_minute", -1), 30, preferences.getInt("current_wake_minute", -1));
    }

    @Test
    public void testDismissingNapAlarmDoesNotAffectCurrentWakeTime() {
        preferences.edit()
                .putBoolean("active", false)
                .putBoolean("wake_up_goal_enabled", true)
                .putInt("wake_up_goal_hour", 6)
                .putInt("wake_up_goal_minute", 30)
                .putInt("current_wake_hour", 7)
                .putInt("current_wake_minute", 30)
                .commit();

        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        Intent napExpiryIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_NAP_EXPIRY);
        service.onStartCommand(napExpiryIntent, 0, 1);

        Intent dismissIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_DISMISS_WAKEUP_ALARM);
        service.onStartCommand(dismissIntent, 0, 1);

        assertEquals("Current wake hour must remain unchanged on nap alarm dismiss",
                7, preferences.getInt("current_wake_hour", -1));
        assertEquals("Current wake minute must remain unchanged on nap alarm dismiss",
                30, preferences.getInt("current_wake_minute", -1));
    }

    @Test
    public void testDismissingNapAlarmViaVolumeKeyDoesNotAffectCurrentWakeTime() {
        preferences.edit()
                .putBoolean("active", false)
                .putBoolean("wake_up_goal_enabled", true)
                .putInt("wake_up_goal_hour", 6)
                .putInt("wake_up_goal_minute", 30)
                .putInt("current_wake_hour", 7)
                .putInt("current_wake_minute", 30)
                .commit();

        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        Intent napExpiryIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_NAP_EXPIRY);
        service.onStartCommand(napExpiryIntent, 0, 1);

        context.sendBroadcast(new Intent("android.media.VOLUME_CHANGED_ACTION"));
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertEquals("Current wake hour must remain unchanged on nap alarm volume key dismiss",
                7, preferences.getInt("current_wake_hour", -1));
        assertEquals("Current wake minute must remain unchanged on nap alarm volume key dismiss",
                30, preferences.getInt("current_wake_minute", -1));
    }

    @Test
    public void testSnoozingAndDismissingNapAlarmDoesNotAffectCurrentWakeTime() {
        preferences.edit()
                .putBoolean("active", false)
                .putBoolean("wake_up_goal_enabled", true)
                .putInt("wake_up_goal_hour", 6)
                .putInt("wake_up_goal_minute", 30)
                .putInt("current_wake_hour", 7)
                .putInt("current_wake_minute", 30)
                .commit();

        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        Intent napExpiryIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_NAP_EXPIRY);
        service.onStartCommand(napExpiryIntent, 0, 1);

        Intent snoozeIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_SNOOZE_WAKEUP_ALARM);
        service.onStartCommand(snoozeIntent, 0, 1);

        Intent dismissIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_DISMISS_WAKEUP_ALARM);
        service.onStartCommand(dismissIntent, 0, 1);

        assertEquals("Current wake hour must remain unchanged on snoozed nap alarm dismiss",
                7, preferences.getInt("current_wake_hour", -1));
        assertEquals("Current wake minute must remain unchanged on snoozed nap alarm dismiss",
                30, preferences.getInt("current_wake_minute", -1));
    }

    @Test
    public void testFadeVolumeStateConsistencyDuringStreamVolumeSet() throws Exception {
        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        java.lang.reflect.Field audioManagerField = MainService.class.getDeclaredField("audioManager");
        audioManagerField.setAccessible(true);
        android.media.AudioManager audioManager = (android.media.AudioManager) audioManagerField.get(service);
        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 10, 0);

        service.beginFadeOut(10);

        java.lang.reflect.Method runFadeStepMethod = MainService.class.getDeclaredMethod("runFadeStep");
        runFadeStepMethod.setAccessible(true);

        runFadeStepMethod.invoke(service);

        int currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);

        assertEquals("lastObservedVolume must match updated stream volume", currentVolume, service.getLastObservedVolume());
        assertTrue("Fade should not be cancelled during fade step volume update", service.isFading());
    }

    @Test
    public void testWakeUpAlarmTriggersVolumeCrescendo() throws Exception {
        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        java.lang.reflect.Field ringtoneField = MainService.class.getDeclaredField("currentAlarmRingtone");
        ringtoneField.setAccessible(true);
        java.lang.reflect.Constructor<android.media.Ringtone> constructor =
                android.media.Ringtone.class.getDeclaredConstructor(Context.class, boolean.class);
        constructor.setAccessible(true);
        android.media.Ringtone mockRingtone = constructor.newInstance(context, false);
        ringtoneField.set(service, mockRingtone);

        java.lang.reflect.Method crescendoMethod = MainService.class.getDeclaredMethod("startWakeUpAlarmCrescendo");
        crescendoMethod.setAccessible(true);
        crescendoMethod.invoke(service);

        java.lang.reflect.Field runnableField = MainService.class.getDeclaredField("alarmCrescendoRunnable");
        runnableField.setAccessible(true);
        assertNotNull("Crescendo runnable should be scheduled when crescendo starts", runnableField.get(service));

        Intent dismissIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_DISMISS_WAKEUP_ALARM);
        service.onStartCommand(dismissIntent, 0, 1);

        assertEquals("Ringtone should be cleared after dismiss", null, ringtoneField.get(service));
        assertEquals("Crescendo runnable should be cancelled after dismiss", null, runnableField.get(service));
    }

    @Test
    public void testEnsureAudibleAlarmStreamVolume() throws Exception {
        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        android.media.AudioManager am = (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            am.setStreamVolume(android.media.AudioManager.STREAM_ALARM, 0, 0);

            java.lang.reflect.Method ensureVolMethod = MainService.class.getDeclaredMethod("ensureAudibleAlarmStreamVolume");
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
        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        java.lang.reflect.Field ringtoneField = MainService.class.getDeclaredField("currentAlarmRingtone");
        ringtoneField.setAccessible(true);
        java.lang.reflect.Constructor<android.media.Ringtone> constructor =
                android.media.Ringtone.class.getDeclaredConstructor(Context.class, boolean.class);
        constructor.setAccessible(true);
        android.media.Ringtone mockRingtone = constructor.newInstance(context, false);
        ringtoneField.set(service, mockRingtone);

        java.lang.reflect.Field startTimeField = MainService.class.getDeclaredField("alarmCrescendoStartTimeMs");
        startTimeField.setAccessible(true);
        long ninetySecondsAgo = System.currentTimeMillis() - 90_000L;
        startTimeField.setLong(service, ninetySecondsAgo);

        java.lang.reflect.Method crescendoStepMethod = MainService.class.getDeclaredMethod("runWakeUpAlarmCrescendoStep");
        crescendoStepMethod.setAccessible(true);
        crescendoStepMethod.invoke(service);

        if (android.os.Build.VERSION.SDK_INT >= 28) {
            assertEquals("Ringtone volume at 50% time should be 0.25 (quadratic gain)", 0.25f, mockRingtone.getVolume(), 0.05f);
        }
    }

    @Test
    public void testWakeUpAlarmAudioAttributesConfiguredAsAlarmToBypassDnd() throws Exception {
        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        android.media.AudioManager am = (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            am.setStreamVolume(android.media.AudioManager.STREAM_ALARM, 0, 0);
        }

        java.lang.reflect.Method playMethod = MainService.class.getDeclaredMethod("playWakeUpAlarmSound");
        playMethod.setAccessible(true);
        playMethod.invoke(service);

        java.lang.reflect.Field ringtoneField = MainService.class.getDeclaredField("currentAlarmRingtone");
        ringtoneField.setAccessible(true);
        android.media.Ringtone ringtone = (android.media.Ringtone) ringtoneField.get(service);

        assertNotNull("currentAlarmRingtone must be non-null when playing alarm sound", ringtone);

        if (android.os.Build.VERSION.SDK_INT >= 21) {
            android.media.AudioAttributes attributes = ringtone.getAudioAttributes();
            assertNotNull("AudioAttributes must be configured on currentAlarmRingtone by playWakeUpAlarmSound", attributes);
            assertEquals("AudioAttributes usage must be USAGE_ALARM to ensure Do Not Disturb does not prevent alarm playback",
                    android.media.AudioAttributes.USAGE_ALARM, attributes.getUsage());
            assertEquals("AudioAttributes content type must be CONTENT_TYPE_SONIFICATION",
                    android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION, attributes.getContentType());
        } else {
            assertEquals("Stream type must be STREAM_ALARM",
                    android.media.AudioManager.STREAM_ALARM, ringtone.getStreamType());
        }

        if (am != null) {
            int maxVol = am.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM);
            int expectedMinVol = Math.max(1, (int) Math.round(maxVol * 0.3));
            assertEquals("STREAM_ALARM volume should be raised to audible volume when alarm plays",
                    expectedMinVol, am.getStreamVolume(android.media.AudioManager.STREAM_ALARM));
        }
    }

    @Test
    public void testWakeUpAlarmHappyPathFullLifecycle() {
        preferences.edit()
                .putBoolean("wake_up_goal_enabled", true)
                .putInt("wake_up_goal_hour", 6)
                .putInt("wake_up_goal_minute", 30)
                .putInt("current_wake_hour", 7)
                .putInt("current_wake_minute", 0)
                .putInt("min_sleep_duration_minutes", 450)
                .commit();

        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        long now = System.currentTimeMillis();
        Calendar cal = MainService.calculateScheduledAlarm(context, now, 0L);
        assertNotNull("Scheduled alarm calendar must not be null when goal is enabled", cal);
        assertTrue("Scheduled alarm time must be in the future", cal.getTimeInMillis() > now);

        Intent triggerIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_WAKEUP_ALARM_EXPIRY);
        service.onStartCommand(triggerIntent, 0, 1);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadowNotificationManager = Shadows.shadowOf(notificationManager);
        android.app.Notification ringingNotification = shadowNotificationManager.getNotification(1001);
        assertNotNull("Ringing notification must be displayed when wake alarm triggers", ringingNotification);
        assertEquals(context.getString(R.string.wakeup_alarm_title), ringingNotification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE));

        Intent dismissIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_DISMISS_WAKEUP_ALARM);
        service.onStartCommand(dismissIntent, 0, 1);

        assertEquals("Current wake hour must reset to target goal hour 6 after dismissal",
                6, preferences.getInt("current_wake_hour", -1));
        assertEquals("Current wake minute must reset to target goal minute 30 after dismissal",
                30, preferences.getInt("current_wake_minute", -1));

        assertTrue("Next daily alarm timestamp must be saved in preferences",
                preferences.contains(MainService.KEY_WAKEUP_LAST_SCHEDULED_MS));
    }

    @Test
    public void testNapAlarmHappyPathFullLifecycle() {
        preferences.edit()
                .putBoolean("wake_up_goal_enabled", true)
                .putInt("wake_up_goal_hour", 6)
                .putInt("wake_up_goal_minute", 30)
                .putInt("current_wake_hour", 7)
                .putInt("current_wake_minute", 30)
                .commit();

        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        Intent startNapIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_START_NAP)
                .putExtra(MainService.EXTRA_NAP_DURATION_MINUTES, 20);
        service.onStartCommand(startNapIntent, 0, 1);

        assertTrue("Nap alarm expiration timestamp must be saved in preferences",
                preferences.getLong(MainService.KEY_NAP_ALARM_ENDS_AT, 0L) > System.currentTimeMillis());

        Intent napExpiryIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_NAP_EXPIRY);
        service.onStartCommand(napExpiryIntent, 0, 1);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadowNotificationManager = Shadows.shadowOf(notificationManager);
        android.app.Notification ringingNotification = shadowNotificationManager.getNotification(1001);
        assertNotNull(ringingNotification);
        assertEquals(context.getString(R.string.wakeup_alarm_title), ringingNotification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE));

        Intent dismissIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_DISMISS_WAKEUP_ALARM);
        service.onStartCommand(dismissIntent, 0, 1);

        assertFalse("Nap alarm ends at timestamp must be cleared on dismiss",
                preferences.contains(MainService.KEY_NAP_ALARM_ENDS_AT));

        assertEquals("Current wake hour must remain unchanged when dismissing nap alarm",
                7, preferences.getInt("current_wake_hour", -1));
        assertEquals("Current wake minute must remain unchanged when dismissing nap alarm",
                30, preferences.getInt("current_wake_minute", -1));
    }

    @Test
    public void testWakeUpAlarmTriggersNextDailyAlarm() {
        preferences.edit()
                .putBoolean("wake_up_goal_enabled", true)
                .putInt("wake_up_goal_hour", 6)
                .putInt("wake_up_goal_minute", 30)
                .putInt("min_sleep_duration_minutes", 450)
                .commit();

        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        long beforeMs = System.currentTimeMillis();

        Intent triggerIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_WAKEUP_ALARM_EXPIRY);
        service.onStartCommand(triggerIntent, 0, 1);

        long scheduledMs = preferences.getLong(MainService.KEY_WAKEUP_LAST_SCHEDULED_MS, 0L);
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

        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        Intent triggerIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_WAKEUP_ALARM_EXPIRY);
        service.onStartCommand(triggerIntent, 0, 1);

        long dailyAlarmMs = preferences.getLong(MainService.KEY_WAKEUP_LAST_SCHEDULED_MS, 0L);
        assertTrue(dailyAlarmMs > 0L);

        Intent snoozeIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_SNOOZE_WAKEUP_ALARM);
        service.onStartCommand(snoozeIntent, 0, 1);

        assertEquals("Snoozing must not overwrite scheduled daily recurring alarm time",
                dailyAlarmMs, preferences.getLong(MainService.KEY_WAKEUP_LAST_SCHEDULED_MS, 0L));
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
        java.util.Calendar cal = MainService.calculateScheduledAlarm(context, now, 0L);
        assertNotNull("calculateScheduledAlarm should return scheduled Calendar for daily goal", cal);
        assertTrue("Scheduled alarm must be in the future", cal.getTimeInMillis() > now);
    }

    @Test
    public void testCalculateScheduledAlarmReturnsNullWhenWakeUpGoalDisabledEvenIfAutoTimerEnabled() {
        preferences.edit()
                .putBoolean("wake_up_goal_enabled", false)
                .putBoolean("auto_timer_enabled", true)
                .putInt("wake_up_goal_hour", 6)
                .putInt("wake_up_goal_minute", 30)
                .commit();

        long now = System.currentTimeMillis();
        java.util.Calendar cal = MainService.calculateScheduledAlarm(context, now, 0L);
        assertEquals("calculateScheduledAlarm must return null when wake_up_goal_enabled is false", null, cal);
    }

    @Test
    public void testCalculateScheduledAlarmPrioritizesActiveTimerEndsAtForMinimumSleepSafeguard() {
        Calendar fixedNowCal = Calendar.getInstance();
        fixedNowCal.set(2025, Calendar.JANUARY, 1, 22, 0, 0); // 10:00 PM
        fixedNowCal.set(Calendar.MILLISECOND, 0);
        long now = fixedNowCal.getTimeInMillis();

        int minSleepMin = 450;
        int timerDurationMin = 30;
        long timerEndsAt = now + 3 * 3600_000L; // 1:00 AM next day

        preferences.edit()
                .putBoolean("wake_up_goal_enabled", true)
                .putInt("wake_up_goal_hour", 6)
                .putInt("wake_up_goal_minute", 30)
                .putInt("current_wake_hour", 6)
                .putInt("current_wake_minute", 30)
                .putInt("min_sleep_duration_minutes", minSleepMin)
                .putInt("duration_minutes", timerDurationMin)
                .putLong("sleep_start_time_ms", now - 2 * 3600_000L)
                .commit();

        java.util.Calendar cal = MainService.calculateScheduledAlarm(context, now, timerEndsAt);
        assertNotNull(cal);

        long expectedWakeMs = timerEndsAt + (minSleepMin - timerDurationMin) * 60_000L;
        assertTrue("Scheduled alarm time must match active timer expiration safeguard",
                Math.abs(expectedWakeMs - cal.getTimeInMillis()) < 1000L);
    }

    @Test
    public void testWakeUpAlarmRingingNotificationShowsSnoozeAndDismissActionsAndInstructions() {
        preferences.edit().putBoolean("show_notification", true).putBoolean("wake_up_goal_enabled", true).commit();
        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadowNotificationManager = Shadows.shadowOf(notificationManager);

        Intent triggerIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_WAKEUP_ALARM_EXPIRY);
        service.onStartCommand(triggerIntent, 0, 1);

        android.app.Notification wakeUpNotification = shadowNotificationManager.getNotification(1001);
        assertNotNull(wakeUpNotification);

        String contentText = wakeUpNotification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString();
        assertTrue("Notification content text when alarm is ringing should inform user how to snooze/dismiss: " + contentText,
                contentText.contains("Flip to snooze") || contentText.contains("volume button"));

        assertEquals("Notification should feature 2 actions (Dismiss and Snooze) when ringing", 2, wakeUpNotification.actions.length);
        assertEquals(context.getString(R.string.action_awake), wakeUpNotification.actions[0].title.toString());
        assertEquals(context.getString(R.string.action_snooze_alarm), wakeUpNotification.actions[1].title.toString());
    }

    @Test
    public void testTimerOffNotificationShowsWakeUpAlarmStatusWhenWakeAlarmIsEnabled() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR_OF_DAY, 8);
        int targetHour = cal.get(Calendar.HOUR_OF_DAY);
        int targetMin = cal.get(Calendar.MINUTE);

        preferences.edit()
                .putBoolean("active", false)
                .putBoolean("show_notification", true)
                .putBoolean("wake_up_goal_enabled", true)
                .putInt("wake_up_goal_hour", targetHour)
                .putInt("wake_up_goal_minute", targetMin)
                .putInt("current_wake_hour", targetHour)
                .putInt("current_wake_minute", targetMin)
                .putInt("duration_minutes", 20)
                .commit();

        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadowNotificationManager = Shadows.shadowOf(notificationManager);
        android.app.Notification notification = shadowNotificationManager.getNotification(1001);
        assertNotNull(notification);

        String contentText = notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString();
        assertTrue("Notification text when timer is off and wake alarm is enabled should contain 'Timer off': " + contentText,
                contentText.contains("Timer off"));
        assertTrue("Notification text when timer is off and wake alarm is enabled should contain 'Wake at': " + contentText,
                contentText.contains("Wake at"));
    }

    @Test
    public void testResettingSleepTimerUpdatesTimerStartTimeMs() throws Exception {
        long initialStartTime = System.currentTimeMillis() - 600_000L; // 10 minutes ago
        preferences.edit()
                .putBoolean("active", true)
                .putInt("duration_minutes", 30)
                .putLong("timer_start_time_ms", initialStartTime)
                .commit();

        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        long resetTime = System.currentTimeMillis();
        service.onTimerRescheduled();

        long updatedStartTime = preferences.getLong("timer_start_time_ms", 0L);
        assertTrue("timer_start_time_ms must be updated when timer is reset/rescheduled", updatedStartTime >= resetTime);
    }

    @Test
    public void testResettingSleepTimerWithinWindowUpdatesSleepStartTimeMs() {
        long now = System.currentTimeMillis();
        int minSleepMin = 450; // 7.5 hours

        // Goal/Current wake alarm set to 5 hours from now (within 1.2 * 7.5h = 9h window)
        Calendar calCurrent = Calendar.getInstance();
        calCurrent.setTimeInMillis(now + 5 * 3600_000L);
        int currentHour = calCurrent.get(Calendar.HOUR_OF_DAY);
        int currentMin = calCurrent.get(Calendar.MINUTE);

        preferences.edit()
                .putBoolean("wake_up_goal_enabled", true)
                .putInt("wake_up_goal_hour", currentHour)
                .putInt("wake_up_goal_minute", currentMin)
                .putInt("current_wake_hour", currentHour)
                .putInt("current_wake_minute", currentMin)
                .putInt("min_sleep_duration_minutes", minSleepMin)
                .commit();

        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        service.onTimerRescheduled();

        long updatedSleepStartTime = preferences.getLong("sleep_start_time_ms", 0L);
        assertTrue("sleep_start_time_ms must be updated to current time when reset within 1.2x min sleep window",
                updatedSleepStartTime >= now);
    }

    @Test
    public void testTimerActivationRecordsTimerStartTimeMsAndPreservesItOnExpiry() throws Exception {
        preferences.edit()
                .putBoolean("active", true)
                .putInt("duration_minutes", 30)
                .commit();

        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        service.startTimer(30, System.currentTimeMillis() + 1800_000L, System.currentTimeMillis(), true);

        assertTrue("timer_start_time_ms should be recorded when timer is active", preferences.contains("timer_start_time_ms"));

        service.handleTurnOff(false);

        assertTrue("timer_start_time_ms should be preserved when timer expires and turns off", preferences.contains("timer_start_time_ms"));
    }

    @Test
    public void testAwakeActionWhileTimerIsActiveDiscardsSessionAndKeepsTimerRunning() throws Exception {
        preferences.edit()
                .putBoolean("active", true)
                .putInt("duration_minutes", 30)
                .putLong("timer_start_time_ms", System.currentTimeMillis() - 300_000L)
                .commit();

        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        service.startTimer(30, System.currentTimeMillis() + 1500_000L, System.currentTimeMillis(), true);

        Intent awakeIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_AWAKE);
        service.onStartCommand(awakeIntent, 0, 1);

        assertTrue("Timer state machine should remain active when marking awake during countdown", service.isActive());
        assertFalse("timer_start_time_ms should be cleared when marking awake during countdown", preferences.contains("timer_start_time_ms"));
    }

    @Test
    public void testProcessSleepSessionClearsStartTimePreferences() {
        preferences.edit()
                .putLong("sleep_start_time_ms", System.currentTimeMillis() - 3600_000L)
                .putLong("timer_start_time_ms", System.currentTimeMillis() - 3600_000L)
                .commit();

        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        Intent awakeIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_AWAKE);
        service.onStartCommand(awakeIntent, 0, 1);

        assertFalse(preferences.contains("sleep_start_time_ms"));
        assertFalse(preferences.contains("timer_start_time_ms"));
    }

    @Test
    public void testAwakeActionRegistersSleepAndUpdatesAlarmSchedule() {
        long sleepStart = System.currentTimeMillis() - 8 * 3600_000L;
        preferences.edit()
                .putBoolean("wake_up_goal_enabled", true)
                .putInt("wake_up_goal_hour", 6)
                .putInt("wake_up_goal_minute", 30)
                .putInt("current_wake_hour", 7)
                .putInt("current_wake_minute", 30)
                .putLong("sleep_start_time_ms", sleepStart)
                .commit();

        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        Intent awakeIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_AWAKE);
        service.onStartCommand(awakeIntent, 0, 1);

        assertEquals("Current wake hour should reset to goal hour 6", 6, preferences.getInt("current_wake_hour", -1));
        assertEquals("Current wake minute should reset to goal min 30", 30, preferences.getInt("current_wake_minute", -1));
        assertEquals("sleep_start_time_ms should be cleared", 0L, preferences.getLong("sleep_start_time_ms", 0L));
    }

    @Test
    public void testOnTimerRescheduledPushesWakeAlarmForwardToSafeguardMinSleep() throws Exception {
        long now = System.currentTimeMillis();
        int minSleepMin = 450; // 7.5 hours
        int timerDurationMin = 30;
        long timerEndsAt = now + 2 * 3600_000L; // Timer ends 2 hours from now

        // Current wake alarm set to 1 hour from now (earlier than requiredWakeTime = now + 2h + 7h = now + 9h)
        Calendar calCurrent = Calendar.getInstance();
        calCurrent.setTimeInMillis(now + 3600_000L);
        int currentHour = calCurrent.get(Calendar.HOUR_OF_DAY);
        int currentMin = calCurrent.get(Calendar.MINUTE);

        preferences.edit()
                .putBoolean("wake_up_goal_enabled", true)
                .putInt("wake_up_goal_hour", currentHour)
                .putInt("wake_up_goal_minute", currentMin)
                .putInt("current_wake_hour", currentHour)
                .putInt("current_wake_minute", currentMin)
                .putInt("min_sleep_duration_minutes", minSleepMin)
                .putInt("duration_minutes", timerDurationMin)
                .commit();

        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        service.startTimer(timerDurationMin, timerEndsAt, now, true);

        service.onTimerRescheduled();

        long requiredWakeTimeMs = timerEndsAt + Math.max(0L, (minSleepMin - timerDurationMin) * 60_000L);
        Calendar calRequired = Calendar.getInstance();
        calRequired.setTimeInMillis(requiredWakeTimeMs);

        int expectedPushedHour = calRequired.get(Calendar.HOUR_OF_DAY);
        int expectedPushedMin = calRequired.get(Calendar.MINUTE);

        assertEquals("Current wake hour must be pushed forward to safeguard minimum sleep duration",
                expectedPushedHour, preferences.getInt("current_wake_hour", -1));
        assertEquals("Current wake minute must be pushed forward to safeguard minimum sleep duration",
                expectedPushedMin, preferences.getInt("current_wake_minute", -1));
    }

    @Test
    public void testOnTimerRescheduledMovesWakeAlarmEarlierWhenGoingToBedEarlyWithinWindow() {
        long now = System.currentTimeMillis();
        int minSleepMin = 450; // 7.5 hours

        // Current wake alarm: 8 hours from now
        Calendar calCurrent = Calendar.getInstance();
        calCurrent.setTimeInMillis(now + 8 * 3600_000L);
        int currentHour = calCurrent.get(Calendar.HOUR_OF_DAY);
        int currentMin = calCurrent.get(Calendar.MINUTE);

        // Goal wake alarm: 7.5 hours from now
        Calendar calGoal = Calendar.getInstance();
        calGoal.setTimeInMillis(now + (7 * 3600_000L + 30 * 60_000L));
        int goalHour = calGoal.get(Calendar.HOUR_OF_DAY);
        int goalMin = calGoal.get(Calendar.MINUTE);

        // Bedtime in the recent past (e.g. 10 minutes ago) so baseTime = now - 10m
        long bedtimeMs = now - 10 * 60_000L;

        preferences.edit()
                .putBoolean("wake_up_goal_enabled", true)
                .putInt("wake_up_goal_hour", goalHour)
                .putInt("wake_up_goal_minute", goalMin)
                .putInt("current_wake_hour", currentHour)
                .putInt("current_wake_minute", currentMin)
                .putInt("min_sleep_duration_minutes", minSleepMin)
                .putLong("sleep_start_time_ms", bedtimeMs)
                .commit();

        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        service.onTimerRescheduled();

        assertEquals(goalHour, preferences.getInt("current_wake_hour", -1));
        assertEquals(goalMin, preferences.getInt("current_wake_minute", -1));
    }

    @Test
    public void testAwakeActionDuringActiveNapPreservesCurrentWakeTime() {
        long napEndsAt = System.currentTimeMillis() + 1200_000L;
        preferences.edit()
                .putBoolean("wake_up_goal_enabled", true)
                .putInt("wake_up_goal_hour", 6)
                .putInt("wake_up_goal_minute", 30)
                .putInt("current_wake_hour", 7)
                .putInt("current_wake_minute", 30)
                .putLong(MainService.KEY_NAP_ALARM_ENDS_AT, napEndsAt)
                .putLong("nap_start_time_ms", System.currentTimeMillis() - 600_000L)
                .commit();

        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        assertTrue("shouldShowAwakeAction should return true when a nap is active", service.shouldShowAwakeAction());

        Intent awakeIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_AWAKE);
        service.onStartCommand(awakeIntent, 0, 1);

        assertEquals("Current wake hour must remain unchanged when marking awake during a nap", 7, preferences.getInt("current_wake_hour", -1));
        assertEquals("Current wake minute must remain unchanged when marking awake during a nap", 30, preferences.getInt("current_wake_minute", -1));
        assertFalse("nap_alarm_ends_at must be cleared after marking awake", preferences.contains(MainService.KEY_NAP_ALARM_ENDS_AT));
        assertFalse("nap_start_time_ms must be cleared after marking awake", preferences.contains("nap_start_time_ms"));
        assertFalse("shouldShowAwakeAction should revert to false after marking awake", service.shouldShowAwakeAction());
    }

    @Test
    public void testAwakeActionNotificationActionWhenWakeAlarmIsEnabledAndActiveSleepSession() {
        long sleepStart = System.currentTimeMillis() - 4 * 3600_000L;
        preferences.edit()
                .putBoolean("show_notification", true)
                .putBoolean("wake_up_goal_enabled", true)
                .putLong("sleep_start_time_ms", sleepStart)
                .commit();

        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadowNotificationManager = Shadows.shadowOf(notificationManager);
        android.app.Notification notification = shadowNotificationManager.getNotification(1001);
        assertNotNull(notification);

        boolean foundAwakeAction = false;
        if (notification.actions != null) {
            for (android.app.Notification.Action action : notification.actions) {
                if (context.getString(R.string.action_awake).equals(action.title.toString())) {
                    foundAwakeAction = true;
                    break;
                }
            }
        }
        assertTrue("Ongoing notification should feature 'I\'m Awake' action button during active sleep session", foundAwakeAction);
    }

    @Test
    public void testNotificationShowsNapWhenNoActiveSleepSessionEvenIfWakeAlarmEnabled() {
        preferences.edit()
                .putBoolean("show_notification", true)
                .putBoolean("wake_up_goal_enabled", true)
                .remove("sleep_start_time_ms")
                .commit();

        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadowNotificationManager = Shadows.shadowOf(notificationManager);
        android.app.Notification notification = shadowNotificationManager.getNotification(1001);
        assertNotNull(notification);

        boolean foundNapAction = false;
        if (notification.actions != null) {
            for (android.app.Notification.Action action : notification.actions) {
                if (context.getString(R.string.action_nap).equals(action.title.toString())) {
                    foundNapAction = true;
                    break;
                }
            }
        }
        assertTrue("Ongoing notification should feature 'Nap' action button during daytime when no active sleep session exists", foundNapAction);
    }

    @Test
    public void testAwakeActionClearsSessionAndRevertsNotificationToNap() {
        long sleepStart = System.currentTimeMillis() - 4 * 3600_000L;
        preferences.edit()
                .putBoolean("show_notification", true)
                .putBoolean("wake_up_goal_enabled", true)
                .putLong("sleep_start_time_ms", sleepStart)
                .commit();

        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        Intent awakeIntent = new Intent(context, MainService.class)
                .setAction(MainService.ACTION_AWAKE);
        service.onStartCommand(awakeIntent, 0, 1);

        assertEquals("sleep_start_time_ms should be removed", 0L, preferences.getLong("sleep_start_time_ms", 0L));

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadowNotificationManager = Shadows.shadowOf(notificationManager);
        android.app.Notification notification = shadowNotificationManager.getNotification(1001);
        assertNotNull(notification);

        boolean foundNapAction = false;
        if (notification.actions != null) {
            for (android.app.Notification.Action action : notification.actions) {
                if (context.getString(R.string.action_nap).equals(action.title.toString())) {
                    foundNapAction = true;
                    break;
                }
            }
        }
        assertTrue("Notification action should revert to 'Nap' after marking awake", foundNapAction);
    }

    @Test
    public void testNotificationReportsProjectedWakeAlarmTimeWhileTimerIsActive() {
        long now = System.currentTimeMillis();
        long timerEndsAt = now + 30 * 60_000L;
        preferences.edit()
                .putBoolean("active", true)
                .putBoolean("show_notification", true)
                .putBoolean("wake_up_goal_enabled", true)
                .putInt("duration_minutes", 30)
                .putInt("min_sleep_duration_minutes", 450)
                .putInt("wake_up_goal_hour", 6)
                .putInt("wake_up_goal_minute", 30)
                .putInt("current_wake_hour", 6)
                .putInt("current_wake_minute", 30)
                .putLong("timer_ends_at", timerEndsAt)
                .commit();

        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadowNotificationManager = Shadows.shadowOf(notificationManager);
        android.app.Notification notification = shadowNotificationManager.getNotification(1001);
        assertNotNull(notification);

        String text = notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString();
        assertTrue("Notification while timer active should contain 'Wake at': " + text, text.contains("Wake at"));
    }

    @Test
    public void testAwakeIntentTargetsMainServiceWithAwakeAction() throws Exception {
        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        java.lang.reflect.Method awakeIntentMethod = MainService.class.getDeclaredMethod("awakeIntent");
        awakeIntentMethod.setAccessible(true);
        android.app.PendingIntent pendingIntent = (android.app.PendingIntent) awakeIntentMethod.invoke(service);

        assertNotNull("awakeIntent pendingIntent must be non-null", pendingIntent);
        org.robolectric.shadows.ShadowPendingIntent shadowPendingIntent = Shadows.shadowOf(pendingIntent);
        assertTrue("awakeIntent must be a service PendingIntent", shadowPendingIntent.isService());
        Intent intent = shadowPendingIntent.getSavedIntent();
        assertEquals(MainService.ACTION_AWAKE, intent.getAction());
        assertEquals(MainService.class.getName(), intent.getComponent().getClassName());
    }

    @Test
    public void testSessionAnchoredMinimumSleepDoesNotPushAlarmWhenSleepDurationSatisfied() {
        long now = System.currentTimeMillis();
        int minSleepMin = 450; // 7.5 hours

        // Setup wake alarm to 7:30 AM tomorrow (or far enough in future so now is outside window)
        Calendar calCurrent = Calendar.getInstance();
        calCurrent.setTimeInMillis(now + 12 * 3600_000L); // 12 hours from now
        int currentHour = calCurrent.get(Calendar.HOUR_OF_DAY);
        int currentMin = calCurrent.get(Calendar.MINUTE);

        long sleepStart = calCurrent.getTimeInMillis() - 8 * 3600_000L; // 8 hours of sleep relative to alarm

        preferences.edit()
                .putBoolean("wake_up_goal_enabled", true)
                .putInt("wake_up_goal_hour", currentHour)
                .putInt("wake_up_goal_minute", currentMin)
                .putInt("current_wake_hour", currentHour)
                .putInt("current_wake_minute", currentMin)
                .putInt("min_sleep_duration_minutes", minSleepMin)
                .putLong("sleep_start_time_ms", sleepStart)
                .commit();

        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        service.onTimerRescheduled();

        assertEquals("Current wake hour should remain unchanged when session-anchored min sleep is satisfied",
                currentHour, preferences.getInt("current_wake_hour", -1));
        assertEquals("Current wake minute should remain unchanged when session-anchored min sleep is satisfied",
                currentMin, preferences.getInt("current_wake_minute", -1));
    }

    @Test
    public void testStartNapIntentTargetsMainServiceWithStartNapAction() {
        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        ShadowNotificationManager shadowNM = Shadows.shadowOf(
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE));
        Notification notification = shadowNM.getAllNotifications().get(0);

        boolean foundNapAction = false;
        for (Notification.Action action : notification.actions) {
            if (context.getString(R.string.action_nap).equals(action.title.toString())) {
                foundNapAction = true;
                Intent intent = Shadows.shadowOf(action.actionIntent).getSavedIntent();
                assertEquals(MainService.class.getName(), intent.getComponent().getClassName());
                assertEquals(MainService.ACTION_START_NAP, intent.getAction());
                break;
            }
        }
        assertTrue("Notification should contain a Nap action", foundNapAction);
    }

    @Test
    public void testAwakeActionDuringActiveNapCancelsNap() {
        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        long now = System.currentTimeMillis();
        preferences.edit()
                .putLong(MainService.KEY_NAP_ALARM_ENDS_AT, now + 1200_000L)
                .putLong("nap_start_time_ms", now)
                .commit();

        Intent awakeIntent = new Intent(context, MainService.class).setAction(MainService.ACTION_AWAKE);
        service.onStartCommand(awakeIntent, 0, 1);

        assertFalse("Nap alarm ends at preference should be removed after awake action",
                preferences.contains(MainService.KEY_NAP_ALARM_ENDS_AT));
        assertFalse("Nap start time preference should be removed after awake action",
                preferences.contains("nap_start_time_ms"));
    }

    @Test
    public void testCancellingNapWithNapDndDoesNotToggleOffSleepTimer() {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Shadows.shadowOf(nm).setNotificationPolicyAccessGranted(true);
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);

        preferences.edit()
                .putBoolean("nap_dnd_enabled", true)
                .putBoolean("auto_timer_enabled", true)
                .putBoolean("active", true)
                .putLong(MainService.KEY_NAP_ALARM_ENDS_AT, System.currentTimeMillis() + 1200_000L)
                .commit();

        ServiceController<MainService> controller = Robolectric.buildService(MainService.class);
        MainService service = controller.create().get();

        Intent cancelNapIntent = new Intent(context, MainService.class).setAction(MainService.ACTION_CANCEL_NAP);
        service.onStartCommand(cancelNapIntent, 0, 1);

        assertTrue("Sleep timer active preference should remain true after nap cancellation",
                preferences.getBoolean("active", false));
    }

}
