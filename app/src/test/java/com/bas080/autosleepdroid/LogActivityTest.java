package com.bas080.autosleepdroid;

import android.app.Application;
import android.widget.TextView;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34})
public class LogActivityTest {

    @Before
    public void setUp() {
        Application application = ApplicationProvider.getApplicationContext();
        EventLogger.clear(application);
    }

    @Test
    public void testActivityCreation() {
        ActivityController<LogActivity> controller = Robolectric.buildActivity(LogActivity.class);
        LogActivity activity = controller.create().get();
        assertNotNull(activity);
    }

    @Test
    public void testEventLoggerUpdatesUI() {
        ActivityController<LogActivity> controller = Robolectric.buildActivity(LogActivity.class);
        LogActivity activity = controller.create().resume().get();

        EventLogger.log(activity, "Test log message from test");

        TextView textView = activity.findViewById(R.id.event_log_text);
        assertNotNull(textView);
        assertTrue(textView.getText().toString().contains("Test log message from test"));
    }
}
