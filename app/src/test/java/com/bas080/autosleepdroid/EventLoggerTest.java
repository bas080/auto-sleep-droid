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
import static org.junit.Assert.assertFalse;
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
        CharSequence formattedLow = EventLogger.formatColoredEvent(context, events.get(0));
        assertTrue(formattedLow instanceof Spanned);
        assertFalse(formattedLow.toString().contains("[L]"));
        assertTrue(formattedLow.toString().contains("Low importance message"));
        ForegroundColorSpan[] lowSpans = ((Spanned) formattedLow).getSpans(0, formattedLow.length(), ForegroundColorSpan.class);
        assertTrue(lowSpans.length >= 2);
        assertEquals(0xFF999999, lowSpans[0].getForegroundColor());
        assertEquals(0xFF888888, lowSpans[1].getForegroundColor());

        // Check LEVEL_NORMAL line format and span color
        CharSequence formattedNormal = EventLogger.formatColoredEvent(context, events.get(1));
        assertTrue(formattedNormal instanceof Spanned);
        assertFalse(formattedNormal.toString().contains("[N]"));
        assertTrue(formattedNormal.toString().contains("Normal importance message"));
        ForegroundColorSpan[] normalSpans = ((Spanned) formattedNormal).getSpans(0, formattedNormal.length(), ForegroundColorSpan.class);
        assertTrue(normalSpans.length >= 2);
        assertEquals(0xFF999999, normalSpans[0].getForegroundColor());
        assertEquals(0xFF444444, normalSpans[1].getForegroundColor());

        // Check LEVEL_HIGH line format and span color
        CharSequence formattedHigh = EventLogger.formatColoredEvent(context, events.get(2));
        assertTrue(formattedHigh instanceof Spanned);
        assertFalse(formattedHigh.toString().contains("[H]"));
        assertTrue(formattedHigh.toString().contains("High importance message"));
        ForegroundColorSpan[] highSpans = ((Spanned) formattedHigh).getSpans(0, formattedHigh.length(), ForegroundColorSpan.class);
        assertTrue(highSpans.length >= 2);
        assertEquals(0xFF999999, highSpans[0].getForegroundColor());
        assertEquals(0xFF000000, highSpans[1].getForegroundColor());
    }

    @Test
    @Config(qualifiers = "night")
    public void testLogLevelFormattingDarkMode() {
        EventLogger.log(context, EventLogger.LEVEL_LOW, "Low importance night message");
        EventLogger.log(context, EventLogger.LEVEL_NORMAL, "Normal importance night message");
        EventLogger.log(context, EventLogger.LEVEL_HIGH, "High importance night message");

        List<String> events = EventLogger.getEvents(context);
        assertEquals(3, events.size());

        CharSequence formattedLow = EventLogger.formatColoredEvent(context, events.get(0));
        ForegroundColorSpan[] lowSpans = ((Spanned) formattedLow).getSpans(0, formattedLow.length(), ForegroundColorSpan.class);
        assertEquals(0xFF888888, lowSpans[0].getForegroundColor());
        assertEquals(0xFFAAAAAA, lowSpans[1].getForegroundColor());

        CharSequence formattedNormal = EventLogger.formatColoredEvent(context, events.get(1));
        ForegroundColorSpan[] normalSpans = ((Spanned) formattedNormal).getSpans(0, formattedNormal.length(), ForegroundColorSpan.class);
        assertEquals(0xFF888888, normalSpans[0].getForegroundColor());
        assertEquals(0xFFDDDDDD, normalSpans[1].getForegroundColor());

        CharSequence formattedHigh = EventLogger.formatColoredEvent(context, events.get(2));
        ForegroundColorSpan[] highSpans = ((Spanned) formattedHigh).getSpans(0, formattedHigh.length(), ForegroundColorSpan.class);
        assertEquals(0xFF888888, highSpans[0].getForegroundColor());
        assertEquals(0xFFFFFFFF, highSpans[1].getForegroundColor());
    }
}
