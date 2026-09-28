package com.bas080.autosleepdroid

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreferenceManagerTest {

    private lateinit var context: Context
    private lateinit var rawPreferences: SharedPreferences
    private lateinit var preferenceManager: PreferenceManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        rawPreferences = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        rawPreferences.edit().clear().commit()
        preferenceManager = PreferenceManager(rawPreferences)
    }

    @Test
    fun testKeySpecificListenerFiresOnlyForTargetKey() {
        val targetKeyFired = AtomicBoolean(false)

        preferenceManager.registerListener("target_key") { targetKeyFired.set(true) }

        rawPreferences.edit().putBoolean("unrelated_key", true).commit()
        assertFalse("Listener should not fire for unrelated key", targetKeyFired.get())

        rawPreferences.edit().putBoolean("target_key", true).commit()
        assertTrue("Listener must fire when target_key changes", targetKeyFired.get())
    }

    @Test
    fun testUnregisterListenerStopsCallbacks() {
        val keyFired = AtomicBoolean(false)
        val listener = PreferenceManager.OnPreferenceChangeListener { keyFired.set(true) }

        preferenceManager.registerListener("test_key", listener)
        preferenceManager.unregisterListener("test_key", listener)

        rawPreferences.edit().putBoolean("test_key", true).commit()
        assertFalse("Unregistered listener must not receive callbacks", keyFired.get())
    }

    @Test
    fun testWriteOperationsAndGetters() {
        val listenerFired = AtomicBoolean(false)
        preferenceManager.registerListener("test_key") { listenerFired.set(true) }

        preferenceManager.edit().putBoolean("test_key", true).apply()

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertTrue("Write must trigger preference listener callback", listenerFired.get())
        assertTrue("Getter must return written preference value", preferenceManager.getBoolean("test_key", false))
    }

    @Test
    fun testAutoTrackingComputedValueMemoizationAndInvalidation() {
        val computeCount = AtomicInteger(0)

        rawPreferences.edit().putInt("dep_key_1", 10).commit()

        val result1 = preferenceManager.getComputed("testComp") { getter ->
            computeCount.incrementAndGet()
            getter.getInt("dep_key_1", 0) * 2
        }

        assertEquals(20, result1)
        assertEquals(1, computeCount.get())

        val result2 = preferenceManager.getComputed("testComp") { getter ->
            computeCount.incrementAndGet()
            getter.getInt("dep_key_1", 0) * 2
        }

        assertEquals(20, result2)
        assertEquals("Compute count should remain 1 due to memoization", 1, computeCount.get())

        rawPreferences.edit().putBoolean("unrelated_key", true).commit()

        val result3 = preferenceManager.getComputed("testComp") { getter ->
            computeCount.incrementAndGet()
            getter.getInt("dep_key_1", 0) * 2
        }

        assertEquals(20, result3)
        assertEquals("Compute count should remain 1 after unrelated key change", 1, computeCount.get())

        rawPreferences.edit().putInt("dep_key_1", 15).commit()

        val result4 = preferenceManager.getComputed("testComp") { getter ->
            computeCount.incrementAndGet()
            getter.getInt("dep_key_1", 0) * 2
        }

        assertEquals(30, result4)
        assertEquals("Compute count should increment to 2 after tracked preference update", 2, computeCount.get())
    }

    @Test
    fun testFunctionKeyedComputedValueMemoization() {
        val computeCount = AtomicInteger(0)
        rawPreferences.edit().putInt("test_minutes", 45).commit()

        val comp = PreferenceManager.ComputedValue { getter ->
            computeCount.incrementAndGet()
            "${getter.getInt("test_minutes", 0)}m"
        }

        val val1 = preferenceManager.getComputed(comp)
        assertEquals("45m", val1)
        assertEquals(1, computeCount.get())

        val val2 = preferenceManager.getComputed(comp)
        assertEquals("45m", val2)
        assertEquals("Compute count should remain 1 when using function reference key", 1, computeCount.get())

        rawPreferences.edit().putInt("test_minutes", 60).commit()

        val val3 = preferenceManager.getComputed(comp)
        assertEquals("60m", val3)
        assertEquals("Compute count should increment to 2 after tracked preference update", 2, computeCount.get())
    }

    @Test
    fun testWatchEffectInitialAndReactiveExecutionAndDispose() {
        val runCount = AtomicInteger(0)
        val lastValue = AtomicInteger(0)

        rawPreferences.edit().putInt("watched_key", 5).commit()

        val handle = preferenceManager.watchEffect { getter ->
            runCount.incrementAndGet()
            lastValue.set(getter.getInt("watched_key", 0))
        }

        assertEquals("Effect must run once immediately upon watchEffect", 1, runCount.get())
        assertEquals(5, lastValue.get())

        rawPreferences.edit().putBoolean("unrelated_key", true).commit()
        assertEquals("Unrelated key change should not trigger watchEffect", 1, runCount.get())

        rawPreferences.edit().putInt("watched_key", 12).commit()
        assertEquals("Watched key change must re-trigger watchEffect", 2, runCount.get())
        assertEquals(12, lastValue.get())

        handle.dispose()
        rawPreferences.edit().putInt("watched_key", 20).commit()
        assertEquals("Disposed watchEffect must not re-run on preference change", 2, runCount.get())
    }

    @Test
    fun testTrackingPreferenceGetterAndCachedComputationTypes() {
        // Test Long, String (non-null and null sentinel), and contains tracking
        rawPreferences.edit()
            .putLong("long_key", 100L)
            .putString("string_key", "hello")
            .commit()

        var computeCount = 0
        val compKey = "type_test_comp"

        val val1 = preferenceManager.getComputed(compKey) { getter ->
            computeCount++
            val l = getter.getLong("long_key", 0L)
            val s = getter.getString("string_key", "default")
            val missing = getter.getString("missing_string", null)
            val hasKey = getter.contains("present_key")
            "$l-$s-$missing-$hasKey"
        }

        assertEquals("100-hello-null-false", val1)
        assertEquals(1, computeCount)

        // Same access without preference change -> memoized
        val val2 = preferenceManager.getComputed(compKey) { _ ->
            computeCount++
            ""
        }
        assertEquals("100-hello-null-false", val2)
        assertEquals(1, computeCount)

        // Updating long_key triggers staleness
        rawPreferences.edit().putLong("long_key", 200L).commit()
        val val3 = preferenceManager.getComputed(compKey) { getter ->
            computeCount++
            val l = getter.getLong("long_key", 0L)
            val s = getter.getString("string_key", "default")
            val missing = getter.getString("missing_string", null)
            val hasKey = getter.contains("present_key")
            "$l-$s-$missing-$hasKey"
        }
        assertEquals("200-hello-null-false", val3)
        assertEquals(2, computeCount)

        // Setting missing_string triggers staleness via NULL_SENTINEL
        rawPreferences.edit().putString("missing_string", "now_present").commit()
        val val4 = preferenceManager.getComputed(compKey) { getter ->
            computeCount++
            val l = getter.getLong("long_key", 0L)
            val s = getter.getString("string_key", "default")
            val missing = getter.getString("missing_string", null)
            val hasKey = getter.contains("present_key")
            "$l-$s-$missing-$hasKey"
        }
        assertEquals("200-hello-now_present-false", val4)
        assertEquals(3, computeCount)

        // Adding present_key triggers staleness via contains:present_key
        rawPreferences.edit().putBoolean("present_key", true).commit()
        val val5 = preferenceManager.getComputed(compKey) { getter ->
            computeCount++
            val l = getter.getLong("long_key", 0L)
            val s = getter.getString("string_key", "default")
            val missing = getter.getString("missing_string", null)
            val hasKey = getter.contains("present_key")
            "$l-$s-$missing-$hasKey"
        }
        assertEquals("200-hello-now_present-true", val5)
        assertEquals(4, computeCount)
    }

    @Test
    fun testWatchEffectsVarargAndTaggedDispose() {
        val count1 = AtomicInteger(0)
        val count2 = AtomicInteger(0)

        rawPreferences.edit().putInt("k1", 1).putInt("k2", 2).commit()

        val tag = "group_tag"
        preferenceManager.watchEffect(tag) { getter ->
            count1.addAndGet(getter.getInt("k1", 0))
        }

        val multiHandle = preferenceManager.watchEffects(
            PreferenceManager.PreferenceEffect { getter ->
                count2.addAndGet(getter.getInt("k2", 0))
            },
            null
        )

        assertEquals(1, count1.get())
        assertEquals(2, count2.get())

        rawPreferences.edit().putInt("k1", 10).commit()
        assertEquals(11, count1.get())

        rawPreferences.edit().putInt("k2", 20).commit()
        assertEquals(22, count2.get())

        // Dispose via tag
        preferenceManager.disposeEffects(tag)
        rawPreferences.edit().putInt("k1", 100).commit()
        assertEquals(11, count1.get()) // Unchanged

        // Dispose multiHandle
        multiHandle.dispose()
        rawPreferences.edit().putInt("k2", 200).commit()
        assertEquals(22, count2.get()) // Unchanged
    }

    @Test
    fun testInvalidationAndNullHandling() {
        // Null inputs to getters/listeners
        preferenceManager.registerListener(null, null)
        preferenceManager.unregisterListener(null)
        preferenceManager.unregisterListener(null, null)
        preferenceManager.disposeEffects(null)
        assertEquals(null, preferenceManager.getComputed<String>(null))
        assertEquals(null, preferenceManager.getComputed<String>("key", null))

        val computeCount = AtomicInteger(0)
        preferenceManager.getComputed("comp1") { getter ->
            computeCount.incrementAndGet()
            getter.getBoolean("b", false)
        }
        assertEquals(1, computeCount.get())

        // Invalidate specific cacheKey
        preferenceManager.invalidateComputed("comp1")
        preferenceManager.getComputed("comp1") { getter ->
            computeCount.incrementAndGet()
            getter.getBoolean("b", false)
        }
        assertEquals(2, computeCount.get())

        // Invalidate all
        preferenceManager.invalidateAllComputed()
        preferenceManager.getComputed("comp1") { getter ->
            computeCount.incrementAndGet()
            getter.getBoolean("b", false)
        }
        assertEquals(3, computeCount.get())

        // Null key in onSharedPreferenceChanged
        preferenceManager.onSharedPreferenceChanged(rawPreferences, null)

        // Shutdown
        preferenceManager.shutdown()
    }
}
