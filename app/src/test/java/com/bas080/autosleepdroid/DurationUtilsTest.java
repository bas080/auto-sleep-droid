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
        assertEquals(-1, DurationUtils.parseDurationMinutes("10x10h4m"));
        assertEquals(-1, DurationUtils.parseDurationMinutes("10m10"));
        assertEquals(-1, DurationUtils.parseDurationMinutes("10h20h"));
        assertEquals(-1, DurationUtils.parseDurationMinutes("abc"));
        assertEquals(-1, DurationUtils.parseDurationMinutes(null));
        assertEquals(-1, DurationUtils.parseDurationMinutes("  "));
    }

    @Test
    public void testParseDurationMinutesForGoalPlainNumbersAsHours() {
        // Plain integer represents hours
        assertEquals(420, DurationUtils.parseDurationMinutesForGoal("7"));
        assertEquals(60, DurationUtils.parseDurationMinutesForGoal("1"));

        // Plain float represents hours (e.g. 0.5 = 30m, 7.5 = 450m)
        assertEquals(30, DurationUtils.parseDurationMinutesForGoal("0.5"));
        assertEquals(30, DurationUtils.parseDurationMinutesForGoal("0,5"));
        assertEquals(450, DurationUtils.parseDurationMinutesForGoal("7.5"));
        assertEquals(450, DurationUtils.parseDurationMinutesForGoal("7,5"));

        // Explicit units override default
        assertEquals(30, DurationUtils.parseDurationMinutesForGoal("30m"));
        assertEquals(450, DurationUtils.parseDurationMinutesForGoal("450m"));
        assertEquals(420, DurationUtils.parseDurationMinutesForGoal("7h"));
        assertEquals(450, DurationUtils.parseDurationMinutesForGoal("7h 30m"));
        assertEquals(450, DurationUtils.parseDurationMinutesForGoal("7h30m"));

        // Invalid inputs
        assertEquals(-1, DurationUtils.parseDurationMinutesForGoal("abc"));
        assertEquals(-1, DurationUtils.parseDurationMinutesForGoal(null));
        assertEquals(-1, DurationUtils.parseDurationMinutesForGoal("  "));
        assertEquals(-1, DurationUtils.parseDurationMinutesForGoal("0"));
    }
}
