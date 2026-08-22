package com.bas080.autosleepdroid;

import android.content.Context;
import android.content.Intent;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34})
public class BootReceiverTest {

    @Test
    public void testBootCompletedTriggersService() {
        Context context = ApplicationProvider.getApplicationContext();
        BootReceiver receiver = new BootReceiver();

        Intent bootIntent = new Intent(Intent.ACTION_BOOT_COMPLETED);
        receiver.onReceive(context, bootIntent);

        Intent nextService = ShadowApplication.getInstance().getNextStartedService();
        assertNotNull(nextService);
        assertEquals(SleepTimerService.class.getName(), nextService.getComponent().getClassName());
    }

    @Test
    public void testNonBootIntentIgnored() {
        Context context = ApplicationProvider.getApplicationContext();
        BootReceiver receiver = new BootReceiver();

        Intent otherIntent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        receiver.onReceive(context, otherIntent);

        Intent nextService = ShadowApplication.getInstance().getNextStartedService();
        assertNull(nextService);
    }
}
