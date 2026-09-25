package com.bas080.autosleepdroid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.widget.TextView
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

    private lateinit var phoneScreenshotsDir: File
    private lateinit var tabletScreenshotsDir: File

    @Before
    fun setUp() {
        var baseDir = File("fastlane/metadata/android/en-US/images")
        if (!baseDir.exists()) {
            baseDir = File("../fastlane/metadata/android/en-US/images")
        }

        phoneScreenshotsDir = File(baseDir, "phoneScreenshots")
        if (!phoneScreenshotsDir.exists()) {
            phoneScreenshotsDir.mkdirs()
        }

        tabletScreenshotsDir = File(baseDir, "tenInchScreenshots")
        if (!tabletScreenshotsDir.exists()) {
            tabletScreenshotsDir.mkdirs()
        }

        assertTrue("phoneScreenshots directory must exist", phoneScreenshotsDir.exists() && phoneScreenshotsDir.isDirectory)
        assertTrue("tenInchScreenshots directory must exist", tabletScreenshotsDir.exists() && tabletScreenshotsDir.isDirectory)
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
        captureScreenshot1MainScreen()
        captureScreenshot2EventLogs()
    }

    private fun captureScreenshot1MainScreen() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        shadowOf(Looper.getMainLooper()).idle()

        val decorView = activity.window.decorView
        renderAndSaveView(decorView, File(phoneScreenshotsDir, "1.png"), 375, 667)
        renderAndSaveView(decorView, File(tabletScreenshotsDir, "1.png"), 1024, 768)
    }

    private fun captureScreenshot2EventLogs() {
        val app = RuntimeEnvironment.getApplication()
        EventLogger.clear(app)
        EventLogger.log(app, "Service started")
        EventLogger.log(app, "Sleep timer activated (30m)")
        EventLogger.log(app, "Do Not Disturb synced")

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val btnLinks = activity.findViewById<View>(R.id.btn_links)
        btnLinks?.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val dialog = ShadowAlertDialog.getLatestDialog()
        val viewToRender = dialog?.window?.decorView ?: activity.window.decorView
        renderAndSaveView(viewToRender, File(phoneScreenshotsDir, "2.png"), 375, 667)
        renderAndSaveView(viewToRender, File(tabletScreenshotsDir, "2.png"), 1024, 768)
    }

    private fun renderAndSaveView(view: View, outputFile: File, width: Int, height: Int) {
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
