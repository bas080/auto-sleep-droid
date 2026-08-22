package com.bas080.autosleepdroid;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34})
public class MediaSessionAccessServiceTest {

    @Test
    public void testPauseAllDoesNotCrashWithoutGrantedAccess() {
        Context context = ApplicationProvider.getApplicationContext();
        // Calling pauseAll should catch SecurityException gracefully when notification listener access is not granted
        MediaSessionAccessService.pauseAll(context);
    }
}
