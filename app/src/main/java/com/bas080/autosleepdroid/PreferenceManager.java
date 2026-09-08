package com.bas080.autosleepdroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PreferenceManager implements SharedPreferences.OnSharedPreferenceChangeListener, com.bas080.autosleepdroid.PreferenceGetter {

    public static final String PREFERENCES_NAME = PreferenceKeys.PREFERENCES_NAME;

    public static final String KEY_ACTIVE = PreferenceKeys.KEY_ACTIVE;
    public static final String KEY_DURATION_MINUTES = PreferenceKeys.KEY_DURATION_MINUTES;
    public static final String KEY_SHOW_NOTIFICATION = PreferenceKeys.KEY_SHOW_NOTIFICATION;
    public static final String KEY_TIMER_ENDS_AT = PreferenceKeys.KEY_TIMER_ENDS_AT;
    public static final String KEY_TIMER_START_TIME_MS = PreferenceKeys.KEY_TIMER_START_TIME_MS;
    public static final String KEY_SLEEP_START_TIME_MS = PreferenceKeys.KEY_SLEEP_START_TIME_MS;
    public static final String KEY_AUTO_TIMER_ENABLED = PreferenceKeys.KEY_AUTO_TIMER_ENABLED;
    public static final String KEY_WAKE_UP_GOAL_ENABLED = PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED;
    public static final String KEY_WAKE_UP_GOAL_HOUR = PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR;
    public static final String KEY_WAKE_UP_GOAL_MINUTE = PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE;
    public static final String KEY_CURRENT_WAKE_HOUR = PreferenceKeys.KEY_CURRENT_WAKE_HOUR;
    public static final String KEY_CURRENT_WAKE_MINUTE = PreferenceKeys.KEY_CURRENT_WAKE_MINUTE;
    public static final String KEY_MIN_SLEEP_DURATION_MINUTES = PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES;
    public static final String KEY_NAP_DND_ENABLED = PreferenceKeys.KEY_NAP_DND_ENABLED;
    public static final String KEY_NAP_DURATION_MINUTES = PreferenceKeys.KEY_NAP_DURATION_MINUTES;
    public static final String KEY_NAP_ALARM_ENDS_AT = PreferenceKeys.KEY_NAP_ALARM_ENDS_AT;
    public static final String KEY_NAP_START_TIME_MS = PreferenceKeys.KEY_NAP_START_TIME_MS;
    public static final String KEY_NAP_ALARM_RINGING = PreferenceKeys.KEY_NAP_ALARM_RINGING;
    public static final String KEY_HEALTH_CONNECT_ENABLED = PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED;
    public static final String KEY_HC_MIN_DURATION_MINUTES = PreferenceKeys.KEY_HC_MIN_DURATION_MINUTES;
    public static final String KEY_WAKEUP_LAST_SCHEDULED_MS = PreferenceKeys.KEY_WAKEUP_LAST_SCHEDULED_MS;

    public interface PreferenceGetter extends com.bas080.autosleepdroid.PreferenceGetter {
    }

    @FunctionalInterface
    public interface OnPreferenceChangeListener {
        void onPreferenceChanged(String key);
    }

    @FunctionalInterface
    public interface ComputedValue<T> {
        T compute(PreferenceGetter getter);
    }

    @FunctionalInterface
    public interface PreferenceEffect {
        void run(PreferenceGetter getter);
    }

    @FunctionalInterface
    public interface EffectHandle {
        void dispose();
    }

    private static class TrackingPreferenceGetter implements PreferenceGetter {
        private final PreferenceManager preferenceManager;
        private final Map<String, Object> accessedValues = new ConcurrentHashMap<>();

        TrackingPreferenceGetter(PreferenceManager preferenceManager) {
            this.preferenceManager = preferenceManager;
        }

        Map<String, Object> getAccessedValues() {
            return accessedValues;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            boolean value = preferenceManager.getBoolean(key, defValue);
            accessedValues.put(key, value);
            return value;
        }

        @Override
        public int getInt(String key, int defValue) {
            int value = preferenceManager.getInt(key, defValue);
            accessedValues.put(key, value);
            return value;
        }

        @Override
        public long getLong(String key, long defValue) {
            long value = preferenceManager.getLong(key, defValue);
            accessedValues.put(key, value);
            return value;
        }

        @Override
        public String getString(String key, String defValue) {
            String value = preferenceManager.getString(key, defValue);
            if (value != null) {
                accessedValues.put(key, value);
            } else {
                accessedValues.put(key, NULL_SENTINEL);
            }
            return value;
        }

        @Override
        public boolean contains(String key) {
            boolean value = preferenceManager.contains(key);
            accessedValues.put("contains:" + key, value);
            return value;
        }
    }

    private static final Object NULL_SENTINEL = new Object();

    private static class CachedComputation {
        final Object value;
        final Map<String, Object> trackedValues;

        CachedComputation(Object value, Map<String, Object> trackedValues) {
            this.value = value;
            this.trackedValues = trackedValues;
        }

        boolean isStale(PreferenceManager preferenceManager) {
            if (trackedValues == null || trackedValues.isEmpty()) {
                return false;
            }
            for (Map.Entry<String, Object> entry : trackedValues.entrySet()) {
                String key = entry.getKey();
                Object trackedValue = entry.getValue();
                if (key.startsWith("contains:")) {
                    String actualKey = key.substring("contains:".length());
                    boolean currentContains = preferenceManager.contains(actualKey);
                    if (!trackedValue.equals(currentContains)) {
                        return true;
                    }
                } else {
                    if (trackedValue instanceof Boolean) {
                        if ((Boolean) trackedValue != preferenceManager.getBoolean(key, !(Boolean) trackedValue)) {
                            return true;
                        }
                    } else if (trackedValue instanceof Integer) {
                        if ((Integer) trackedValue != preferenceManager.getInt(key, (Integer) trackedValue + 1)) {
                            return true;
                        }
                    } else if (trackedValue instanceof Long) {
                        if ((Long) trackedValue != preferenceManager.getLong(key, (Long) trackedValue + 1L)) {
                            return true;
                        }
                    } else if (trackedValue == NULL_SENTINEL) {
                        if (preferenceManager.contains(key)) {
                            return true;
                        }
                    } else if (trackedValue instanceof String) {
                        String current = preferenceManager.getString(key, null);
                        if (!trackedValue.equals(current)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }

    private static class WatchEffectRegistration implements EffectHandle {
        final PreferenceManager preferenceManager;
        final PreferenceEffect effect;
        final boolean isMainThread;
        final Set<String> trackedKeys = ConcurrentHashMap.newKeySet();
        volatile boolean isDisposed = false;

        WatchEffectRegistration(PreferenceManager preferenceManager, PreferenceEffect effect, boolean isMainThread) {
            this.preferenceManager = preferenceManager;
            this.effect = effect;
            this.isMainThread = isMainThread;
        }

        void runEffect() {
            if (isDisposed) return;
            Runnable runnable = () -> {
                if (isDisposed) return;
                TrackingPreferenceGetter getter = new TrackingPreferenceGetter(preferenceManager);
                effect.run(getter);
                trackedKeys.clear();
                for (String key : getter.getAccessedValues().keySet()) {
                    if (key.startsWith("contains:")) {
                        trackedKeys.add(key.substring("contains:".length()));
                    } else {
                        trackedKeys.add(key);
                    }
                }
            };
            dispatch(runnable);
        }

        void dispatch(Runnable runnable) {
            if (isMainThread) {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    runnable.run();
                } else {
                    preferenceManager.mainHandler.post(runnable);
                }
            } else {
                preferenceManager.asyncExecutor.execute(runnable);
            }
        }

        @Override
        public void dispose() {
            isDisposed = true;
            preferenceManager.activeEffects.remove(this);
        }
    }

    private final SharedPreferences preferences;
    private final Map<String, Set<OnPreferenceChangeListener>> listenersMap = new ConcurrentHashMap<>();
    private final Map<Object, CachedComputation> computedCache = new ConcurrentHashMap<>();
    private final Set<WatchEffectRegistration> activeEffects = new CopyOnWriteArraySet<>();
    private final Map<Object, Set<EffectHandle>> taggedEffects = new ConcurrentHashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
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

    public EffectHandle watchEffect(PreferenceEffect effect) {
        if (effect == null) return () -> {};
        boolean isMainThread = Looper.myLooper() == Looper.getMainLooper();
        WatchEffectRegistration reg = new WatchEffectRegistration(this, effect, isMainThread);
        activeEffects.add(reg);
        reg.runEffect();
        return reg;
    }

    public EffectHandle watchEffect(Object tag, PreferenceEffect effect) {
        EffectHandle handle = watchEffect(effect);
        if (tag != null) {
            taggedEffects.computeIfAbsent(tag, k -> new CopyOnWriteArraySet<>()).add(handle);
        }
        return handle;
    }

    public EffectHandle watchEffects(PreferenceEffect... effects) {
        if (effects == null || effects.length == 0) return () -> {};
        java.util.List<EffectHandle> handles = new java.util.ArrayList<>();
        for (PreferenceEffect effect : effects) {
            handles.add(watchEffect(effect));
        }
        return () -> {
            for (EffectHandle handle : handles) {
                handle.dispose();
            }
            handles.clear();
        };
    }

    public void disposeEffects(Object tag) {
        if (tag == null) return;
        Set<EffectHandle> handles = taggedEffects.remove(tag);
        if (handles != null) {
            for (EffectHandle handle : handles) {
                handle.dispose();
            }
            handles.clear();
        }
    }

    public <T> T getComputed(ComputedValue<T> computer) {
        return getComputed(computer, computer);
    }

    @SuppressWarnings("unchecked")
    public <T> T getComputed(Object cacheKey, ComputedValue<T> computer) {
        if (cacheKey == null || computer == null) return null;
        CachedComputation cached = computedCache.get(cacheKey);
        if (cached != null && !cached.isStale(this)) {
            return (T) cached.value;
        }
        TrackingPreferenceGetter getter = new TrackingPreferenceGetter(this);
        T result = computer.compute(getter);
        computedCache.put(cacheKey, new CachedComputation(result, getter.getAccessedValues()));
        return result;
    }

    public void invalidateComputed(Object cacheKey) {
        if (cacheKey != null) {
            computedCache.remove(cacheKey);
        }
    }

    public void invalidateAllComputed() {
        computedCache.clear();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key == null) return;

        if (!computedCache.isEmpty()) {
            for (Map.Entry<Object, CachedComputation> entry : computedCache.entrySet()) {
                CachedComputation cached = entry.getValue();
                if (cached.trackedValues != null && (cached.trackedValues.containsKey(key) || cached.trackedValues.containsKey("contains:" + key))) {
                    computedCache.remove(entry.getKey());
                }
            }
        }

        if (!activeEffects.isEmpty()) {
            for (WatchEffectRegistration reg : activeEffects) {
                if (!reg.isDisposed && reg.trackedKeys.contains(key)) {
                    reg.runEffect();
                }
            }
        }

        Set<OnPreferenceChangeListener> listeners = listenersMap.get(key);
        if (listeners != null && !listeners.isEmpty()) {
            for (OnPreferenceChangeListener listener : listeners) {
                listener.onPreferenceChanged(key);
            }
        }
    }

    public void putBoolean(String key, boolean value) {
        preferences.edit().putBoolean(key, value).apply();
    }

    public void putInt(String key, int value) {
        preferences.edit().putInt(key, value).apply();
    }

    public void putLong(String key, long value) {
        preferences.edit().putLong(key, value).apply();
    }

    public void putString(String key, String value) {
        preferences.edit().putString(key, value).apply();
    }

    public void remove(String key) {
        preferences.edit().remove(key).apply();
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
        computedCache.clear();
        for (WatchEffectRegistration reg : activeEffects) {
            reg.dispose();
        }
        activeEffects.clear();
        asyncExecutor.shutdown();
    }
}
