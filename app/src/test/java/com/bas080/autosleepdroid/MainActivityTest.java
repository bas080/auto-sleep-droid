package com.bas080.autosleepdroid;

import android.Manifest;
import android.app.Application;
import android.content.Intent;
import android.widget.TextView;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.robolectric.shadows.ShadowApplication;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34})
public class MainActivityTest {

    @Before
    public void setUp() {
        Application application = ApplicationProvider.getApplicationContext();
        Shadows.shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        EventLogger.clear(application);
    }

    @Test
    public void testActivityCreation() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().get();
        assertNotNull(activity);
    }

    @Test
    public void testResumeRemainsOpenAndLogsEvents() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().get();

        // Simulate returning from Settings (onResume)
        controller.resume();

        assertFalse(activity.isFinishing());

        TextView textView = activity.findViewById(R.id.event_log_text);
        assertNotNull(textView);
        assertTrue(textView.getText().toString().contains("MainActivity resumed"));
    }

    @Test
    public void testEventLoggerUpdatesUI() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().resume().get();

        EventLogger.log(activity, "Test event message");

        TextView textView = activity.findViewById(R.id.event_log_text);
        assertNotNull(textView);
        assertTrue(textView.getText().toString().contains("Test event message"));
    }

    @Test
    public void testFormatColoredEvent() {
        CharSequence formatted = EventLogger.formatColoredEvent("8/29 14:30:00 Timer turned on");
        assertNotNull(formatted);
        assertTrue(formatted instanceof android.text.Spanned);

        android.text.Spanned spanned = (android.text.Spanned) formatted;
        android.text.style.ForegroundColorSpan[] colorSpans =
                spanned.getSpans(0, spanned.length(), android.text.style.ForegroundColorSpan.class);
        assertTrue(colorSpans.length >= 2);

        assertEquals(0xFF888888, colorSpans[0].getForegroundColor());
        assertEquals(0xFF000000, colorSpans[1].getForegroundColor());
    }

    @Test
    public void testNewIntentHandling() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().get();
        controller.newIntent(new Intent(ApplicationProvider.getApplicationContext(), MainActivity.class));
        assertNotNull(activity);
    }

    @Test
    public void testOnResumeStartsRedrawServiceIntent() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        controller.create();

        Application app = ApplicationProvider.getApplicationContext();
        ShadowApplication shadowApp = Shadows.shadowOf(app);

        // Consume service intents queued during onCreate
        while (shadowApp.getNextStartedService() != null) {}

        controller.resume();

        boolean foundRedrawIntent = false;
        Intent intent;
        while ((intent = shadowApp.getNextStartedService()) != null) {
            if (SleepTimerService.ACTION_REDRAW_NOTIFICATION.equals(intent.getAction())
                    && SleepTimerService.class.getName().equals(intent.getComponent().getClassName())) {
                foundRedrawIntent = true;
                break;
            }
        }
        assertTrue("Expected ACTION_REDRAW_NOTIFICATION intent when MainActivity is resumed", foundRedrawIntent);
    }
}
