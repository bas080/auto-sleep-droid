package com.bas080.autosleepdroid

import android.content.DialogInterface
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AwakeDialogActivityTest {

    @Before
    fun setUp() {
    }

    @Test
    fun testConfirmAwakeButtonStartsServiceWithAwakeActionAndFinishes() {
        val controller = Robolectric.buildActivity(AwakeDialogActivity::class.java)
        val activity = controller.create().get()

        val dialog = activity.alertDialog
        assertNotNull(dialog)

        val btnAwake = dialog?.getButton(DialogInterface.BUTTON_POSITIVE)
        assertNotNull(btnAwake)

        btnAwake?.performClick()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val shadowApp = Shadows.shadowOf(activity.application)
        val startedIntent: Intent? = shadowApp.nextStartedService
        assertNotNull(startedIntent)
        assertEquals(MainService.ACTION_AWAKE, startedIntent?.action)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun testCancelButtonFinishesActivityWithoutStartingService() {
        val controller = Robolectric.buildActivity(AwakeDialogActivity::class.java)
        val activity = controller.create().get()

        val dialog = activity.alertDialog
        assertNotNull(dialog)

        val btnCancel = dialog?.getButton(DialogInterface.BUTTON_NEGATIVE)
        assertNotNull(btnCancel)

        btnCancel?.performClick()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertTrue(activity.isFinishing)
    }
}
