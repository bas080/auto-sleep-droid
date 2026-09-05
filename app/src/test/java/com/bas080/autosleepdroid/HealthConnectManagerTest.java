package com.bas080.autosleepdroid;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import androidx.health.connect.client.HealthConnectClient;
import androidx.health.connect.client.PermissionController;
import androidx.health.connect.client.records.Record;
import androidx.health.connect.client.records.SleepSessionRecord;
import androidx.health.connect.client.response.InsertRecordsResponse;

import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class HealthConnectManagerTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }

    @Test
    public void testIsHealthConnectAvailable_DoesNotCrash() {
        boolean available = HealthConnectManager.isHealthConnectAvailable(context);
        // In Robolectric environment without Health Connect provider APK installed, should return false safely
        assertFalse(available);
    }

    @Test
    public void testWriteSleepSession_InvalidTimestamps_FailsValidation() {
        AtomicBoolean successRef = new AtomicBoolean(true);
        AtomicReference<String> errorRef = new AtomicReference<>();

        HealthConnectManager.writeSleepSession(context, 0L, 1000L, (success, error) -> {
            successRef.set(success);
            errorRef.set(error);
        });

        assertFalse(successRef.get());
        assertNotNull(errorRef.get());
        assertTrue(errorRef.get().contains("Invalid timestamps"));
    }

    @Test
    public void testWriteSleepSession_EndTimeBeforeStartTime_FailsValidation() {
        AtomicBoolean successRef = new AtomicBoolean(true);
        AtomicReference<String> errorRef = new AtomicReference<>();

        long now = System.currentTimeMillis();
        HealthConnectManager.writeSleepSession(context, now, now - 1000L, (success, error) -> {
            successRef.set(success);
            errorRef.set(error);
        });

        assertFalse(successRef.get());
        assertNotNull(errorRef.get());
        assertTrue(errorRef.get().contains("Invalid timestamps"));
    }

    @Test
    public void testWriteSleepSession_DurationTooShort_FailsValidation() {
        AtomicBoolean successRef = new AtomicBoolean(true);
        AtomicReference<String> errorRef = new AtomicReference<>();

        long now = System.currentTimeMillis();
        // 30 seconds duration (< 1 minute)
        HealthConnectManager.writeSleepSession(context, now, now + 30_000L, (success, error) -> {
            successRef.set(success);
            errorRef.set(error);
        });

        assertFalse(successRef.get());
        assertNotNull(errorRef.get());
        assertTrue(errorRef.get().contains("Invalid sleep duration"));
    }

    @Test
    public void testWriteSleepSession_DurationTooLong_FailsValidation() {
        AtomicBoolean successRef = new AtomicBoolean(true);
        AtomicReference<String> errorRef = new AtomicReference<>();

        long now = System.currentTimeMillis();
        // 25 hours duration (> 24 hours / 1440 minutes)
        HealthConnectManager.writeSleepSession(context, now, now + 25 * 3600_000L, (success, error) -> {
            successRef.set(success);
            errorRef.set(error);
        });

        assertFalse(successRef.get());
        assertNotNull(errorRef.get());
        assertTrue(errorRef.get().contains("Invalid sleep duration"));
    }

    @Test
    public void testHealthConnectPreferencePersistence() {
        SharedPreferences prefs = context.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE);
        assertFalse(prefs.getBoolean("health_connect_enabled", false));

        prefs.edit().putBoolean("health_connect_enabled", true).apply();
        assertTrue(prefs.getBoolean("health_connect_enabled", false));
    }

    @Test
    public void testNapAndSleepSessionStartKeyPersistence() {
        SharedPreferences prefs = context.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();

        prefs.edit().putLong("nap_start_time_ms", now).apply();
        assertEquals(now, prefs.getLong("nap_start_time_ms", 0L));

        prefs.edit().remove("nap_start_time_ms").apply();
        assertEquals(0L, prefs.getLong("nap_start_time_ms", 0L));
    }

}
