package com.bas080.autosleepdroid;

import android.content.Context;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34})
public class EventLoggerTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        EventLogger.clear(context);
    }

    @Test
    public void testLogLevelFormatting() {
        EventLogger.log(context, EventLogger.LEVEL_LOW, "Low importance message");
        EventLogger.log(context, EventLogger.LEVEL_NORMAL, "Normal importance message");
        EventLogger.log(context, EventLogger.LEVEL_HIGH, "High importance message");

        List<String> events = EventLogger.getEvents(context);
        assertEquals(3, events.size());

        // Check LEVEL_LOW line format and span color
        CharSequence formattedLow = EventLogger.formatColoredEvent(events.get(0));
        assertTrue(formattedLow instanceof Spanned);
        ForegroundColorSpan[] lowSpans = ((Spanned) formattedLow).getSpans(0, formattedLow.length(), ForegroundColorSpan.class);
        assertTrue(lowSpans.length >= 2);
        assertEquals(0xFF888888, lowSpans[1].getForegroundColor());

        // Check LEVEL_NORMAL line format and span color
        CharSequence formattedNormal = EventLogger.formatColoredEvent(events.get(1));
        assertTrue(formattedNormal instanceof Spanned);
        ForegroundColorSpan[] normalSpans = ((Spanned) formattedNormal).getSpans(0, formattedNormal.length(), ForegroundColorSpan.class);
        assertTrue(normalSpans.length >= 2);
        assertEquals(0xFF444444, normalSpans[1].getForegroundColor());

        // Check LEVEL_HIGH line format and span color
        CharSequence formattedHigh = EventLogger.formatColoredEvent(events.get(2));
        assertTrue(formattedHigh instanceof Spanned);
        ForegroundColorSpan[] highSpans = ((Spanned) formattedHigh).getSpans(0, formattedHigh.length(), ForegroundColorSpan.class);
        assertTrue(highSpans.length >= 2);
        assertEquals(0xFF000000, highSpans[1].getForegroundColor());
    }
}
