package com.bas080.autosleepdroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

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

    public interface Listener {
        void onEventLogged(String event);
    }

    public static synchronized void setListener(Listener l) {
        listener = l;
    }

    public static synchronized void log(Context context, String message) {
        ensureLoaded(context);

        String timestamp = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date());
        String line = timestamp + " - " + message;

        events.add(line);
        if (events.size() > MAX_LOGS) {
            events.remove(0);
        }

        if (context != null) {
            persistLogs(context.getApplicationContext());
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
