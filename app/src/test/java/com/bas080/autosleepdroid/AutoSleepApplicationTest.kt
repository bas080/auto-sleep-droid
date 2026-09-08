package com.bas080.autosleepdroid

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoSleepApplicationTest {

    @Test
    fun testUncaughtExceptionHandlerLogsCrash() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val application = context.applicationContext as AutoSleepApplication
        assertNotNull(application)

        val handler = Thread.getDefaultUncaughtExceptionHandler()
        assertNotNull(handler)

        val dummyException = RuntimeException("Test crash logging")
        try {
            handler?.uncaughtException(Thread.currentThread(), dummyException)
        } catch (ignored: RuntimeException) {
        }

        val logs = EventLogger.getEvents(context)
        var foundCrashLog = false
        for (logLine in logs) {
            if (logLine.contains("Test crash logging")) {
                foundCrashLog = true
                break
            }
        }
        assertTrue("EventLogger must record uncaught exception message", foundCrashLog)
    }
}
