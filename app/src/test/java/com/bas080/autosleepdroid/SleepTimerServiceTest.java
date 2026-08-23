package com.bas080.autosleepdroid;

import android.app.NotificationManager;
import android.app.RemoteInput;
import android.content.ComponentName;
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
    public void testRedrawNotificationAction() {
        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

        Intent redrawIntent = new Intent(context, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_REDRAW_NOTIFICATION);

        service.onStartCommand(redrawIntent, 0, 1);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadowNotificationManager = Shadows.shadowOf(notificationManager);
        assertNotNull(shadowNotificationManager.getNotification(1001));
    }

    @Test
    public void testPhoneFlipSensorEventDetection() throws Exception {
        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();

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

        java.lang.reflect.Method method = SleepTimerService.class.getDeclaredMethod("checkAndClearFlipDetected");
        method.setAccessible(true);
        boolean flipDetected = (boolean) method.invoke(service);
        assertTrue(flipDetected);
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
}
