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

        // Grant notification listener access so the service can process actions
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadowNotificationManager = Shadows.shadowOf(notificationManager);
        shadowNotificationManager.setNotificationListenerAccessGranted(
                new ComponentName(context, MediaSessionAccessService.class), true);
    }

    @Test
    public void testServiceCreation() {
        ServiceController<SleepTimerService> controller = Robolectric.buildService(SleepTimerService.class);
        SleepTimerService service = controller.create().get();
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
}
