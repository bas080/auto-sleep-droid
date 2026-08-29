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

    public static synchronized void log(Context context, String message) {
        if (context != null && appContext == null) {
            appContext = context.getApplicationContext();
        }
        Context targetContext = context != null ? context : appContext;
        ensureLoaded(targetContext);

        String timestamp = new SimpleDateFormat("M/d HH:mm:ss", Locale.US).format(new Date());
        String line = timestamp + " " + message;

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

    public static synchronized void log(String message) {
        log(null, message);
    }

    public static synchronized List<String> getEvents(Context context) {
        ensureLoaded(context);
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    public static CharSequence formatColoredEvent(String line) {
        if (line == null || line.isEmpty()) {
            return "";
        }
        SpannableString spannable = new SpannableString(line);
        int spaceIdx = line.indexOf(' ');
        int secondSpaceIdx = spaceIdx != -1 ? line.indexOf(' ', spaceIdx + 1) : -1;
        int timestampEnd = secondSpaceIdx != -1 ? secondSpaceIdx : (spaceIdx != -1 ? spaceIdx : 0);

        if (timestampEnd > 0) {
            spannable.setSpan(new ForegroundColorSpan(0xFF888888), 0, timestampEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        int messageStart = timestampEnd < line.length() ? timestampEnd : 0;
        String message = line.substring(messageStart);

        int textColor = 0xFF444444;
        boolean isBold = false;

        String lower = message.toLowerCase(Locale.US);
        if (lower.contains("mainactivity") || lower.contains("created") || lower.contains("resumed")
                || lower.contains("paused") || lower.contains("destroyed") || lower.contains("permission")) {
            textColor = 0xFF888888;
        } else if (lower.contains("timer turned") || lower.contains("duration set") || lower.contains("fade-out")
                || lower.contains("expired") || lower.contains("wake-up goal") || lower.contains("alarm")
                || lower.contains("crash")) {
            textColor = 0xFF000000;
            isBold = true;
        }

        spannable.setSpan(new ForegroundColorSpan(textColor), messageStart, line.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (isBold) {
            spannable.setSpan(new StyleSpan(Typeface.BOLD), messageStart, line.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return spannable;
    }

    public static synchronized void clear(Context context) {
        events.clear();
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
