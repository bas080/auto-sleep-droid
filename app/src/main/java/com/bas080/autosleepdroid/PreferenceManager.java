package com.bas080.autosleepdroid;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PreferenceManager implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String PREFERENCES_NAME = "sleep_timer";

    public static final String KEY_ACTIVE = "active";
    public static final String KEY_DURATION_MINUTES = "duration_minutes";
    public static final String KEY_SHOW_NOTIFICATION = "show_notification";
    public static final String KEY_TIMER_ENDS_AT = "timer_ends_at";
    public static final String KEY_TIMER_START_TIME_MS = "timer_start_time_ms";
    public static final String KEY_SLEEP_START_TIME_MS = "sleep_start_time_ms";
    public static final String KEY_AUTO_TIMER_ENABLED = "auto_timer_enabled";
    public static final String KEY_WAKE_UP_GOAL_ENABLED = "wake_up_goal_enabled";
    public static final String KEY_WAKE_UP_GOAL_HOUR = "wake_up_goal_hour";
    public static final String KEY_WAKE_UP_GOAL_MINUTE = "wake_up_goal_minute";
    public static final String KEY_CURRENT_WAKE_HOUR = "current_wake_hour";
    public static final String KEY_CURRENT_WAKE_MINUTE = "current_wake_minute";
    public static final String KEY_MIN_SLEEP_DURATION_MINUTES = "min_sleep_duration_minutes";
    public static final String KEY_NAP_DND_ENABLED = "nap_dnd_enabled";
    public static final String KEY_NAP_DURATION_MINUTES = "nap_duration_minutes";
    public static final String KEY_NAP_ALARM_ENDS_AT = "nap_alarm_ends_at";
    public static final String KEY_NAP_START_TIME_MS = "nap_start_time_ms";
    public static final String KEY_NAP_ALARM_RINGING = "is_nap_alarm_ringing";
    public static final String KEY_HEALTH_CONNECT_ENABLED = "health_connect_enabled";
    public static final String KEY_HC_MIN_DURATION_MINUTES = "hc_min_duration_minutes";
    public static final String KEY_WAKEUP_LAST_SCHEDULED_MS = "wakeup_last_scheduled_ms";

    public interface OnPreferenceChangeListener {
        void onPreferenceChanged(String key);
    }

    private final SharedPreferences preferences;
    private final Map<String, Set<OnPreferenceChangeListener>> listenersMap = new ConcurrentHashMap<>();
    private final ExecutorService asyncExecutor = Executors.newSingleThreadExecutor();

    public PreferenceManager(Context context, String preferenceName) {
        this.preferences = context.getApplicationContext().getSharedPreferences(preferenceName, Context.MODE_PRIVATE);
        this.preferences.registerOnSharedPreferenceChangeListener(this);
    }

    public PreferenceManager(SharedPreferences sharedPreferences) {
        this.preferences = sharedPreferences;
        this.preferences.registerOnSharedPreferenceChangeListener(this);
    }

    public SharedPreferences getSharedPreferences() {
        return preferences;
    }

    public void registerListener(String key, OnPreferenceChangeListener listener) {
        if (key == null || listener == null) return;
        listenersMap.computeIfAbsent(key, k -> new CopyOnWriteArraySet<>()).add(listener);
    }

    public void unregisterListener(OnPreferenceChangeListener listener) {
        if (listener == null) return;
        for (Set<OnPreferenceChangeListener> listeners : listenersMap.values()) {
            listeners.remove(listener);
        }
    }

    public void unregisterListener(String key, OnPreferenceChangeListener listener) {
        if (key == null || listener == null) return;
        Set<OnPreferenceChangeListener> listeners = listenersMap.get(key);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key == null) return;
        Set<OnPreferenceChangeListener> listeners = listenersMap.get(key);
        if (listeners != null && !listeners.isEmpty()) {
            for (OnPreferenceChangeListener listener : listeners) {
                listener.onPreferenceChanged(key);
            }
        }
    }

    public void executeAsync(Runnable task) {
        if (task != null) {
            asyncExecutor.execute(task);
        }
    }

    public void putBooleanAsync(String key, boolean value) {
        executeAsync(() -> preferences.edit().putBoolean(key, value).apply());
    }

    public void putIntAsync(String key, int value) {
        executeAsync(() -> preferences.edit().putInt(key, value).apply());
    }

    public void putLongAsync(String key, long value) {
        executeAsync(() -> preferences.edit().putLong(key, value).apply());
    }

    public void putStringAsync(String key, String value) {
        executeAsync(() -> preferences.edit().putString(key, value).apply());
    }

    public void removeAsync(String key) {
        executeAsync(() -> preferences.edit().remove(key).apply());
    }

    public boolean getBoolean(String key, boolean defValue) {
        return preferences.getBoolean(key, defValue);
    }

    public int getInt(String key, int defValue) {
        return preferences.getInt(key, defValue);
    }

    public long getLong(String key, long defValue) {
        return preferences.getLong(key, defValue);
    }

    public String getString(String key, String defValue) {
        return preferences.getString(key, defValue);
    }

    public boolean contains(String key) {
        return preferences.contains(key);
    }

    public void shutdown() {
        preferences.unregisterOnSharedPreferenceChangeListener(this);
        listenersMap.clear();
        asyncExecutor.shutdown();
    }
}
