package com.bas080.autosleepdroid;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34})
public class AutoSleepApplicationTest {

    @Test
    public void testUncaughtExceptionHandlerLogsCrash() {
        Context context = ApplicationProvider.getApplicationContext();
        AutoSleepApplication application = (AutoSleepApplication) context.getApplicationContext();
        assertNotNull(application);

        Thread.UncaughtExceptionHandler handler = Thread.getDefaultUncaughtExceptionHandler();
        assertNotNull(handler);

        RuntimeException dummyException = new RuntimeException("Test crash logging");
        try {
            handler.uncaughtException(Thread.currentThread(), dummyException);
        } catch (RuntimeException e) {
            // UncaughtExceptionHandler delegates to default handler which may rethrow in test
        }

        java.util.List<String> logs = EventLogger.getEvents(context);
        boolean foundCrashLog = false;
        for (String logLine : logs) {
            if (logLine != null && logLine.contains("Test crash logging")) {
                foundCrashLog = true;
                break;
            }
        }
        assertTrue("EventLogger must record uncaught exception message", foundCrashLog);
    }
}
