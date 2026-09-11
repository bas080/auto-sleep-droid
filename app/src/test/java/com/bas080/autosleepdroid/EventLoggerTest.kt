package com.bas080.autosleepdroid

import android.content.Context
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EventLoggerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        EventLogger.clear(context)
    }

    @Test
    fun testLogLevelFormatting() {
        EventLogger.log(context, EventLogger.LEVEL_LOW, "Low importance message")
        EventLogger.log(context, EventLogger.LEVEL_NORMAL, "Normal importance message")
        EventLogger.log(context, EventLogger.LEVEL_HIGH, "High importance message")

        val events = EventLogger.getEvents(context)
        assertEquals(3, events.size)

        val formattedLow = EventLogger.formatColoredEvent(context, events[0])
        assertTrue(formattedLow is Spanned)
        assertFalse(formattedLow.toString().contains("[L]"))
        assertTrue(formattedLow.toString().contains("Low importance message"))
        val lowSpans = (formattedLow as Spanned).getSpans(0, formattedLow.length, ForegroundColorSpan::class.java)
        assertTrue(lowSpans.size >= 2)
        assertEquals(-0x666667, lowSpans[0].foregroundColor)
        assertEquals(-0x777778, lowSpans[1].foregroundColor)

        val formattedNormal = EventLogger.formatColoredEvent(context, events[1])
        assertTrue(formattedNormal is Spanned)
        assertFalse(formattedNormal.toString().contains("[N]"))
        assertTrue(formattedNormal.toString().contains("Normal importance message"))
        val normalSpans = (formattedNormal as Spanned).getSpans(0, formattedNormal.length, ForegroundColorSpan::class.java)
        assertTrue(normalSpans.size >= 2)
        assertEquals(-0x666667, normalSpans[0].foregroundColor)
        assertEquals(-0xbbbbbc, normalSpans[1].foregroundColor)

        val formattedHigh = EventLogger.formatColoredEvent(context, events[2])
        assertTrue(formattedHigh is Spanned)
        assertFalse(formattedHigh.toString().contains("[H]"))
        assertTrue(formattedHigh.toString().contains("High importance message"))
        val highSpans = (formattedHigh as Spanned).getSpans(0, formattedHigh.length, ForegroundColorSpan::class.java)
        assertTrue(highSpans.size >= 2)
        assertEquals(-0x666667, highSpans[0].foregroundColor)
        assertEquals(-0x1000000, highSpans[1].foregroundColor)
    }

    @Test
    @Config(qualifiers = "night")
    fun testLogLevelFormattingDarkMode() {
        EventLogger.log(context, EventLogger.LEVEL_LOW, "Low importance night message")
        EventLogger.log(context, EventLogger.LEVEL_NORMAL, "Normal importance night message")
        EventLogger.log(context, EventLogger.LEVEL_HIGH, "High importance night message")

        val events = EventLogger.getEvents(context)
        assertEquals(3, events.size)

        val formattedLow = EventLogger.formatColoredEvent(context, events[0])
        val lowSpans = (formattedLow as Spanned).getSpans(0, formattedLow.length, ForegroundColorSpan::class.java)
        assertEquals(-0x777778, lowSpans[0].foregroundColor)
        assertEquals(-0x555556, lowSpans[1].foregroundColor)

        val formattedNormal = EventLogger.formatColoredEvent(context, events[1])
        val normalSpans = (formattedNormal as Spanned).getSpans(0, formattedNormal.length, ForegroundColorSpan::class.java)
        assertEquals(-0x777778, normalSpans[0].foregroundColor)
        assertEquals(-0x222223, normalSpans[1].foregroundColor)

        val formattedHigh = EventLogger.formatColoredEvent(context, events[2])
        val highSpans = (formattedHigh as Spanned).getSpans(0, formattedHigh.length, ForegroundColorSpan::class.java)
        assertEquals(-0x777778, highSpans[0].foregroundColor)
        assertEquals(-0x1, highSpans[1].foregroundColor)
    }

    @Test
    fun testFilePersistenceAndAppend() {
        EventLogger.log(context, "First log message")
        EventLogger.log(context, "Second log message")

        val logFile = File(context.filesDir, "event_logs.txt")
        assertTrue(logFile.exists())

        val linesInFile = logFile.readLines().filter { it.trim().isNotEmpty() }
        assertEquals(2, linesInFile.size)

        val eventsFromApi = EventLogger.getEvents(context)
        assertEquals(2, eventsFromApi.size)
        assertEquals(linesInFile, eventsFromApi)
    }

    @Test
    fun testFilePruningExceedingMaxLogs() {
        for (i in 1..510) {
            EventLogger.log(context, "Message number $i")
        }

        val events = EventLogger.getEvents(context)
        assertEquals(500, events.size)
        assertTrue(events.first().contains("Message number 11"))
        assertTrue(events.last().contains("Message number 510"))

        val logFile = File(context.filesDir, "event_logs.txt")
        val linesInFile = logFile.readLines().filter { it.trim().isNotEmpty() }
        assertEquals(500, linesInFile.size)
        assertEquals(events, linesInFile)
    }

    @Test
    fun testClearLogs() {
        EventLogger.log(context, "Message to clear")
        val logFile = File(context.filesDir, "event_logs.txt")
        assertTrue(logFile.exists())

        EventLogger.clear(context)

        assertTrue(EventLogger.getEvents(context).isEmpty())
        assertFalse(logFile.exists())
    }

    @Test
    fun testLegacySharedPreferencesMigration() {
        val prefs = context.getSharedPreferences("event_logger", Context.MODE_PRIVATE)
        prefs.edit().putString("logs", "1/1 12:00:00 \u0001Legacy log entry 1\n1/1 12:00:01 \u0001Legacy log entry 2").commit()

        val logFile = File(context.filesDir, "event_logs.txt")
        if (logFile.exists()) {
            logFile.delete()
        }

        val events = EventLogger.getEvents(context)
        assertEquals(2, events.size)
        assertTrue(events[0].contains("Legacy log entry 1"))
        assertTrue(events[1].contains("Legacy log entry 2"))

        assertTrue(logFile.exists())
        assertFalse(prefs.contains("logs"))
    }
}
