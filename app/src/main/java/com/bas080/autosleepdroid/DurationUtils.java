package com.bas080.autosleepdroid;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DurationUtils {

    public enum DefaultUnit {
        MINUTES,
        HOURS
    }

    public static String formatDurationString(int totalMinutes) {
        if (totalMinutes < 60) {
            return totalMinutes + "m";
        }
        int hours = totalMinutes / 60;
        int mins = totalMinutes % 60;
        if (mins == 0) {
            return hours + "h";
        }
        return hours + "h " + mins + "m";
    }

    public static int parseDurationMinutes(String input) {
        return parseDurationMinutes(input, DefaultUnit.MINUTES);
    }

    public static int parseDurationMinutes(String input, DefaultUnit defaultUnit) {
        if (input == null) {
            return -1;
        }
        String raw = input.trim();
        if (raw.isEmpty()) {
            return -1;
        }
        String s = raw.replaceAll("\\s+", "").toLowerCase(Locale.US);

        try {
            if (s.matches("^[0-9]+([.,][0-9]+)?$")) {
                double val = Double.parseDouble(s.replace(',', '.'));
                if (val <= 0) {
                    return -1;
                }
                long totalMins = (defaultUnit == DefaultUnit.HOURS) ? Math.round(val * 60.0) : Math.round(val);
                return (totalMins > 0 && totalMins <= Integer.MAX_VALUE) ? (int) totalMins : -1;
            }

            Matcher m;

            m = Pattern.compile("^([0-9]+)h$").matcher(s);
            if (m.matches()) {
                long hours = Long.parseLong(m.group(1));
                long total = hours * 60L;
                return (total > 0 && total <= Integer.MAX_VALUE) ? (int) total : -1;
            }

            m = Pattern.compile("^([0-9]+)m$").matcher(s);
            if (m.matches()) {
                long mins = Long.parseLong(m.group(1));
                return (mins > 0 && mins <= Integer.MAX_VALUE) ? (int) mins : -1;
            }

            m = Pattern.compile("^([0-9]+)h([0-9]+)m$").matcher(s);
            if (m.matches()) {
                long hours = Long.parseLong(m.group(1));
                long mins = Long.parseLong(m.group(2));
                long total = hours * 60L + mins;
                return (total > 0 && total <= Integer.MAX_VALUE) ? (int) total : -1;
            }

            m = Pattern.compile("^([0-9]+)m[0-9]+s$").matcher(s);
            if (m.matches()) {
                long mins = Long.parseLong(m.group(1));
                return (mins > 0 && mins <= Integer.MAX_VALUE) ? (int) mins : -1;
            }

            m = Pattern.compile("^([0-9]+)h[0-9]+s$").matcher(s);
            if (m.matches()) {
                long hours = Long.parseLong(m.group(1));
                long total = hours * 60L;
                return (total > 0 && total <= Integer.MAX_VALUE) ? (int) total : -1;
            }

            m = Pattern.compile("^([0-9]+)h([0-9]+)m[0-9]+s$").matcher(s);
            if (m.matches()) {
                long hours = Long.parseLong(m.group(1));
                long mins = Long.parseLong(m.group(2));
                long total = hours * 60L + mins;
                return (total > 0 && total <= Integer.MAX_VALUE) ? (int) total : -1;
            }
        } catch (NumberFormatException ignored) {
            return -1;
        }

        return -1;
    }
}
