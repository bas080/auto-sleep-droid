package com.bas080.autosleepdroid

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BootReceiverTest {

    @Test
    fun testBootCompletedTriggersService() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val receiver = BootReceiver()

        val bootIntent = Intent(Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(context, bootIntent)

        val shadowApp = Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>())
        val nextService = shadowApp.nextStartedService
        assertNotNull(nextService)
        assertEquals(MainService::class.java.name, nextService?.component?.className)
    }

    @Test
    fun testNonBootIntentIgnored() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val receiver = BootReceiver()

        val otherIntent = Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED)
        receiver.onReceive(context, otherIntent)

        val shadowApp = Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>())
        val nextService = shadowApp.nextStartedService
        assertNull(nextService)
    }
}
