package com.bas080.autosleepdroid

import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportExportTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testExportSettingsGeneratesValidSchemaVersion1Json() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        val prefs = context.getSharedPreferences(PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE)

        prefs.edit()
            .putBoolean(PreferenceKeys.KEY_ACTIVE, true)
            .putInt(PreferenceKeys.KEY_DURATION_MINUTES, 45)
            .putBoolean(PreferenceKeys.KEY_AUTO_TIMER_ENABLED, true)
            .putBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, true)
            .putInt(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR, 7)
            .putInt(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE, 30)
            .putInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, 8)
            .putInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, 0)
            .putInt(PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES, 480)
            .putBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, true)
            .putInt(PreferenceKeys.KEY_HC_MIN_DURATION_MINUTES, 20)
            .putBoolean(PreferenceKeys.KEY_DONATE_DIALOG_HIDDEN, true)
            .apply()

        val btnExport = activity.findViewById<android.view.View>(R.id.btn_export)
        assertNotNull(btnExport)
        btnExport.performClick()

        val shadowActivity = Shadows.shadowOf(activity)
        val startedIntent = shadowActivity.nextStartedActivity
        assertNotNull(startedIntent)
        assertEquals(Intent.ACTION_CHOOSER, startedIntent.action)

        val targetIntent = IntentCompat.getParcelableExtra(startedIntent, Intent.EXTRA_INTENT, Intent::class.java)
        assertNotNull(targetIntent)
        assertEquals(Intent.ACTION_SEND, targetIntent?.action)
        assertEquals("text/plain", targetIntent?.type)

        val jsonStr = targetIntent?.getStringExtra(Intent.EXTRA_TEXT)
        assertNotNull(jsonStr)

        val json = JSONObject(jsonStr!!)
        assertEquals(1, json.getInt("version"))
        assertTrue(json.getBoolean(PreferenceKeys.KEY_ACTIVE))
        assertEquals(45, json.getInt(PreferenceKeys.KEY_DURATION_MINUTES))
        assertTrue(json.getBoolean(PreferenceKeys.KEY_AUTO_TIMER_ENABLED))
        assertTrue(json.getBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED))
        assertEquals(7, json.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR))
        assertEquals(30, json.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE))
        assertEquals(8, json.getInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR))
        assertEquals(0, json.getInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE))
        assertEquals(480, json.getInt(PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES))
        assertTrue(json.getBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED))
        assertEquals(20, json.getInt(PreferenceKeys.KEY_HC_MIN_DURATION_MINUTES))
        assertTrue(json.getBoolean(PreferenceKeys.KEY_DONATE_DIALOG_HIDDEN))
    }

    @Test
    fun testImportSettingsAppliesValidJsonAndUpdatesPreferences() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        val prefs = context.getSharedPreferences(PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE)

        val validJson = JSONObject().apply {
            put("version", 1)
            put(PreferenceKeys.KEY_ACTIVE, false)
            put(PreferenceKeys.KEY_DURATION_MINUTES, 35)
            put(PreferenceKeys.KEY_AUTO_TIMER_ENABLED, false)
            put(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, true)
            put(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR, 6)
            put(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE, 15)
            put(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, 6)
            put(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, 15)
            put(PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES, 420)
            put(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, false)
            put(PreferenceKeys.KEY_HC_MIN_DURATION_MINUTES, 15)
            put(PreferenceKeys.KEY_DONATE_DIALOG_HIDDEN, true)
        }.toString()

        val method = MainActivity::class.java.getDeclaredMethod("importSettings", String::class.java)
        method.isAccessible = true
        method.invoke(activity, validJson)

        assertFalse(prefs.getBoolean(PreferenceKeys.KEY_ACTIVE, true))
        assertEquals(35, prefs.getInt(PreferenceKeys.KEY_DURATION_MINUTES, 0))
        assertFalse(prefs.getBoolean(PreferenceKeys.KEY_AUTO_TIMER_ENABLED, true))
        assertTrue(prefs.getBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, false))
        assertEquals(6, prefs.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR, 0))
        assertEquals(15, prefs.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE, 0))
        assertEquals(420, prefs.getInt(PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES, 0))

        assertEquals(activity.getString(R.string.toast_import_success), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testImportSettingsRejectsInvalidSchemaVersionAndLeavesPreferencesUnchanged() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        val prefs = context.getSharedPreferences(PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE)

        prefs.edit().putInt(PreferenceKeys.KEY_DURATION_MINUTES, 20).apply()

        val invalidVersionJson = JSONObject().apply {
            put("version", 2)
            put(PreferenceKeys.KEY_DURATION_MINUTES, 90)
        }.toString()

        val method = MainActivity::class.java.getDeclaredMethod("importSettings", String::class.java)
        method.isAccessible = true
        method.invoke(activity, invalidVersionJson)

        assertEquals(20, prefs.getInt(PreferenceKeys.KEY_DURATION_MINUTES, 0))
        assertEquals(activity.getString(R.string.toast_import_invalid), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testImportSettingsRejectsOutOfRangeParametersAndPreservesOriginalPreferences() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        val prefs = context.getSharedPreferences(PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE)

        prefs.edit().putInt(PreferenceKeys.KEY_DURATION_MINUTES, 20).apply()

        val outOfRangeJson = JSONObject().apply {
            put("version", 1)
            put(PreferenceKeys.KEY_DURATION_MINUTES, 5000) // max is 1440
        }.toString()

        val method = MainActivity::class.java.getDeclaredMethod("importSettings", String::class.java)
        method.isAccessible = true
        method.invoke(activity, outOfRangeJson)

        assertEquals(20, prefs.getInt(PreferenceKeys.KEY_DURATION_MINUTES, 0))
        assertEquals(activity.getString(R.string.toast_import_invalid), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testImportSettingsRejectsMalformedJsonAndNullInput() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        val prefs = context.getSharedPreferences(PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE)

        prefs.edit().putInt(PreferenceKeys.KEY_DURATION_MINUTES, 20).apply()

        val method = MainActivity::class.java.getDeclaredMethod("importSettings", String::class.java)
        method.isAccessible = true

        method.invoke(activity, "not valid json {")
        assertEquals(20, prefs.getInt(PreferenceKeys.KEY_DURATION_MINUTES, 0))
        assertEquals(activity.getString(R.string.toast_import_invalid), ShadowToast.getTextOfLatestToast())

        method.invoke(activity, null)
        assertEquals(20, prefs.getInt(PreferenceKeys.KEY_DURATION_MINUTES, 0))
        assertEquals(activity.getString(R.string.toast_import_invalid), ShadowToast.getTextOfLatestToast())
    }
}
