package com.bas080.autosleepdroid;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34})
public class PreferenceManagerTest {

    private Context context;
    private SharedPreferences rawPreferences;
    private PreferenceManager preferenceManager;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        rawPreferences = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE);
        rawPreferences.edit().clear().commit();
        preferenceManager = new PreferenceManager(rawPreferences);
    }

    @Test
    public void testKeySpecificListenerFiresOnlyForTargetKey() {
        AtomicBoolean targetKeyFired = new AtomicBoolean(false);

        preferenceManager.registerListener("target_key", key -> targetKeyFired.set(true));

        rawPreferences.edit().putBoolean("unrelated_key", true).commit();
        assertFalse("Listener should not fire for unrelated key", targetKeyFired.get());

        rawPreferences.edit().putBoolean("target_key", true).commit();
        assertTrue("Listener must fire when target_key changes", targetKeyFired.get());
    }

    @Test
    public void testUnregisterListenerStopsCallbacks() {
        AtomicBoolean keyFired = new AtomicBoolean(false);
        PreferenceManager.OnPreferenceChangeListener listener = key -> keyFired.set(true);

        preferenceManager.registerListener("test_key", listener);
        preferenceManager.unregisterListener("test_key", listener);

        rawPreferences.edit().putBoolean("test_key", true).commit();
        assertFalse("Unregistered listener must not receive callbacks", keyFired.get());
    }

    @Test
    public void testAsyncWriteOperationsAndGetters() throws Exception {
        AtomicBoolean listenerFired = new AtomicBoolean(false);
        preferenceManager.registerListener("async_key", key -> listenerFired.set(true));

        preferenceManager.putBooleanAsync("async_key", true);

        // Allow async executor thread to finish writing and flush looper for listener callback
        Thread.sleep(100);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertTrue("Async write must trigger preference listener callback", listenerFired.get());
        assertTrue("Getter must return written preference value", preferenceManager.getBoolean("async_key", false));
    }

    @Test
    public void testAutoTrackingComputedValueMemoizationAndInvalidation() {
        AtomicInteger computeCount = new AtomicInteger(0);

        rawPreferences.edit().putInt("dep_key_1", 10).commit();

        // First call evaluates the computation and automatically tracks accessed keys
        int result1 = preferenceManager.getComputed("testComp", getter -> {
            computeCount.incrementAndGet();
            return getter.getInt("dep_key_1", 0) * 2;
        });

        assertEquals(20, result1);
        assertEquals(1, computeCount.get());

        // Second call uses cached result without re-evaluating
        int result2 = preferenceManager.getComputed("testComp", getter -> {
            computeCount.incrementAndGet();
            return getter.getInt("dep_key_1", 0) * 2;
        });

        assertEquals(20, result2);
        assertEquals("Compute count should remain 1 due to memoization", 1, computeCount.get());

        // Modifying unrelated key does not invalidate computation
        rawPreferences.edit().putBoolean("unrelated_key", true).commit();

        int result3 = preferenceManager.getComputed("testComp", getter -> {
            computeCount.incrementAndGet();
            return getter.getInt("dep_key_1", 0) * 2;
        });

        assertEquals(20, result3);
        assertEquals("Compute count should remain 1 after unrelated key change", 1, computeCount.get());

        // Modifying tracked preference key invalidates computation automatically
        rawPreferences.edit().putInt("dep_key_1", 15).commit();

        int result4 = preferenceManager.getComputed("testComp", getter -> {
            computeCount.incrementAndGet();
            return getter.getInt("dep_key_1", 0) * 2;
        });

        assertEquals(30, result4);
        assertEquals("Compute count should increment to 2 after tracked preference update", 2, computeCount.get());
    }
}
