package com.bas080.autosleepdroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EventLogger {
    public static final int LEVEL_LOW = 0;
    public static final int LEVEL_NORMAL = 1;
    public static final int LEVEL_HIGH = 2;

    private static final String PREF_NAME = "event_logger";
    private static final String KEY_LOGS = "logs";
    private static final int MAX_LOGS = 500;
    private static final List<String> events = new ArrayList<>();
    private static boolean loaded = false;
    private static Listener listener;
    private static Context appContext;

    public interface Listener {
        void onEventLogged(String event);
    }

    public static synchronized void setListener(Listener l) {
        listener = l;
    }

    public static synchronized void log(Context context, int level, String message) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
        Context targetContext = context != null ? context : appContext;
        ensureLoaded(targetContext);

        String timestamp = new SimpleDateFormat("M/d HH:mm:ss", Locale.US).format(new Date());
        char marker;
        switch (level) {
            case LEVEL_LOW:
                marker = '\u0000';
                break;
            case LEVEL_HIGH:
                marker = '\u0002';
                break;
            case LEVEL_NORMAL:
            default:
                marker = '\u0001';
                break;
        }

        String line = timestamp + " " + marker + message;

        events.add(line);
        if (events.size() > MAX_LOGS) {
            events.remove(0);
        }

        if (targetContext != null) {
            persistLogs(targetContext.getApplicationContext());
        }

        final Listener currentListener = listener;
        if (currentListener != null) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                currentListener.onEventLogged(line);
            } else {
                new Handler(Looper.getMainLooper()).post(() -> currentListener.onEventLogged(line));
            }
        }
    }

    public static synchronized void log(Context context, String message) {
        log(context, LEVEL_NORMAL, message);
    }

    public static synchronized void log(int level, String message) {
        log(null, level, message);
    }

    public static synchronized void log(String message) {
        log(null, LEVEL_NORMAL, message);
    }

    public static synchronized List<String> getEvents(Context context) {
        ensureLoaded(context);
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    public static boolean isDarkMode(Context context) {
        if (context == null) {
            context = appContext;
        }
        if (context == null || context.getResources() == null || context.getResources().getConfiguration() == null) {
            return false;
        }
        int currentNightMode = context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    public static CharSequence formatColoredEvent(String line) {
        return formatColoredEvent(null, line);
    }

    public static CharSequence formatColoredEvent(Context context, String line) {
        if (line == null || line.isEmpty()) {
            return "";
        }

        boolean darkMode = isDarkMode(context);

        int spaceIdx = line.indexOf(' ');
        int secondSpaceIdx = spaceIdx != -1 ? line.indexOf(' ', spaceIdx + 1) : -1;
        int timestampEnd = secondSpaceIdx != -1 ? secondSpaceIdx : (spaceIdx != -1 ? spaceIdx : 0);

        int level = LEVEL_NORMAL;
        String timestamp = timestampEnd > 0 ? line.substring(0, timestampEnd) : "";
        String rawMessage = timestampEnd < line.length() ? line.substring(timestampEnd) : "";

        if (rawMessage.contains("\u0000")) {
            level = LEVEL_LOW;
            rawMessage = rawMessage.replace("\u0000", "");
        } else if (rawMessage.contains("\u0002")) {
            level = LEVEL_HIGH;
            rawMessage = rawMessage.replace("\u0002", "");
        } else if (rawMessage.contains("\u0001")) {
            level = LEVEL_NORMAL;
            rawMessage = rawMessage.replace("\u0001", "");
        }

        String displayString = timestamp.isEmpty() ? rawMessage.trim() : (timestamp + rawMessage);
        SpannableString spannable = new SpannableString(displayString);

        int timestampColor = darkMode ? 0xFF888888 : 0xFF999999;
        if (!timestamp.isEmpty() && timestamp.length() <= displayString.length()) {
            spannable.setSpan(new ForegroundColorSpan(timestampColor), 0, timestamp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        int messageStart = timestamp.isEmpty() ? 0 : timestamp.length();

        int textColor;
        boolean isBold = false;

        if (darkMode) {
            if (level == LEVEL_LOW) {
                textColor = 0xFFAAAAAA;
            } else if (level == LEVEL_HIGH) {
                textColor = 0xFFFFFFFF;
                isBold = true;
            } else {
                textColor = 0xFFDDDDDD;
            }
        } else {
            if (level == LEVEL_LOW) {
                textColor = 0xFF888888;
            } else if (level == LEVEL_HIGH) {
                textColor = 0xFF000000;
                isBold = true;
            } else {
                textColor = 0xFF444444;
            }
        }

        if (messageStart < displayString.length()) {
            spannable.setSpan(new ForegroundColorSpan(textColor), messageStart, displayString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (isBold) {
                spannable.setSpan(new StyleSpan(Typeface.BOLD), messageStart, displayString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        return spannable;
    }

    public static synchronized void clear(Context context) {
        events.clear();
        appContext = null;
        loaded = false;
        if (context != null) {
            SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit().remove(KEY_LOGS).apply();
        }
    }

    private static void ensureLoaded(Context context) {
        if (!loaded && context != null) {
            SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String raw = prefs.getString(KEY_LOGS, "");
            if (raw != null && !raw.isEmpty()) {
                String[] lines = raw.split("\n");
                for (String line : lines) {
                    if (!line.trim().isEmpty()) {
                        events.add(line);
                    }
                }
            }
            loaded = true;
        }
    }

    private static void persistLogs(Context context) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < events.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append(events.get(i));
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LOGS, sb.toString()).apply();
    }
}
