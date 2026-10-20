package com.bas080.autosleepdroid

import android.app.Application
import android.content.Context
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SleepFactoidManagerTest {

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        application.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun testFactoidDatasetSizeIs100() {
        assertEquals(100, SleepFactoidManager.FACTOIDS.size)
    }

    @Test
    fun testGetTodayFactoidReturnsValidFactoid() {
        val factoid = SleepFactoidManager.getTodayFactoid(application)
        assertNotNull(factoid)
        assertTrue(factoid.text.isNotBlank())
        assertTrue(factoid.category.isNotBlank())
    }

    @Test
    fun testDislikeFactoidFiltersFromFutureRotations() {
        val initialFactoid = SleepFactoidManager.getTodayFactoid(application)
        val initialId = initialFactoid.id

        val replacement = SleepFactoidManager.dislikeFactoid(application, initialId)
        assertNotNull(replacement)

        val prefs = application.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE)
        val dislikedSet = prefs.getStringSet(PreferenceKeys.KEY_DISLIKED_FACTOIDS, emptySet()) ?: emptySet()
        assertTrue(dislikedSet.contains(initialId.toString()))

        val newToday = SleepFactoidManager.getTodayFactoid(application)
        assertFalse(newToday.id == initialId)
    }

    @Test
    fun testAllFactoidsDislikedFallbackUsesFullList() {
        val prefs = application.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE)
        val allIds = SleepFactoidManager.FACTOIDS.map { it.id.toString() }.toSet()
        prefs.edit().putStringSet(PreferenceKeys.KEY_DISLIKED_FACTOIDS, allIds).commit()

        val factoid = SleepFactoidManager.getTodayFactoid(application)
        assertNotNull(factoid)
        assertTrue(factoid.text.isNotBlank())
    }

    @Test
    fun testCategoriesCoverExpected5Categories() {
        val categories = SleepFactoidManager.FACTOIDS.map { it.category }.toSet()
        assertTrue(categories.contains("Brain Refresh"))
        assertTrue(categories.contains("Body Recovery"))
        assertTrue(categories.contains("Rhythms & Light"))
        assertTrue(categories.contains("Stress & Mood"))
        assertTrue(categories.contains("Wind-Down Habits"))
    }

    @Test
    fun testMainServiceBedtimeReminderAction() {
        val prefs = application.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, true)
            .putBoolean(PreferenceKeys.KEY_BEDTIME_REMINDER_ENABLED, true)
            .commit()

        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        val intent = android.content.Intent(application, MainService::class.java).apply {
            action = MainService.ACTION_BEDTIME_REMINDER_EXPIRY
        }
        service.onStartCommand(intent, 0, 1)

        val shadowApp = org.robolectric.Shadows.shadowOf(application)
        val nextStartedActivity = shadowApp.nextStartedActivity
        assertNotNull(nextStartedActivity)
        assertEquals(MainActivity::class.java.name, nextStartedActivity.component?.className)
    }

    @Test
    fun testBedtimeOverlayWithBlankNoteAndLikeButton() {
        val prefs = application.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, true)
            .putBoolean(PreferenceKeys.KEY_BEDTIME_REMINDER_ENABLED, true)
            .putString(PreferenceKeys.KEY_BEDTIME_NOTE_TO_SELF, "")
            .commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().get()

        MainActivityDialogs.showBedtimeOverlay(activity)

        val textNote = activity.findViewById<TextView>(R.id.text_bedtime_note_display)
        val btnLike = activity.findViewById<Button>(R.id.btn_factoid_like)

        assertEquals(activity.getString(R.string.hint_bedtime_note_placeholder), textNote.text.toString())
        btnLike.performClick()
    }

    @Test
    fun testBedtimeReminderToggleInActivity() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().resume().get()

        val rowGoal = activity.findViewById<SettingRowView>(R.id.row_enable_goal)
        val rowBedtime = activity.findViewById<SettingRowView>(R.id.row_bedtime_reminder)
        assertNotNull(rowBedtime)

        rowGoal.performClick()
        rowBedtime.performClick()

        val prefs = application.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE)
        assertTrue(prefs.getBoolean(PreferenceKeys.KEY_BEDTIME_REMINDER_ENABLED, false))

        rowBedtime.performClick()
        assertFalse(prefs.getBoolean(PreferenceKeys.KEY_BEDTIME_REMINDER_ENABLED, true))
    }

    @Test
    fun testCheckAndShowBedtimeOverlayOnResumeTriggerBranch() {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MINUTE, 60)
        val targetHour = cal.get(Calendar.HOUR_OF_DAY)
        val targetMin = cal.get(Calendar.MINUTE)

        val prefs = application.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, true)
            .putBoolean(PreferenceKeys.KEY_BEDTIME_REMINDER_ENABLED, true)
            .putInt(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR, targetHour)
            .putInt(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE, targetMin)
            .putInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, targetHour)
            .putInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, targetMin)
            .putInt(PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES, 450)
            .commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().resume().get()

        MainActivityHeaderAndLogsHandler.checkAndShowBedtimeOverlayOnResume(activity)

        val overlay = activity.findViewById<android.view.View>(R.id.bedtime_overlay_container)
        assertEquals(android.view.View.VISIBLE, overlay.visibility)
    }
}
