package com.bas080.autosleepdroid

import android.Manifest
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.IntentCompat
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
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowApplication
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {

    @Before
    fun setUp() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        EventLogger.clear(application)
    }

    @Test
    fun testActivityCreation() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().get()
        assertNotNull(activity)
    }

    @Test
    fun testVersionDisplayAndClickLaunchesReleases() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().get()
        val btnVersion = activity.findViewById<View>(R.id.btn_version)
        val versionView = activity.findViewById<TextView>(R.id.app_version_text)
        assertNotNull(btnVersion)
        assertNotNull(versionView)
        assertTrue(versionView.text.toString().contains(BuildConfig.VERSION_NAME))

        btnVersion.performClick()
        val intent = Shadows.shadowOf(activity).nextStartedActivity
        assertNotNull(intent)
        assertEquals(Intent.ACTION_VIEW, intent?.action)
        assertEquals("https://github.com/bas080/auto-sleep-droid/releases", intent?.dataString)
    }

    @Test
    fun testInlineEventLogs() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().resume().get()

        EventLogger.log(activity, EventLogger.LEVEL_HIGH, "Test event log entry")
        val eventLogText = activity.findViewById<TextView>(R.id.event_log_text)
        assertNotNull(eventLogText)
        assertTrue(eventLogText.text.toString().contains("Test event log entry"))
        assertTrue("Expected event_log_text to be selectable", eventLogText.isTextSelectable)
    }

    @Test
    fun testLinksDialogManualShowsFullScreenView() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().get()
        val btnLinks = activity.findViewById<View>(R.id.btn_links)
        assertNotNull(btnLinks)

        val manualOverlay = activity.findViewById<View>(R.id.manual_overlay_container)
        val mainContent = activity.findViewById<View>(R.id.main_content_container)
        assertEquals(View.GONE, manualOverlay.visibility)
        assertEquals(View.VISIBLE, mainContent.visibility)

        btnLinks.performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        Shadows.shadowOf(dialog).clickOnItem(0)

        assertEquals(View.VISIBLE, manualOverlay.visibility)
        assertEquals(View.GONE, mainContent.visibility)

        val btnBack = activity.findViewById<Button>(R.id.btn_manual_back)
        assertNotNull(btnBack)
        btnBack.performClick()

        assertEquals(View.GONE, manualOverlay.visibility)
        assertEquals(View.VISIBLE, mainContent.visibility)
    }

    @Test
    fun testLinksDialogLogsShowsFullScreenView() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().start().resume().get()
        val btnLinks = activity.findViewById<View>(R.id.btn_links)
        assertNotNull(btnLinks)

        val logsOverlay = activity.findViewById<View>(R.id.logs_overlay_container)
        val mainContent = activity.findViewById<View>(R.id.main_content_container)
        assertEquals(View.GONE, logsOverlay.visibility)
        assertEquals(View.VISIBLE, mainContent.visibility)

        btnLinks.performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        Shadows.shadowOf(dialog).clickOnItem(1)

        assertEquals(View.VISIBLE, logsOverlay.visibility)
        assertEquals(View.GONE, mainContent.visibility)

        activity.onBackPressedDispatcher.onBackPressed()

        assertEquals(View.GONE, logsOverlay.visibility)
        assertEquals(View.VISIBLE, mainContent.visibility)
    }

    @Test
    fun testPendingCrashReportPromptsUserDialogAndLaunchesFeedbackIntent() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        EventLogger.log(application, EventLogger.LEVEL_HIGH, "Sample logged event before crash")
        val prefs = application.getSharedPreferences("crash_reports", Context.MODE_PRIVATE)
        prefs.edit().putString("pending_crash_report", "CRASH: java.lang.NullPointerException at test.DummyClass").commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().resume().get()

        val shadowActivity = Shadows.shadowOf(activity)
        while (shadowActivity.nextStartedActivity != null) {}

        val crashDialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("Crash report dialog should be shown on launch if pending crash report exists", crashDialog)

        val sendBtn = crashDialog.getButton(DialogInterface.BUTTON_POSITIVE)
        assertNotNull(sendBtn)
        sendBtn.performClick()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val chooserIntent = shadowActivity.nextStartedActivity
        assertNotNull(chooserIntent)
        assertEquals(Intent.ACTION_CHOOSER, chooserIntent?.action)

        val sendIntent = IntentCompat.getParcelableExtra(chooserIntent!!, Intent.EXTRA_INTENT, Intent::class.java)
        assertNotNull(sendIntent)
        assertEquals(Intent.ACTION_SENDTO, sendIntent?.action)
        val bodyText = sendIntent?.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        assertTrue(bodyText.contains("NullPointerException"))
        assertTrue("Crash report email must include Logs section", bodyText.contains("Logs:"))
        assertTrue("Crash report email must contain logged events", bodyText.contains("Sample logged event before crash"))

        assertFalse("Pending crash report should be cleared after prompting user", prefs.contains("pending_crash_report"))
    }

    @Test
    fun testFeedbackButtonPromptsIncludeLogsDialogAndLaunchesIntent() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        EventLogger.log(application, EventLogger.LEVEL_HIGH, "User feedback test log")

        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().resume().get()

        val shadowActivity = Shadows.shadowOf(activity)
        while (shadowActivity.nextStartedActivity != null) {}

        val btnFeedback = activity.findViewById<View>(R.id.btn_feedback)
        assertNotNull("btn_feedback view should exist in About section", btnFeedback)

        btnFeedback.performClick()
        val promptDialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("Feedback click should show include logs prompt dialog", promptDialog)

        val yesBtn = promptDialog.getButton(DialogInterface.BUTTON_POSITIVE)
        assertNotNull(yesBtn)
        yesBtn.performClick()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val chooserIntent = Shadows.shadowOf(activity).nextStartedActivity
        assertNotNull(chooserIntent)
        assertEquals(Intent.ACTION_CHOOSER, chooserIntent?.action)

        val sendIntent = IntentCompat.getParcelableExtra(chooserIntent!!, Intent.EXTRA_INTENT, Intent::class.java)
        assertNotNull(sendIntent)
        assertEquals(Intent.ACTION_SENDTO, sendIntent?.action)
        assertTrue(sendIntent?.dataString?.startsWith("mailto:bas080@hotmail.com") == true)
        assertTrue(sendIntent?.getStringExtra(Intent.EXTRA_SUBJECT)?.contains("Auto Sleep Droid Feedback") == true)
        val bodyWithLogs = sendIntent?.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        assertTrue("Feedback email with logs included should contain Logs section", bodyWithLogs.contains("Logs:"))
        assertTrue("Feedback email should contain logged events", bodyWithLogs.contains("User feedback test log"))

        btnFeedback.performClick()
        val promptDialogNo = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(promptDialogNo)

        val noBtn = promptDialogNo.getButton(DialogInterface.BUTTON_NEGATIVE)
        assertNotNull(noBtn)
        noBtn.performClick()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val chooserIntentNo = Shadows.shadowOf(activity).nextStartedActivity
        assertNotNull(chooserIntentNo)

        val sendIntentNo = IntentCompat.getParcelableExtra(chooserIntentNo!!, Intent.EXTRA_INTENT, Intent::class.java)
        assertNotNull(sendIntentNo)
        val bodyWithoutLogs = sendIntentNo?.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        assertFalse("Feedback email without logs should NOT contain Logs section", bodyWithoutLogs.contains("Logs:"))
    }

    @Test
    fun testRandomDonateDialogShownWhenTriggeredAndNotHidden() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().resume().get()

        activity.maybeShowRandomDonateDialog(forceShow = true)

        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("Random donate dialog should be displayed when forceShow is true and not hidden", dialog)
        val shadowDialog = Shadows.shadowOf(dialog)
        assertTrue("Dialog title should match witty donate title", shadowDialog.title.toString().contains("Enjoying Auto Sleep Droid"))
    }

    @Test
    fun testRandomDonateDialogDonateButtonHidesInFutureAndLaunchesIntent() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().resume().get()

        activity.maybeShowRandomDonateDialog(forceShow = true)

        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)

        val donateBtn = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        assertNotNull(donateBtn)
        donateBtn.performClick()

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val prefs = activity.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE)
        assertTrue("donate_dialog_hidden preference must be true after clicking Donate", prefs.getBoolean("donate_dialog_hidden", false))

        val intent = Shadows.shadowOf(activity).nextStartedActivity
        assertNotNull(intent)
        assertEquals(Intent.ACTION_VIEW, intent?.action)
        assertEquals("https://liberapay.com/bas080", intent?.dataString)

        val dialogCountBefore = ShadowAlertDialog.getShownDialogs().size
        activity.maybeShowRandomDonateDialog(forceShow = true)
        val dialogCountAfter = ShadowAlertDialog.getShownDialogs().size
        assertEquals("Donate dialog should not show again after donate_dialog_hidden is true", dialogCountBefore, dialogCountAfter)
    }

    @Test
    fun testRandomDonateDialogLaterButtonDismissesWithoutHidingInFuture() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().resume().get()

        activity.maybeShowRandomDonateDialog(forceShow = true)

        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)

        val laterBtn = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
        assertNotNull(laterBtn)
        laterBtn.performClick()

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val prefs = activity.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE)
        assertFalse("donate_dialog_hidden preference must remain false after clicking Later", prefs.getBoolean("donate_dialog_hidden", false))
    }

    @Test
    fun testLinksDialogDonateLaunchesIntent() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().get()
        val btnLinks = activity.findViewById<View>(R.id.btn_links)
        assertNotNull(btnLinks)

        btnLinks.performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        Shadows.shadowOf(dialog).clickOnItem(2)

        val intent = Shadows.shadowOf(activity).nextStartedActivity
        assertNotNull(intent)
        assertEquals(Intent.ACTION_VIEW, intent?.action)
        assertEquals("https://liberapay.com/bas080", intent?.dataString)
    }

    @Test
    fun testExportButtonLaunchesShareIntent() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().get()
        val btnExport = activity.findViewById<View>(R.id.btn_export)
        assertNotNull(btnExport)

        btnExport.performClick()

        val chooserIntent = Shadows.shadowOf(activity).nextStartedActivity
        assertNotNull(chooserIntent)
        assertEquals(Intent.ACTION_CHOOSER, chooserIntent?.action)

        val sendIntent = IntentCompat.getParcelableExtra(chooserIntent!!, Intent.EXTRA_INTENT, Intent::class.java)
        assertNotNull(sendIntent)
        assertEquals(Intent.ACTION_SEND, sendIntent?.action)
        assertTrue(sendIntent?.getStringExtra(Intent.EXTRA_TEXT)?.contains("\"version\":1") == true)
    }

    @Test
    fun testImportButtonShowsImportDialogAndImportsJSON() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().get()
        val btnImport = activity.findViewById<View>(R.id.btn_import)
        assertNotNull(btnImport)

        btnImport.performClick()

        val importDialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(importDialog)

        var editText: EditText? = null
        if (importDialog.window != null) {
            val list = ArrayList<EditText>()
            findViewsOfType(importDialog.window!!.decorView, EditText::class.java, list)
            if (list.isNotEmpty()) {
                editText = list[0]
            }
        }
        assertNotNull(editText)

        val json = "{\"version\":1,\"duration_minutes\":45,\"active\":true,\"wake_up_goal_enabled\":true,\"wake_up_goal_hour\":7,\"wake_up_goal_minute\":15,\"min_sleep_duration_minutes\":480,\"hc_min_duration_minutes\":20}"
        editText?.setText(json)

        val importBtn = importDialog.getButton(DialogInterface.BUTTON_POSITIVE)
        assertNotNull(importBtn)
        importBtn.performClick()

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val prefs = activity.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE)
        assertEquals(45, prefs.getInt("duration_minutes", -1))
        assertTrue(prefs.getBoolean("active", false))
        assertTrue(prefs.getBoolean("wake_up_goal_enabled", false))
        assertEquals(7, prefs.getInt("wake_up_goal_hour", -1))
        assertEquals(15, prefs.getInt("wake_up_goal_minute", -1))
        assertEquals(480, prefs.getInt("min_sleep_duration_minutes", -1))
        assertEquals(20, prefs.getInt("hc_min_duration_minutes", -1))
    }

    private fun <T : View> findViewsOfType(root: View, clazz: Class<T>, outList: MutableList<T>) {
        if (clazz.isInstance(root)) {
            outList.add(clazz.cast(root)!!)
        }
        if (root is android.view.ViewGroup) {
            for (i in 0 until root.childCount) {
                findViewsOfType(root.getChildAt(i), clazz, outList)
            }
        }
    }

    @Test
    fun testResumeRemainsOpen() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().get()

        controller.resume()

        assertFalse(activity.isFinishing)
        assertNotNull(activity.findViewById(R.id.switch_enable_timer))
    }

    @Test
    fun testManualTimerTogglePreservedWhenAutoDndIsEnabledAndActivityResumed() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val prefs = application.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("auto_timer_enabled", true).putBoolean("active", true).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().resume().get()

        val switchEnable = activity.findViewById<Switch>(R.id.switch_enable_timer)
        assertNotNull(switchEnable)
        assertTrue(switchEnable.isChecked)

        switchEnable.isChecked = false
        assertFalse(prefs.getBoolean("active", true))

        controller.pause().resume()

        assertFalse("Manual timer toggle should be preserved on resume even when auto DND is enabled", switchEnable.isChecked)
        assertFalse(prefs.getBoolean("active", true))
    }

    @Test
    fun testTargetTimeButtonClickOpensDialogAndSavesTime() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().resume().get()

        val btnTargetTime = activity.findViewById<View>(R.id.btn_target_time)
        val textTargetTimeValue = activity.findViewById<TextView>(R.id.text_target_time_value)
        assertNotNull(btnTargetTime)
        assertNotNull(textTargetTimeValue)
        assertFalse(textTargetTimeValue.text.toString().isEmpty())

        btnTargetTime.performClick()

        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull(dialog)
        assertTrue(dialog is android.app.TimePickerDialog)

        val timePickerDialog = dialog as android.app.TimePickerDialog
        timePickerDialog.updateTime(7, 45)

        val okButton = timePickerDialog.getButton(DialogInterface.BUTTON_POSITIVE)
        assertNotNull(okButton)
        okButton.performClick()

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val prefs = activity.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE)
        assertEquals(7, prefs.getInt("wake_up_goal_hour", -1))
        assertEquals(45, prefs.getInt("wake_up_goal_minute", -1))
    }

    @Test
    fun testConfigControlsUpdatesSharedPreferences() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().resume().get()

        val switchEnable = activity.findViewById<Switch>(R.id.switch_enable_timer)
        val inputDuration = activity.findViewById<View>(R.id.input_duration)
        val switchAutoTimer = activity.findViewById<Switch>(R.id.switch_auto_timer)
        val switchGoal = activity.findViewById<Switch>(R.id.switch_enable_goal)

        assertNotNull(switchEnable)
        assertNotNull(inputDuration)
        assertNotNull(switchAutoTimer)
        assertNotNull(switchGoal)

        switchEnable.isChecked = false

        inputDuration.performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        val list = ArrayList<DurationInputView>()
        if (dialog.window != null) {
            findViewsOfType(dialog.window!!.decorView, DurationInputView::class.java, list)
        }
        assertFalse(list.isEmpty())
        list[0].setTotalMinutes(45)
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        switchGoal.isChecked = true

        val prefs = activity.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE)
        assertFalse(prefs.getBoolean("active", true))
        assertEquals(45, prefs.getInt("duration_minutes", -1))
        assertTrue(prefs.getBoolean("wake_up_goal_enabled", false))
    }

    @Test
    fun testStartupRequestsNotificationPermissionWhenNotGranted() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().resume().get()

        val shadowActivity = Shadows.shadowOf(activity)
        val request = shadowActivity.lastRequestedPermission
        assertNotNull(request)
        assertEquals(Manifest.permission.POST_NOTIFICATIONS, request.requestedPermissions[0])
    }

    @Test
    fun testFormatColoredEvent() {
        val formatted = EventLogger.formatColoredEvent("8/29 14:30:00 \u0002Timer turned on")
        assertNotNull(formatted)
        assertTrue(formatted is android.text.Spanned)
        assertFalse(formatted.toString().contains("\u0002"))

        val spanned = formatted as android.text.Spanned
        val colorSpans = spanned.getSpans(0, spanned.length, android.text.style.ForegroundColorSpan::class.java)
        assertTrue(colorSpans.size >= 2)

        assertEquals(-0x666667, colorSpans[0].foregroundColor)
        assertEquals(-0x1000000, colorSpans[1].foregroundColor)
    }

    @Test
    fun testNewIntentHandling() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().get()
        controller.newIntent(Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java))
        assertNotNull(activity)
    }

    @Test
    fun testInvalidDurationInputShowsToastAndRevertsText() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().resume().get()

        val inputDuration = activity.findViewById<View>(R.id.input_duration)
        val textDurationValue = activity.findViewById<TextView>(R.id.text_duration_value)
        assertNotNull(inputDuration)
        assertNotNull(textDurationValue)

        assertEquals("20m", textDurationValue.text.toString())

        inputDuration.performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        val list = ArrayList<DurationInputView>()
        if (dialog.window != null) {
            findViewsOfType(dialog.window!!.decorView, DurationInputView::class.java, list)
        }
        assertFalse(list.isEmpty())
        list[0].getHoursPicker()?.value = 0
        list[0].getMinutesPicker()?.value = 0

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val prefs = activity.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE)
        assertEquals(20, prefs.getInt("duration_minutes", 20))
        assertEquals("20m", textDurationValue.text.toString())
        assertEquals(activity.getString(R.string.toast_duration_invalid), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testZeroDurationInputInDialogShowsToastAndRevertsText() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().resume().get()

        val inputDuration = activity.findViewById<View>(R.id.input_duration)
        val textDurationValue = activity.findViewById<TextView>(R.id.text_duration_value)
        assertNotNull(inputDuration)
        assertNotNull(textDurationValue)

        inputDuration.performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        val list = ArrayList<DurationInputView>()
        if (dialog.window != null) {
            findViewsOfType(dialog.window!!.decorView, DurationInputView::class.java, list)
        }
        assertFalse(list.isEmpty())
        list[0].getHoursPicker()?.value = 0
        list[0].getMinutesPicker()?.value = 0

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val prefs = activity.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE)
        assertEquals(20, prefs.getInt("duration_minutes", 20))
        assertEquals("20m", textDurationValue.text.toString())
        assertEquals(activity.getString(R.string.toast_duration_invalid), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testInvalidMinSleepInputInDialogShowsToastAndRevertsText() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().resume().get()

        val inputMinSleep = activity.findViewById<View>(R.id.input_min_sleep)
        val textMinSleepValue = activity.findViewById<TextView>(R.id.text_min_sleep_value)
        assertNotNull(inputMinSleep)
        assertNotNull(textMinSleepValue)

        assertEquals("7h 30m", textMinSleepValue.text.toString())

        inputMinSleep.performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        val list = ArrayList<DurationInputView>()
        if (dialog.window != null) {
            findViewsOfType(dialog.window!!.decorView, DurationInputView::class.java, list)
        }
        assertFalse(list.isEmpty())
        list[0].getHoursPicker()?.value = 0
        list[0].getMinutesPicker()?.value = 0

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val prefs = activity.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE)
        assertEquals(450, prefs.getInt("min_sleep_duration_minutes", 450))
        assertEquals("7h 30m", textMinSleepValue.text.toString())
        assertEquals(activity.getString(R.string.toast_duration_invalid), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testDurationDialogUpdatesTotalMinutesAndText() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().resume().get()

        val inputDuration = activity.findViewById<View>(R.id.input_duration)
        val textDurationValue = activity.findViewById<TextView>(R.id.text_duration_value)
        assertNotNull(inputDuration)
        assertNotNull(textDurationValue)

        inputDuration.performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        val list = ArrayList<DurationInputView>()
        if (dialog.window != null) {
            findViewsOfType(dialog.window!!.decorView, DurationInputView::class.java, list)
        }
        assertFalse(list.isEmpty())
        list[0].setTotalMinutes(90)

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val prefs = activity.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE)
        assertEquals(90, prefs.getInt("duration_minutes", -1))
        assertEquals("1h 30m", textDurationValue.text.toString())
    }

    @Test
    fun testAboutHeaderExistsAboveLinks() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().resume().get()

        val headerAbout = activity.findViewById<TextView>(R.id.header_about)
        assertNotNull(headerAbout)
        assertEquals(activity.getString(R.string.heading_about), headerAbout.text.toString())
    }

    @Test
    fun testSectionHeadingsExistAndReflectEnabledStates() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().resume().get()

        val headerTimer = activity.findViewById<TextView>(R.id.header_timer)
        val headerAlarm = activity.findViewById<TextView>(R.id.header_alarm)
        val headerHealthConnect = activity.findViewById<TextView>(R.id.header_health_connect)
        val headerDnd = activity.findViewById<TextView>(R.id.header_dnd)
        val headerBackup = activity.findViewById<TextView>(R.id.header_backup)
        val headerAbout = activity.findViewById<TextView>(R.id.header_about)

        assertNotNull(headerTimer)
        assertNotNull(headerAlarm)
        assertNotNull(headerHealthConnect)
        assertNotNull(headerDnd)
        assertNotNull(headerBackup)
        assertNotNull(headerAbout)

        assertEquals(activity.getString(R.string.heading_timer), headerTimer.text.toString())
        assertEquals(activity.getString(R.string.heading_alarm), headerAlarm.text.toString())
        assertEquals(activity.getString(R.string.heading_health_connect), headerHealthConnect.text.toString())
        assertEquals(activity.getString(R.string.heading_dnd), headerDnd.text.toString())
        assertEquals(activity.getString(R.string.heading_backup), headerBackup.text.toString())
        assertEquals(activity.getString(R.string.heading_about), headerAbout.text.toString())

        val switchEnable = activity.findViewById<Switch>(R.id.switch_enable_timer)
        val switchGoal = activity.findViewById<Switch>(R.id.switch_enable_goal)

        switchEnable.isChecked = true
        switchGoal.isChecked = true

        assertTrue(headerDnd.isEnabled)
        assertEquals(1.0f, headerDnd.alpha, 0.01f)
        assertTrue(headerTimer.isEnabled)
        assertEquals(1.0f, headerTimer.alpha, 0.01f)
        assertTrue(headerAlarm.isEnabled)
        assertEquals(1.0f, headerAlarm.alpha, 0.01f)

        switchGoal.isChecked = false
        assertTrue(headerDnd.isEnabled)
        assertEquals(1.0f, headerDnd.alpha, 0.01f)
        assertTrue(headerTimer.isEnabled)
        assertEquals(1.0f, headerTimer.alpha, 0.01f)
        assertTrue(headerAlarm.isEnabled)
        assertEquals(1.0f, headerAlarm.alpha, 0.01f)

        switchEnable.isChecked = false
        assertTrue(headerDnd.isEnabled)
        assertEquals(1.0f, headerDnd.alpha, 0.01f)
        assertTrue(headerTimer.isEnabled)
        assertEquals(1.0f, headerTimer.alpha, 0.01f)
        assertTrue(headerAlarm.isEnabled)
        assertEquals(1.0f, headerAlarm.alpha, 0.01f)

        switchGoal.isChecked = true
        assertTrue(headerAlarm.isEnabled)
        assertEquals(1.0f, headerAlarm.alpha, 0.01f)
    }

    @Test
    fun testTimerInputsRemainEnabledWhenSleepTimerIsDisabled() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().resume().get()

        val switchEnable = activity.findViewById<Switch>(R.id.switch_enable_timer)
        val headerTimer = activity.findViewById<View>(R.id.header_timer)
        val headerAlarm = activity.findViewById<View>(R.id.header_alarm)
        val inputDuration = activity.findViewById<View>(R.id.input_duration)
        val rowAutoTimer = activity.findViewById<View>(R.id.row_auto_timer)
        val rowEnableGoal = activity.findViewById<View>(R.id.row_enable_goal)
        val switchGoal = activity.findViewById<Switch>(R.id.switch_enable_goal)
        val btnTargetTime = activity.findViewById<View>(R.id.btn_target_time)
        val btnCurrentWakeTime = activity.findViewById<View>(R.id.btn_current_wake_time)
        val inputMinSleep = activity.findViewById<View>(R.id.input_min_sleep)

        switchGoal.isChecked = false
        switchEnable.isChecked = false

        assertTrue(headerTimer.isEnabled)
        assertEquals(1.0f, headerTimer.alpha, 0.01f)
        assertTrue(inputDuration.isEnabled)
        assertEquals(1.0f, inputDuration.alpha, 0.01f)
        assertTrue(rowAutoTimer.isEnabled)
        assertTrue(rowEnableGoal.isEnabled)
        assertEquals(1.0f, rowEnableGoal.alpha, 0.01f)
        assertTrue(headerAlarm.isEnabled)
        assertEquals(1.0f, headerAlarm.alpha, 0.01f)
        assertFalse(btnTargetTime.isEnabled)
        assertFalse(btnCurrentWakeTime.isEnabled)
        assertFalse(inputMinSleep.isEnabled)

        switchGoal.isChecked = true
        assertTrue(headerAlarm.isEnabled)
        assertEquals(1.0f, headerAlarm.alpha, 0.01f)
        assertTrue(btnTargetTime.isEnabled)
        assertTrue(btnCurrentWakeTime.isEnabled)
        assertTrue(inputMinSleep.isEnabled)
    }

    @Test
    fun testAutoTimerToggleIsOffByDefaultOnAppInstall() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().resume().get()

        val switchAutoTimer = activity.findViewById<Switch>(R.id.switch_auto_timer)
        assertNotNull(switchAutoTimer)
        assertFalse("Auto Sleep Timer (DND) toggle must be OFF by default on app install", switchAutoTimer.isChecked)

        val prefs = activity.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE)
        assertFalse("auto_timer_enabled preference must default to false", prefs.getBoolean("auto_timer_enabled", false))
    }

    @Test
    fun testCurrentWakeTimeButtonClickOpensDialogAndSavesTime() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().resume().get()

        val btnCurrentWakeTime = activity.findViewById<View>(R.id.btn_current_wake_time)
        val textCurrentWakeTimeValue = activity.findViewById<TextView>(R.id.text_current_wake_time_value)
        assertNotNull(btnCurrentWakeTime)
        assertNotNull(textCurrentWakeTimeValue)

        btnCurrentWakeTime.performClick()

        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull(dialog)
        assertTrue(dialog is android.app.TimePickerDialog)

        val targetCal = Calendar.getInstance()
        targetCal.add(Calendar.HOUR_OF_DAY, 12)
        val targetHour = targetCal.get(Calendar.HOUR_OF_DAY)
        val targetMin = targetCal.get(Calendar.MINUTE)

        val timePickerDialog = dialog as android.app.TimePickerDialog
        timePickerDialog.updateTime(targetHour, targetMin)

        val okButton = timePickerDialog.getButton(DialogInterface.BUTTON_POSITIVE)
        assertNotNull(okButton)
        okButton.performClick()

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val prefs = activity.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE)
        assertEquals(targetHour, prefs.getInt("current_wake_hour", -1))
        assertEquals(targetMin, prefs.getInt("current_wake_minute", -1))
    }

    @Test
    fun testSettingCurrentWakeTimeRegistersAlarmAndUpdatesNotification() {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("sleep_timer", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("wake_up_goal_enabled", true)
            .putInt("wake_up_goal_hour", 6)
            .putInt("wake_up_goal_minute", 30)
            .commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().resume().get()

        val btnCurrentWakeTime = activity.findViewById<View>(R.id.btn_current_wake_time)
        assertNotNull(btnCurrentWakeTime)

        btnCurrentWakeTime.performClick()

        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull(dialog)
        assertTrue(dialog is android.app.TimePickerDialog)

        val targetCal = Calendar.getInstance()
        targetCal.add(Calendar.HOUR_OF_DAY, 12)
        val targetHour = targetCal.get(Calendar.HOUR_OF_DAY)
        val targetMin = targetCal.get(Calendar.MINUTE)

        val timePickerDialog = dialog as android.app.TimePickerDialog
        timePickerDialog.updateTime(targetHour, targetMin)

        val okButton = timePickerDialog.getButton(DialogInterface.BUTTON_POSITIVE)
        assertNotNull(okButton)
        okButton.performClick()

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val serviceController = Robolectric.buildService(MainService::class.java)
        val service = serviceController.create().get()

        val shadowApp = Shadows.shadowOf(activity.application)
        var serviceIntent: Intent?
        while (shadowApp.nextStartedService.also { serviceIntent = it } != null) {
            service.onStartCommand(serviceIntent, 0, 1)
        }

        assertEquals(targetHour, prefs.getInt("current_wake_hour", -1))
        assertEquals(targetMin, prefs.getInt("current_wake_minute", -1))
        assertTrue("Wake alarm timestamp must be registered after setting current wake time",
            prefs.contains(MainService.KEY_WAKEUP_LAST_SCHEDULED_MS))

        val notificationManager = activity.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadowNotificationManager = Shadows.shadowOf(notificationManager)
        val notification = shadowNotificationManager.getNotification(1001)
        assertNotNull("Notification should be posted", notification)
        val text = notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString()
        val timeFormat = android.text.format.DateFormat.getTimeFormat(activity)
        val formattedExpectedTime = timeFormat.format(targetCal.time)
        assertTrue("Notification text should display wake time: $text", text.contains("\u23F0") || text.contains(formattedExpectedTime))
    }

    @Test
    fun testGoalContainerVisibleAndGoalInputsDisabledWhenGoalIsDisabled() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().resume().get()

        val switchEnable = activity.findViewById<Switch>(R.id.switch_enable_timer)
        val switchGoal = activity.findViewById<Switch>(R.id.switch_enable_goal)
        val goalContainer = activity.findViewById<View>(R.id.goal_container)
        val btnTargetTime = activity.findViewById<View>(R.id.btn_target_time)
        val inputMinSleep = activity.findViewById<View>(R.id.input_min_sleep)

        switchEnable.isChecked = true
        switchGoal.isChecked = false

        assertEquals(View.VISIBLE, goalContainer.visibility)
        val inputDuration = activity.findViewById<View>(R.id.input_duration)
        assertTrue(inputDuration.isEnabled)
        assertTrue(switchGoal.isEnabled)
        assertFalse(btnTargetTime.isEnabled)
        assertEquals(0.38f, btnTargetTime.alpha, 0.01f)
        assertFalse(inputMinSleep.isEnabled)
        assertEquals(0.38f, inputMinSleep.alpha, 0.01f)
    }

    @Test
    fun testSwitchRowClicksToggleSwitches() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().resume().get()

        val rowEnableTimer = activity.findViewById<View>(R.id.row_enable_timer)
        val switchEnableTimer = activity.findViewById<Switch>(R.id.switch_enable_timer)
        val rowAutoTimer = activity.findViewById<View>(R.id.row_auto_timer)
        val switchAutoTimer = activity.findViewById<Switch>(R.id.switch_auto_timer)
        val rowEnableGoal = activity.findViewById<View>(R.id.row_enable_goal)
        val switchEnableGoal = activity.findViewById<Switch>(R.id.switch_enable_goal)

        assertNotNull(rowEnableTimer)
        assertNotNull(switchEnableTimer)
        assertNotNull(rowAutoTimer)
        assertNotNull(switchAutoTimer)
        assertNotNull(rowEnableGoal)
        assertNotNull(switchEnableGoal)

        val initialEnable = switchEnableTimer.isChecked
        rowEnableTimer.performClick()
        assertEquals(!initialEnable, switchEnableTimer.isChecked)

        val initialAuto = switchAutoTimer.isChecked
        rowAutoTimer.performClick()
        assertEquals(!initialAuto, switchAutoTimer.isChecked)

        val initialGoal = switchEnableGoal.isChecked
        rowEnableGoal.performClick()
        assertEquals(!initialGoal, switchEnableGoal.isChecked)
    }

    @Test
    fun testProgrammaticHealthConnectUiUpdateDoesNotLogOrCheckPermissions() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        EventLogger.clear(application)

        val prefs = application.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("health_connect_enabled", true).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().resume().get()

        val events = EventLogger.getEvents(activity)
        val hcEvents = events.filter { it.contains("Health Connect") }
        assertTrue("Programmatic UI update when health_connect_enabled is true must not generate log entries, found: $hcEvents", hcEvents.isEmpty())
    }

    @Test
    fun testProgrammaticToggleDoesNotOpenSettingsPages() {
        HealthConnectManager.setClientForTesting(null, true)
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().resume().get()

        val switchAutoTimer = activity.findViewById<Switch>(R.id.switch_auto_timer)
        val switchHealthConnect = activity.findViewById<Switch>(R.id.switch_health_connect)

        assertNotNull(switchAutoTimer)
        assertNotNull(switchHealthConnect)

        val shadowActivity = Shadows.shadowOf(activity)
        while (shadowActivity.nextStartedActivity != null) {}

        switchAutoTimer.isChecked = true
        var nextIntent = shadowActivity.nextStartedActivity
        assertEquals("Programmatic toggle of Auto DND must not open DND settings page", null, nextIntent)

        switchHealthConnect.isChecked = true
        nextIntent = shadowActivity.nextStartedActivity
        assertEquals("Programmatic toggle of Health Connect must not open permissions settings page", null, nextIntent)
    }

    @Test
    fun testAutoTimerToggleDoesNotOpenDndSettings() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().resume().get()

        val switchAutoTimer = activity.findViewById<Switch>(R.id.switch_auto_timer)
        assertNotNull(switchAutoTimer)

        val shadowActivity = Shadows.shadowOf(activity)
        while (shadowActivity.nextStartedActivity != null) {}

        switchAutoTimer.isPressed = true
        switchAutoTimer.isChecked = true
        switchAutoTimer.isPressed = false

        val nextIntent = shadowActivity.nextStartedActivity
        assertEquals("Toggle of Auto DND must not open DND settings page", null, nextIntent)
    }

    @Test
    fun testAutoTimerRowClickTogglesSwitchWithoutLaunchingDndSettings() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().resume().get()

        val rowAutoTimer = activity.findViewById<View>(R.id.row_auto_timer)
        val switchAutoTimer = activity.findViewById<Switch>(R.id.switch_auto_timer)
        assertNotNull(rowAutoTimer)
        assertNotNull(switchAutoTimer)

        val shadowActivity = Shadows.shadowOf(activity)
        while (shadowActivity.nextStartedActivity != null) {}

        val initialChecked = switchAutoTimer.isChecked
        rowAutoTimer.performClick()

        assertEquals(!initialChecked, switchAutoTimer.isChecked)
        val nextIntent = shadowActivity.nextStartedActivity
        assertEquals("Clicking Auto DND row container must toggle switch without opening DND settings page", null, nextIntent)
    }

    @Test
    fun testHealthConnectRowClickLaunchesHealthConnectSettings() {
        HealthConnectManager.setClientForTesting(null, true)
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().resume().get()

        val rowHealthConnect = activity.findViewById<View>(R.id.row_health_connect)
        assertNotNull(rowHealthConnect)

        val shadowActivity = Shadows.shadowOf(activity)
        while (shadowActivity.nextStartedActivity != null) {}

        rowHealthConnect.performClick()

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val nextIntent = shadowActivity.nextStartedActivity
        assertNotNull("Clicking Health Connect row container must open Health Connect settings page", nextIntent)
        assertTrue("Intent must have FLAG_ACTIVITY_NEW_TASK set", (nextIntent!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
    }

    @Test
    fun testOpenSettingsWithFallbackLaunchesIntentWithNewTaskFlag() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().resume().get()
        val shadowActivity = Shadows.shadowOf(activity)
        while (shadowActivity.nextStartedActivity != null) {}

        activity.openSettingsWithFallback(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, android.provider.Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS)

        val nextIntent = shadowActivity.nextStartedActivity
        assertNotNull(nextIntent)
        assertEquals(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, nextIntent?.action)
        assertTrue("Intent must have FLAG_ACTIVITY_NEW_TASK set", (nextIntent!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
    }

    @Test
    fun testRequestExactAlarmPermissionIfNeededLaunchesIntentWithNewTaskFlag() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().resume().get()
        val shadowActivity = Shadows.shadowOf(activity)
        while (shadowActivity.nextStartedActivity != null) {}

        activity.requestExactAlarmPermissionIfNeeded()

        val nextIntent = shadowActivity.nextStartedActivity
        if (nextIntent != null) {
            assertEquals(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, nextIntent.action)
            assertTrue("Intent must have FLAG_ACTIVITY_NEW_TASK set", (nextIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
        }
    }

    @Test
    fun testButtonRowChildViewsAreNotClickableAndParentIsClickable() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().resume().get()

        val switchGoal = activity.findViewById<Switch>(R.id.switch_enable_goal)
        switchGoal.isChecked = true

        val rowIds = intArrayOf(R.id.input_duration, R.id.btn_target_time, R.id.input_min_sleep, R.id.btn_export, R.id.btn_import, R.id.btn_version, R.id.btn_feedback, R.id.btn_links)
        for (rowId in rowIds) {
            val parentRow = activity.findViewById<View>(rowId)
            assertNotNull("Row should exist", parentRow)
            assertTrue("Parent row should be clickable when enabled", parentRow.isClickable)

            if (parentRow is android.view.ViewGroup) {
                val childList = ArrayList<View>()
                findViewsOfType(parentRow, View::class.java, childList)
                for (child in childList) {
                    if (child != parentRow && child !is Switch) {
                        assertFalse("Child view inside button row should not be clickable: $child", child.isClickable)
                        assertFalse("Child view inside button row should not be focusable: $child", child.isFocusable)
                    }
                }
            }
        }
    }

    @Test
    fun testMainActivityResumeDoesNotAutomaticallyLaunchAwakeDialogActivity() {
        val sleepStart = System.currentTimeMillis() - 4 * 3600_000L
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("sleep_timer", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("wake_up_goal_enabled", true)
            .putLong("sleep_start_time_ms", sleepStart)
            .commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().resume().get()

        var nextIntent = Shadows.shadowOf(activity).nextStartedActivity
        while (nextIntent != null) {
            val className = nextIntent.component?.className
            assertFalse("MainActivity should not automatically launch AwakeDialogActivity on resume",
                AwakeDialogActivity::class.java.name == className)
            nextIntent = Shadows.shadowOf(activity).nextStartedActivity
        }
    }
}
