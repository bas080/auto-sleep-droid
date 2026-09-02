package com.bas080.autosleepdroid;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DurationUtilsTest {

    @Test
    public void testFormatDurationString() {
        assertEquals("15m", DurationUtils.formatDurationString(15));
        assertEquals("1h", DurationUtils.formatDurationString(60));
        assertEquals("1h 15m", DurationUtils.formatDurationString(75));
        assertEquals("7h 30m", DurationUtils.formatDurationString(450));
    }

    @Test
    public void testParseDurationMinutesDefaultPlainNumbersAsMinutes() {
        assertEquals(30, DurationUtils.parseDurationMinutes("30"));
        assertEquals(60, DurationUtils.parseDurationMinutes("1h"));
        assertEquals(120, DurationUtils.parseDurationMinutes("2H"));
        assertEquals(135, DurationUtils.parseDurationMinutes("2h15m"));
        assertEquals(450, DurationUtils.parseDurationMinutes("7h30m"));
        assertEquals(450, DurationUtils.parseDurationMinutes("7h 30m"));
        assertEquals(75, DurationUtils.parseDurationMinutes("1 h 15 m"));
        assertEquals(130, DurationUtils.parseDurationMinutes("2h10m5s"));
        assertEquals(15, DurationUtils.parseDurationMinutes("15m30s"));
        assertEquals(18, DurationUtils.parseDurationMinutes("0.3h"));
        assertEquals(18, DurationUtils.parseDurationMinutes("0,3h"));
        assertEquals(90, DurationUtils.parseDurationMinutes("1.5h"));
        assertEquals(-1, DurationUtils.parseDurationMinutes("1h 20x"));
        assertEquals(-1, DurationUtils.parseDurationMinutes("10x10h4m"));
        assertEquals(-1, DurationUtils.parseDurationMinutes("10m10"));
        assertEquals(-1, DurationUtils.parseDurationMinutes("10h20h"));
        assertEquals(-1, DurationUtils.parseDurationMinutes("abc"));
        assertEquals(-1, DurationUtils.parseDurationMinutes(null));
        assertEquals(-1, DurationUtils.parseDurationMinutes("  "));
    }

    @Test
    public void testParseDurationMinutesWithHoursDefaultUnit() {
        // Plain integer represents hours
        assertEquals(420, DurationUtils.parseDurationMinutes("7", DurationUtils.DefaultUnit.HOURS));
        assertEquals(60, DurationUtils.parseDurationMinutes("1", DurationUtils.DefaultUnit.HOURS));

        // Plain float represents hours (e.g. 0.5 = 30m, 7.5 = 450m)
        assertEquals(30, DurationUtils.parseDurationMinutes("0.5", DurationUtils.DefaultUnit.HOURS));
        assertEquals(30, DurationUtils.parseDurationMinutes("0,5", DurationUtils.DefaultUnit.HOURS));
        assertEquals(450, DurationUtils.parseDurationMinutes("7.5", DurationUtils.DefaultUnit.HOURS));
        assertEquals(450, DurationUtils.parseDurationMinutes("7,5", DurationUtils.DefaultUnit.HOURS));

        // Float with 'h' suffix
        assertEquals(18, DurationUtils.parseDurationMinutes("0.3h", DurationUtils.DefaultUnit.HOURS));
        assertEquals(18, DurationUtils.parseDurationMinutes("0,3h", DurationUtils.DefaultUnit.HOURS));
        assertEquals(90, DurationUtils.parseDurationMinutes("1.5h", DurationUtils.DefaultUnit.HOURS));

        // Explicit units override default
        assertEquals(30, DurationUtils.parseDurationMinutes("30m", DurationUtils.DefaultUnit.HOURS));
        assertEquals(450, DurationUtils.parseDurationMinutes("450m", DurationUtils.DefaultUnit.HOURS));
        assertEquals(420, DurationUtils.parseDurationMinutes("7h", DurationUtils.DefaultUnit.HOURS));
        assertEquals(450, DurationUtils.parseDurationMinutes("7h 30m", DurationUtils.DefaultUnit.HOURS));
        assertEquals(450, DurationUtils.parseDurationMinutes("7h30m", DurationUtils.DefaultUnit.HOURS));

        // Invalid inputs
        assertEquals(-1, DurationUtils.parseDurationMinutes("abc", DurationUtils.DefaultUnit.HOURS));
        assertEquals(-1, DurationUtils.parseDurationMinutes(null, DurationUtils.DefaultUnit.HOURS));
        assertEquals(-1, DurationUtils.parseDurationMinutes("  ", DurationUtils.DefaultUnit.HOURS));
        assertEquals(-1, DurationUtils.parseDurationMinutes("0", DurationUtils.DefaultUnit.HOURS));
    }
}
