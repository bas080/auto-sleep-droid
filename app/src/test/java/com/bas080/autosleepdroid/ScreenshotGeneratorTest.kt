package com.bas080.autosleepdroid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.widget.ListView
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotGeneratorTest {

    private val targetLocales = listOf("en-US", "es-ES")
    private val phoneDirs = mutableListOf<File>()
    private val tabletDirs = mutableListOf<File>()

    @Before
    fun setUp() {
        var metadataDir = File("fastlane/metadata/android")
        if (!metadataDir.exists()) {
            metadataDir = File("../fastlane/metadata/android")
        }

        phoneDirs.clear()
        tabletDirs.clear()

        for (locale in targetLocales) {
            val localeImagesDir = File(metadataDir, "$locale/images")

            val phoneDir = File(localeImagesDir, "phoneScreenshots")
            if (!phoneDir.exists()) {
                phoneDir.mkdirs()
            }
            phoneDirs.add(phoneDir)

            val tabletDir = File(localeImagesDir, "tenInchScreenshots")
            if (!tabletDir.exists()) {
                tabletDir.mkdirs()
            }
            tabletDirs.add(tabletDir)
        }

        assertTrue("phoneScreenshots directories must exist", phoneDirs.all { it.exists() && it.isDirectory })
        assertTrue("tenInchScreenshots directories must exist", tabletDirs.all { it.exists() && it.isDirectory })
    }

    @After
    fun tearDown() {
    }

    @Test
    fun captureFeatureScreenshots() {
        val shouldGenerate = System.getenv("GENERATE_SCREENSHOTS") == "true" ||
                System.getProperty("generate.screenshots") == "true"
        if (!shouldGenerate) {
            println("Skipping Fastlane screenshot generation because GENERATE_SCREENSHOTS is not set.")
            return
        }

        captureScreenshot1MainOverview()
        captureScreenshot2GoalDisabled()
        captureScreenshot3DurationPicker()
        captureScreenshot4TimePicker()
        captureScreenshot5LinksDialog()
        captureScreenshot6EventLogs()
        captureScreenshot7UserManual()
        captureScreenshot8FeedbackOverlay()
        captureScreenshot9ImportDialog()
        captureScreenshot10DonateDialog()
    }

    private fun captureScreenshot1MainOverview() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences(PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear()
            .putBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, true)
            .putInt(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR, 7)
            .putInt(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE, 0)
            .putInt(PreferenceKeys.KEY_DURATION_MINUTES, 30)
            .putInt(PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES, 420)
            .putBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, true)
            .putBoolean(PreferenceKeys.KEY_AUTO_TIMER_ENABLED, true)
            .commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        drainActivityIntents(activity)
        shadowOf(Looper.getMainLooper()).idle()

        renderAndSaveView(activity.window.decorView, "1.png")
    }

    private fun captureScreenshot2GoalDisabled() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences(PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear()
            .putBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, false)
            .putInt(PreferenceKeys.KEY_DURATION_MINUTES, 45)
            .commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        drainActivityIntents(activity)
        shadowOf(Looper.getMainLooper()).idle()

        renderAndSaveView(activity.window.decorView, "2.png")
    }

    private fun captureScreenshot3DurationPicker() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        drainActivityIntents(activity)

        activity.findViewById<View>(R.id.input_duration)?.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val dialog = ShadowAlertDialog.getLatestDialog()
        val viewToRender = dialog?.window?.decorView ?: activity.window.decorView
        renderAndSaveView(viewToRender, "3.png")
    }

    private fun captureScreenshot4TimePicker() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        drainActivityIntents(activity)

        activity.findViewById<View>(R.id.btn_target_time)?.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val dialog = ShadowAlertDialog.getLatestDialog()
        val viewToRender = dialog?.window?.decorView ?: activity.window.decorView
        renderAndSaveView(viewToRender, "4.png")
    }

    private fun captureScreenshot5LinksDialog() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        drainActivityIntents(activity)

        activity.findViewById<View>(R.id.btn_links)?.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val dialog = ShadowAlertDialog.getLatestDialog()
        val viewToRender = dialog?.window?.decorView ?: activity.window.decorView
        renderAndSaveView(viewToRender, "5.png")
    }

    private fun captureScreenshot6EventLogs() {
        val app = RuntimeEnvironment.getApplication()
        EventLogger.clear(app)
        EventLogger.log(app, "Service started")
        EventLogger.log(app, "Sleep timer activated (30m)")
        EventLogger.log(app, "Do Not Disturb synced")
        EventLogger.log(app, "Wake alarm scheduled for 07:00")
        EventLogger.log(app, "Health Connect session recorded")

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        drainActivityIntents(activity)

        activity.findViewById<View>(R.id.btn_links)?.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val dialog = ShadowAlertDialog.getLatestDialog()
        val listView = dialog?.findViewById<ListView>(android.R.id.list)
        listView?.performItemClick(listView.adapter.getView(1, null, listView), 1, 1)
        shadowOf(Looper.getMainLooper()).idle()

        renderAndSaveView(activity.window.decorView, "6.png")
    }

    private fun captureScreenshot7UserManual() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        drainActivityIntents(activity)

        activity.findViewById<View>(R.id.btn_links)?.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val dialog = ShadowAlertDialog.getLatestDialog()
        val listView = dialog?.findViewById<ListView>(android.R.id.list)
        listView?.performItemClick(listView.adapter.getView(0, null, listView), 0, 0)
        shadowOf(Looper.getMainLooper()).idle()

        renderAndSaveView(activity.window.decorView, "7.png")
    }

    private fun captureScreenshot8FeedbackOverlay() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        drainActivityIntents(activity)

        val sampleCrash = "java.lang.NullPointerException: Simulated crash for feedback\n" +
                "\tat com.bas080.autosleepdroid.MainService.onStartCommand(MainService.kt:42)\n" +
                "\tat android.app.ActivityThread.main(ActivityThread.java:7356)"
        activity.showFeedbackOverlay(crashReport = sampleCrash)
        shadowOf(Looper.getMainLooper()).idle()

        renderAndSaveView(activity.window.decorView, "8.png")
    }

    private fun captureScreenshot9ImportDialog() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        drainActivityIntents(activity)

        activity.findViewById<View>(R.id.btn_import)?.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val dialog = ShadowAlertDialog.getLatestDialog()
        val viewToRender = dialog?.window?.decorView ?: activity.window.decorView
        renderAndSaveView(viewToRender, "9.png")
    }

    private fun captureScreenshot10DonateDialog() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        drainActivityIntents(activity)

        activity.maybeShowRandomDonateDialog(forceShow = true)
        shadowOf(Looper.getMainLooper()).idle()

        val dialog = ShadowAlertDialog.getLatestDialog()
        val viewToRender = dialog?.window?.decorView ?: activity.window.decorView
        renderAndSaveView(viewToRender, "10.png")
    }

    private fun drainActivityIntents(activity: MainActivity) {
        val shadowActivity = shadowOf(activity)
        while (shadowActivity.nextStartedActivity != null) {
            // Drain startup intents
        }
    }

    private fun renderAndSaveView(view: View, filename: String) {
        for (dir in phoneDirs) {
            renderAndSaveViewTo(view, File(dir, filename), 375, 667)
        }
        for (dir in tabletDirs) {
            renderAndSaveViewTo(view, File(dir, filename), 1024, 768)
        }
    }

    private fun renderAndSaveViewTo(view: View, outputFile: File, width: Int, height: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)

        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        assertTrue("Screenshot ${outputFile.name} should exist", outputFile.exists())
        assertTrue("Screenshot ${outputFile.name} should not be empty", outputFile.length() > 0)
    }
}
