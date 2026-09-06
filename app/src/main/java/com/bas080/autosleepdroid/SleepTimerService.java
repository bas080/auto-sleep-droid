package com.bas080.autosleepdroid;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.RemoteInput;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;

import java.util.Calendar;
import java.util.Date;

public class SleepTimerService extends Service implements SensorEventListener, SleepTimerStateMachine.Callback {
    public static final String ACTION_SET_DURATION = "com.bas080.autosleepdroid.SET_DURATION";
    public static final String ACTION_TURN_OFF = "com.bas080.autosleepdroid.TURN_OFF";
    public static final String ACTION_TURN_ON = "com.bas080.autosleepdroid.TURN_ON";
    public static final String ACTION_ALARM_EXPIRY = "com.bas080.autosleepdroid.ALARM_EXPIRY";
    public static final String ACTION_WAKEUP_ALARM_EXPIRY = "com.bas080.autosleepdroid.AUTO_SLEEP_ALARM_EXPIRY";
    public static final String ACTION_DISMISS_WAKEUP_ALARM = "com.bas080.autosleepdroid.DISMISS_WAKEUP_ALARM";
    public static final String ACTION_SNOOZE_WAKEUP_ALARM = "com.bas080.autosleepdroid.SNOOZE_WAKEUP_ALARM";
    public static final String ACTION_AWAKE = "com.bas080.autosleepdroid.AWAKE";
    public static final String ACTION_REDRAW_NOTIFICATION = "com.bas080.autosleepdroid.REDRAW_NOTIFICATION";
    public static final String ACTION_CLEAR_GOAL = "com.bas080.autosleepdroid.CLEAR_GOAL";
    public static final String ACTION_AUTO_TIMER_CHECK = "com.bas080.autosleepdroid.AUTO_TIMER_CHECK";
    public static final String ACTION_START_NAP = "com.bas080.autosleepdroid.START_NAP";
    public static final String ACTION_CANCEL_NAP = "com.bas080.autosleepdroid.CANCEL_NAP";
    public static final String ACTION_NAP_EXPIRY = "com.bas080.autosleepdroid.NAP_EXPIRY";
    public static final String EXTRA_DURATION = "com.bas080.autosleepdroid.DURATION";
    public static final String EXTRA_NAP_DURATION_MINUTES = "extra_nap_duration_minutes";
    public static final String ALARM_SEARCH_NAME = "Auto Sleep";
    public static final String KEY_WAKEUP_LAST_SCHEDULED_MS = "wakeup_last_scheduled_ms";
    public static final String KEY_NAP_DURATION_MINUTES = "nap_duration_minutes";
    public static final String KEY_NAP_ALARM_ENDS_AT = "nap_alarm_ends_at";
    public static final String KEY_NAP_ALARM_RINGING = "is_nap_alarm_ringing";

    private static final String CHANNEL_ID = "sleep_timer";
    private static final int NOTIFICATION_ID = 1001;
    private static final long SNOOZE_DURATION_MS = 9 * 60_000L;
    private static final String PREFERENCES = "sleep_timer";
    private static final String KEY_ENABLED = "active";
    private static final String KEY_DURATION_MINUTES = "duration_minutes";
    private static final String KEY_TIMER_ENDS_AT = "timer_ends_at";
    private static final String REMOTE_INPUT_KEY = "duration_minutes";
    private static final long PAUSE_RESET_DELAY_MS = 500L;
    private static final long SENSOR_THROTTLE_MS = 300L;
    private static final long ALARM_CRESCENDO_DURATION_MS = 3 * 60_000L;
    private static final long ALARM_CRESCENDO_INTERVAL_MS = 500L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AudioManager audioManager;
    private android.app.AlarmManager alarmManager;
    private android.content.SharedPreferences preferences;
    private Runnable expiryRunnable;
    private Runnable fadeRunnable;
    private Runnable restoreVolumeRunnable;
    private AudioManager.AudioPlaybackCallback audioPlaybackCallback;
    private android.content.BroadcastReceiver volumeReceiver;
    private android.content.BroadcastReceiver dndReceiver;

    private static final int ORIENTATION_UNKNOWN = 0;
    private static final int ORIENTATION_FACE_UP = 1;
    private static final int ORIENTATION_FACE_DOWN = 2;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private int lastOrientation = ORIENTATION_UNKNOWN;
    private long lastSensorEventTimeMs = 0L;
    private boolean sensorListenerRegistered = false;
    private android.os.HandlerThread sensorThread;
    private Handler sensorHandler;
    private android.os.Vibrator vibrator;
    private long lastScheduledWakeupAlarmTimeMs = 0L;
    private Ringtone currentAlarmRingtone;
    private Runnable alarmCrescendoRunnable;
    private long alarmCrescendoStartTimeMs = 0L;
    private boolean isWakeUpAlarmRinging = false;
    private boolean isWakeUpAlarmSnoozed = false;
    private boolean isForeground = false;
    private long napAlarmEndsAt = 0L;
    private long lastTimerEndsAt = 0L;
    private boolean isNapAlarmRinging = false;

    private SleepTimerStateMachine stateMachine;

    @Override
    public void onCreate() {
        super.onCreate();
        EventLogger.log(this, EventLogger.LEVEL_LOW, "SleepTimerService created");
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        alarmManager = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
        preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        vibrator = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
        createNotificationChannel();

        stateMachine = new SleepTimerStateMachine(this);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        initializeStateAndNotification();
    }

    private void initializeStateAndNotification() {
        boolean savedEnabled = preferences.getBoolean(KEY_ENABLED, true);
        int savedDuration = preferences.getInt(KEY_DURATION_MINUTES, SleepTimerStateMachine.DEFAULT_DURATION_MINUTES);
        long savedEndsAt = preferences.getLong(KEY_TIMER_ENDS_AT, 0L);
        napAlarmEndsAt = preferences.getLong(KEY_NAP_ALARM_ENDS_AT, 0L);
        isNapAlarmRinging = preferences.getBoolean(KEY_NAP_ALARM_RINGING, false);
        int currentVolume = audioManager != null ? audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) : 0;
        boolean musicActive = audioManager != null && audioManager.isMusicActive();

        EventLogger.log(this, "SleepTimerService state initialized (enabled: " + savedEnabled + ", duration: " + savedDuration + "m)");

        stateMachine.initialize(savedEnabled, savedDuration, savedEndsAt, currentVolume, musicActive, System.currentTimeMillis());

        registerDndReceiver();
        checkAndApplyDndAutoTimer();

        showOrHideNotification();

        boolean goalEnabled = preferences != null && preferences.getBoolean("wake_up_goal_enabled", false);
        if (goalEnabled) {
            checkAndScheduleSmartWakeUpAlarm(savedEndsAt);
        }
    }

    private void registerAudioPlaybackCallback() {
        if (audioManager != null && android.os.Build.VERSION.SDK_INT >= 26) {
            audioPlaybackCallback = new AudioManager.AudioPlaybackCallback() {
                @Override
                public void onPlaybackConfigChanged(java.util.List<android.media.AudioPlaybackConfiguration> configs) {
                    super.onPlaybackConfigChanged(configs);
                    boolean musicActive = audioManager.isMusicActive();
                    stateMachine.onPlaybackStateChanged(musicActive, System.currentTimeMillis());
                }
            };
            audioManager.registerAudioPlaybackCallback(audioPlaybackCallback, handler);
        }
    }

    private void unregisterAudioPlaybackCallback() {
        if (audioManager != null && audioPlaybackCallback != null && android.os.Build.VERSION.SDK_INT >= 26) {
            audioManager.unregisterAudioPlaybackCallback(audioPlaybackCallback);
            audioPlaybackCallback = null;
        }
    }

    private void registerDndReceiver() {
        if (dndReceiver == null && android.os.Build.VERSION.SDK_INT >= 23) {
            dndReceiver = new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED.equals(intent.getAction())) {
                        EventLogger.log(context, EventLogger.LEVEL_HIGH, "DND state changed");
                        checkAndApplyDndAutoTimer();
                    }
                }
            };
            android.content.IntentFilter filter = new android.content.IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED);
            registerReceiver(dndReceiver, filter);
        }
    }

    private void unregisterDndReceiver() {
        if (dndReceiver != null) {
            try {
                unregisterReceiver(dndReceiver);
            } catch (IllegalArgumentException ignored) {
            }
            dndReceiver = null;
        }
    }

    private void checkAndApplyDndAutoTimer() {
        if (preferences == null || stateMachine == null) return;
        boolean autoTimerEnabled = preferences.getBoolean("auto_timer_enabled", false);
        if (!autoTimerEnabled) return;

        if (android.os.Build.VERSION.SDK_INT >= 23) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                int filter = nm.getCurrentInterruptionFilter();
                boolean dndActive = (filter != NotificationManager.INTERRUPTION_FILTER_ALL);
                boolean musicActive = audioManager != null && audioManager.isMusicActive();
                long now = System.currentTimeMillis();

                if (dndActive && !stateMachine.isEnabled()) {
                    EventLogger.log(this, EventLogger.LEVEL_HIGH, "DND active: turning ON sleep timer");
                    stateMachine.handleTurnOn(musicActive, now, true);
                    updateNotification();
                } else if (!dndActive && stateMachine.isEnabled()) {
                    EventLogger.log(this, EventLogger.LEVEL_HIGH, "DND inactive: turning OFF sleep timer");
                    stateMachine.handleTurnOff(true);
                    updateNotification();
                }
            }
        }
    }

    private void registerVolumeObserver() {
        if (volumeReceiver == null) {
            volumeReceiver = new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if ("android.media.VOLUME_CHANGED_ACTION".equals(intent.getAction())) {
                        if (isWakeUpAlarmRinging || isWakeUpAlarmSnoozed) {
                            dismissWakeUpAlarmViaVolumeKey();
                        } else {
                            int streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1);
                            if (streamType == AudioManager.STREAM_MUSIC || streamType == -1) {
                                if (audioManager != null) {
                                    int currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                                    stateMachine.onVolumeChanged(currentVol, System.currentTimeMillis());
                                }
                            }
                        }
                    }
                }
            };
            android.content.IntentFilter filter = new android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION");
            registerReceiver(volumeReceiver, filter);
        }
    }

    private void unregisterVolumeObserver() {
        if (volumeReceiver != null) {
            try {
                unregisterReceiver(volumeReceiver);
            } catch (IllegalArgumentException ignored) {
            }
            volumeReceiver = null;
        }
    }

    private void registerSensorListener() {
        if (sensorManager != null && accelerometer != null && !sensorListenerRegistered) {
            if (sensorThread == null) {
                sensorThread = new android.os.HandlerThread("SensorThread");
                sensorThread.start();
                sensorHandler = new Handler(sensorThread.getLooper());
            }
            sensorListenerRegistered = true;
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL, sensorHandler);
        }
    }

    private void unregisterSensorListener() {
        if (sensorListenerRegistered && sensorManager != null) {
            sensorListenerRegistered = false;
            sensorManager.unregisterListener(this);
        }
        if (sensorThread != null) {
            sensorThread.quitSafely();
            sensorThread = null;
            sensorHandler = null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (ACTION_TURN_OFF.equals(intent.getAction())) {
                EventLogger.log(this, EventLogger.LEVEL_HIGH, "Timer turned off");
                stateMachine.handleTurnOff(true);
                android.widget.Toast.makeText(this, R.string.toast_timer_turned_off, android.widget.Toast.LENGTH_SHORT).show();
            } else if (ACTION_TURN_ON.equals(intent.getAction())) {
                EventLogger.log(this, EventLogger.LEVEL_HIGH, "Timer turned on");
                boolean musicActive = audioManager != null && audioManager.isMusicActive();
                stateMachine.handleTurnOn(musicActive, System.currentTimeMillis(), true);
                android.widget.Toast.makeText(this, R.string.toast_timer_turned_on, android.widget.Toast.LENGTH_SHORT).show();
            } else if (ACTION_SET_DURATION.equals(intent.getAction())) {
                handleDurationReply(intent);
            } else if (ACTION_ALARM_EXPIRY.equals(intent.getAction())) {
                EventLogger.log(this, EventLogger.LEVEL_HIGH, "AlarmManager trigger received");
                int currentVol = audioManager != null ? audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) : 0;
                stateMachine.handleAlarmExpiry(currentVol);
            } else if (ACTION_WAKEUP_ALARM_EXPIRY.equals(intent.getAction())) {
                EventLogger.log(this, EventLogger.LEVEL_HIGH, "Auto Sleep wake-up alarm triggered");
                if (!isNapAlarmRinging) {
                    updateNextWakeUpTimeOnDismissOrExpiry();
                }
                if (isWakeAlarmEnabled()) {
                    isWakeUpAlarmRinging = true;
                    isWakeUpAlarmSnoozed = false;
                    updateListenersRegistration();
                    playWakeUpAlarmSound();
                } else {
                    EventLogger.log(this, "Wake alarm disabled; skipping alarm tone");
                }
                updateNotification();
                checkAndScheduleSmartWakeUpAlarm(stateMachine != null ? stateMachine.getTimerEndsAt() : 0L);
            } else if (ACTION_DISMISS_WAKEUP_ALARM.equals(intent.getAction())) {
                EventLogger.log(this, EventLogger.LEVEL_HIGH, "Wake-Up Goal alarm dismissed");
                processSleepSessionOnAlarmDismissal();
                if (!isNapAlarmRinging) {
                    updateNextWakeUpTimeOnDismissOrExpiry();
                }
                stopWakeUpAlarmSound();
                cancelSnoozeAlarm();
                cancelNapAlarm(false);
                setNapAlarmRinging(false);
                isWakeUpAlarmRinging = false;
                isWakeUpAlarmSnoozed = false;
                updateListenersRegistration();
                checkAndScheduleSmartWakeUpAlarm(stateMachine != null ? stateMachine.getTimerEndsAt() : 0L);
                updateNotification();
                android.widget.Toast.makeText(this, R.string.toast_alarm_dismissed, android.widget.Toast.LENGTH_SHORT).show();
            } else if (ACTION_SNOOZE_WAKEUP_ALARM.equals(intent.getAction())) {
                EventLogger.log(this, EventLogger.LEVEL_HIGH, "Wake-Up Goal alarm snoozed for 9m");
                stopWakeUpAlarmSound();
                snoozeWakeUpAlarm();
                isWakeUpAlarmRinging = false;
                isWakeUpAlarmSnoozed = true;
                updateListenersRegistration();
                updateNotification();
                android.widget.Toast.makeText(this, R.string.toast_alarm_snoozed, android.widget.Toast.LENGTH_SHORT).show();
            } else if (ACTION_AWAKE.equals(intent.getAction())) {
                handleAwakeAction();
            } else if (ACTION_CLEAR_GOAL.equals(intent.getAction())) {
                if (preferences != null) {
                    preferences.edit()
                            .putBoolean("wake_up_goal_enabled", false)
                            .remove(KEY_WAKEUP_LAST_SCHEDULED_MS)
                            .apply();
                }
                cancelSnoozeAlarm();
                dismissAutoSleepAlarm();
                EventLogger.log(this, EventLogger.LEVEL_HIGH, "Smart Wake-Up Goal cleared");
                android.widget.Toast.makeText(this, R.string.toast_goal_stopped, android.widget.Toast.LENGTH_SHORT).show();
                updateNotification();
            } else if (ACTION_START_NAP.equals(intent.getAction())) {
                int duration = intent.getIntExtra(EXTRA_NAP_DURATION_MINUTES, preferences.getInt(KEY_NAP_DURATION_MINUTES, 20));
                startNapAlarm(duration);
            } else if (ACTION_CANCEL_NAP.equals(intent.getAction())) {
                cancelNapAlarm(true);
            } else if (ACTION_NAP_EXPIRY.equals(intent.getAction())) {
                EventLogger.log(this, EventLogger.LEVEL_HIGH, "Nap alarm triggered");
                cancelNapAlarm(false);
                setNapAlarmRinging(true);
                isWakeUpAlarmRinging = true;
                isWakeUpAlarmSnoozed = false;
                updateListenersRegistration();
                playWakeUpAlarmSound();
                updateNotification();
            } else if (ACTION_REDRAW_NOTIFICATION.equals(intent.getAction())) {
                reloadSettingsAndUpdate();
            }
        }
        return START_STICKY;
    }

    public static String formatDurationString(int totalMinutes) {
        return DurationUtils.formatDurationString(totalMinutes);
    }

    public static int parseDurationMinutes(String input) {
        return DurationUtils.parseDurationMinutes(input);
    }

    private void handleDurationReply(Intent intent) {
        CharSequence reply = RemoteInput.getResultsFromIntent(intent) == null
                ? null
                : RemoteInput.getResultsFromIntent(intent).getCharSequence(REMOTE_INPUT_KEY);

        int duration = parseDurationMinutes(reply != null ? reply.toString() : null);

        if (duration > 0) {
            boolean musicActive = audioManager != null && audioManager.isMusicActive();
            stateMachine.handleDurationReply(duration, musicActive, System.currentTimeMillis(), true);
            EventLogger.log(this, EventLogger.LEVEL_HIGH, "Duration set to " + stateMachine.getConfiguredDurationMinutes() + "m (input: '" + reply + "')");
            String formattedStr = formatDurationString(stateMachine.getConfiguredDurationMinutes());
            android.widget.Toast.makeText(this, getString(R.string.toast_duration_set, formattedStr), android.widget.Toast.LENGTH_SHORT).show();
        } else {
            EventLogger.log(this, EventLogger.LEVEL_HIGH, "Invalid duration input: '" + reply + "'");
            android.widget.Toast.makeText(this, R.string.toast_duration_invalid, android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void startFadeRunnable() {
        EventLogger.log(this, EventLogger.LEVEL_HIGH, "Fade-out started");
        fadeRunnable = this::runFadeStep;
        handler.post(fadeRunnable);
    }

    private void runFadeStep() {
        if (audioManager == null) {
            stateMachine.finishExpiry();
            return;
        }

        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

        boolean continues = stateMachine.runFadeStep(currentVolume, false);
        if (continues) {
            handler.postDelayed(fadeRunnable, SleepTimerStateMachine.FADE_STEP_INTERVAL_MS);
        }
    }

    private void scheduleExpiry() {
        if (expiryRunnable != null) {
            handler.removeCallbacks(expiryRunnable);
        }
        long delay = Math.max(0L, stateMachine.getTimerEndsAt() - System.currentTimeMillis());
        expiryRunnable = () -> {
            int vol = audioManager != null ? audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) : 0;
            stateMachine.beginFadeOut(vol);
        };
        handler.postDelayed(expiryRunnable, delay);
    }

    private void cancelTimerCallbacks() {
        if (expiryRunnable != null) {
            handler.removeCallbacks(expiryRunnable);
        }
        if (fadeRunnable != null) {
            handler.removeCallbacks(fadeRunnable);
        }
        if (restoreVolumeRunnable != null) {
            handler.removeCallbacks(restoreVolumeRunnable);
        }
    }

    private void updateListenersRegistration() {
        boolean needSensor = stateMachine.isActive() || isWakeUpAlarmRinging;
        if (needSensor) {
            registerSensorListener();
        } else {
            unregisterSensorListener();
        }

        boolean needVolume = stateMachine.isActive() || isWakeUpAlarmRinging || isWakeUpAlarmSnoozed;
        if (needVolume) {
            registerVolumeObserver();
        } else {
            unregisterVolumeObserver();
        }
    }

    @Override
    public void onStateChanged(SleepTimerStateMachine.State newState) {
        cancelTimerCallbacks();
        if (newState == SleepTimerStateMachine.State.OFF) {
            unregisterAudioPlaybackCallback();
            stopWakeUpAlarmSound();
            cancelSnoozeAlarm();
            isWakeUpAlarmRinging = false;
            isWakeUpAlarmSnoozed = false;
            onCancelAlarm();
            updateListenersRegistration();
            showOrHideNotification();
        } else if (newState == SleepTimerStateMachine.State.WAITING) {
            registerAudioPlaybackCallback();
            onCancelAlarm();
            updateListenersRegistration();
            showOrHideNotification();
        } else if (newState == SleepTimerStateMachine.State.FADING) {
            unregisterAudioPlaybackCallback();
            updateListenersRegistration();
            showOrHideNotification();
            startFadeRunnable();
        } else if (newState == SleepTimerStateMachine.State.ACTIVE) {
            unregisterAudioPlaybackCallback();
            updateListenersRegistration();
            checkAndScheduleSmartWakeUpAlarm(stateMachine.getTimerEndsAt());
            showOrHideNotification();
            scheduleExpiry();
        }
    }

    @Override
    public void onSetStreamVolume(int volume) {
        if (audioManager != null) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
        }
    }

    @Override
    public void onScheduleAlarm(long triggerAtMillis) {
        if (alarmManager == null) {
            return;
        }
        Intent intent = new Intent(this, SleepTimerService.class).setAction(ACTION_ALARM_EXPIRY);
        PendingIntent pendingIntent = PendingIntent.getService(this, 100, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        try {
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                } else {
                    EventLogger.log(this, "Exact alarm permission missing, using fallback alarm");
                    alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                }
            } else if (android.os.Build.VERSION.SDK_INT >= 23) {
                alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }
        } catch (SecurityException e) {
            EventLogger.log(this, "SecurityException scheduling alarm, using fallback alarm");
        }
    }

    @Override
    public void onCancelAlarm() {
        if (alarmManager == null) {
            return;
        }
        Intent intent = new Intent(this, SleepTimerService.class).setAction(ACTION_ALARM_EXPIRY);
        PendingIntent pendingIntent = PendingIntent.getService(this, 100, intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
    }

    @Override
    public void onPauseMedia() {
        EventLogger.log(this, EventLogger.LEVEL_HIGH, "Timer expired: pausing media");
        long now = System.currentTimeMillis();
        if (preferences != null) {
            preferences.edit().putLong("sleep_start_time_ms", now).apply();
        }
        pauseMediaViaAudioFocus();

        restoreVolumeRunnable = () -> stateMachine.restoreVolumeAfterPause();
        handler.postDelayed(restoreVolumeRunnable, PAUSE_RESET_DELAY_MS);
    }

    private void processSleepSessionOnAlarmDismissal() {
        if (preferences == null) return;
        boolean healthConnectEnabled = preferences.getBoolean("health_connect_enabled", false);
        if (!healthConnectEnabled) return;

        long sleepStartTime = preferences.getLong("sleep_start_time_ms", 0L);
        long napStartTime = preferences.getLong("nap_start_time_ms", 0L);
        long wakeTime = System.currentTimeMillis();

        if (napStartTime > 0L && wakeTime > napStartTime) {
            HealthConnectManager.writeSleepSession(this, napStartTime, wakeTime, null);
            preferences.edit().remove("nap_start_time_ms").apply();
        } else if (sleepStartTime > 0L && wakeTime > sleepStartTime) {
            HealthConnectManager.writeSleepSession(this, sleepStartTime, wakeTime, null);
            preferences.edit().remove("sleep_start_time_ms").apply();
        }
    }

    private void handleAwakeAction() {
        EventLogger.log(this, EventLogger.LEVEL_HIGH, "User marked as awake explicitly");
        if (stateMachine != null && stateMachine.isActive()) {
            stateMachine.handleTurnOff(false);
        }

        boolean wasNap = isNapAlarmRinging || isNapActive();

        processSleepSessionOnAwake();

        stopWakeUpAlarmSound();
        cancelSnoozeAlarm();
        cancelNapAlarm(false);
        setNapAlarmRinging(false);
        isWakeUpAlarmRinging = false;
        isWakeUpAlarmSnoozed = false;

        if (!wasNap) {
            dismissAutoSleepAlarm();
            updateNextWakeUpTimeOnDismissOrExpiry();
            checkAndScheduleSmartWakeUpAlarm(stateMachine != null ? stateMachine.getTimerEndsAt() : 0L);
        }

        updateListenersRegistration();
        updateNotification();
        android.widget.Toast.makeText(this, R.string.toast_awake_registered, android.widget.Toast.LENGTH_SHORT).show();
    }

    private void processSleepSessionOnAwake() {
        if (preferences == null) return;
        boolean healthConnectEnabled = preferences.getBoolean("health_connect_enabled", false);

        long sleepStartTime = preferences.getLong("sleep_start_time_ms", 0L);
        long napStartTime = preferences.getLong("nap_start_time_ms", 0L);
        long wakeTime = System.currentTimeMillis();

        if (napStartTime > 0L && wakeTime > napStartTime) {
            if (healthConnectEnabled) {
                HealthConnectManager.writeSleepSession(this, napStartTime, wakeTime, null);
            }
            preferences.edit().remove("nap_start_time_ms").apply();
        } else {
            if (sleepStartTime <= 0L) {
                int minSleepMin = preferences.getInt("min_sleep_duration_minutes", 450);
                sleepStartTime = wakeTime - minSleepMin * 60_000L;
            }
            if (wakeTime > sleepStartTime) {
                if (healthConnectEnabled) {
                    HealthConnectManager.writeSleepSession(this, sleepStartTime, wakeTime, null);
                }
                preferences.edit().remove("sleep_start_time_ms").apply();
            }
        }
    }

    private boolean isWakeAlarmEnabled() {
        if (preferences == null) return false;
        return preferences.getBoolean("wake_alarm_enabled", preferences.getBoolean("wake_up_goal_enabled", false));
    }

    private boolean hasActiveSleepSession() {
        if (preferences == null) return false;
        long sleepStartTime = preferences.getLong("sleep_start_time_ms", 0L);
        long now = System.currentTimeMillis();
        boolean activeSleepSession = sleepStartTime > 0L && (now - sleepStartTime < 14 * 3600_000L);
        boolean timerActive = stateMachine != null && stateMachine.isActive();
        return activeSleepSession || timerActive;
    }

    public static Calendar calculateScheduledAlarm(Context context, long now, long timerEndsAt) {
        if (context == null) {
            return null;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        boolean wakeAlarmEnabled = prefs.getBoolean("wake_alarm_enabled", prefs.getBoolean("wake_up_goal_enabled", false));
        boolean autoTimerEnabled = prefs.getBoolean("auto_timer_enabled", false);
        if (!wakeAlarmEnabled && !autoTimerEnabled) {
            return null;
        }

        int goalHour = prefs.getInt("wake_up_goal_hour", 6);
        int goalMin = prefs.getInt("wake_up_goal_minute", 30);
        int currentHour = prefs.getInt("current_wake_hour", goalHour);
        int currentMin = prefs.getInt("current_wake_minute", goalMin);
        int minSleepMin = prefs.getInt("min_sleep_duration_minutes", 450);

        Calendar calCurrent = Calendar.getInstance();
        calCurrent.setTimeInMillis(now);
        calCurrent.set(Calendar.HOUR_OF_DAY, currentHour);
        calCurrent.set(Calendar.MINUTE, currentMin);
        calCurrent.set(Calendar.SECOND, 0);
        calCurrent.set(Calendar.MILLISECOND, 0);

        if (calCurrent.getTimeInMillis() <= now) {
            calCurrent.add(Calendar.DAY_OF_YEAR, 1);
        }

        long scheduledAlarmMillis = calCurrent.getTimeInMillis();

        long sleepStartTime = prefs.getLong("sleep_start_time_ms", 0L);
        long minWakeTimeMillis = 0L;
        if (sleepStartTime > 0L && (now - sleepStartTime < 14 * 3600_000L)) {
            minWakeTimeMillis = sleepStartTime + minSleepMin * 60_000L;
        } else if (timerEndsAt > 0L) {
            minWakeTimeMillis = timerEndsAt + minSleepMin * 60_000L;
        }

        if (minWakeTimeMillis > scheduledAlarmMillis) {
            scheduledAlarmMillis = minWakeTimeMillis;
        }

        Calendar calAlarm = Calendar.getInstance();
        calAlarm.setTimeInMillis(scheduledAlarmMillis);
        return calAlarm;
    }

    private void updateNextWakeUpTimeOnDismissOrExpiry() {
        if (preferences == null) return;
        int goalHour = preferences.getInt("wake_up_goal_hour", 6);
        int goalMin = preferences.getInt("wake_up_goal_minute", 30);
        int currentHour = preferences.getInt("current_wake_hour", goalHour);
        int currentMin = preferences.getInt("current_wake_minute", goalMin);

        int currentTotalMins = currentHour * 60 + currentMin;
        int goalTotalMins = goalHour * 60 + goalMin;

        int nextTotalMins = Math.max(goalTotalMins, currentTotalMins - 15);
        int nextHour = (nextTotalMins / 60) % 24;
        int nextMin = nextTotalMins % 60;

        preferences.edit()
                .putInt("current_wake_hour", nextHour)
                .putInt("current_wake_minute", nextMin)
                .remove(KEY_WAKEUP_LAST_SCHEDULED_MS)
                .apply();
    }


    private void checkAndScheduleSmartWakeUpAlarm(long timerEndsAt) {
        if (!isWakeAlarmEnabled()) {
            dismissAutoSleepAlarm();
            return;
        }

        Calendar calAlarm = calculateScheduledAlarm(this, System.currentTimeMillis(), timerEndsAt);
        if (calAlarm == null) {
            return;
        }

        long targetAlarmTimeMs = calAlarm.getTimeInMillis();
        long lastScheduled = preferences != null ? preferences.getLong(KEY_WAKEUP_LAST_SCHEDULED_MS, 0L) : 0L;

        if (targetAlarmTimeMs == lastScheduled) {
            return;
        }

        dismissAutoSleepAlarm();

        lastScheduledWakeupAlarmTimeMs = targetAlarmTimeMs;
        if (preferences != null) {
            preferences.edit().putLong(KEY_WAKEUP_LAST_SCHEDULED_MS, targetAlarmTimeMs).apply();
        }

        if (alarmManager == null) {
            return;
        }

        if (alarmManager != null) {
            Intent intent = new Intent(this, SleepTimerService.class).setAction(ACTION_WAKEUP_ALARM_EXPIRY);
            PendingIntent pendingIntent = PendingIntent.getService(this, 101, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Intent showIntent = new Intent(this, MainActivity.class);
            PendingIntent showPendingIntent = PendingIntent.getActivity(this, 102, showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            AlarmManager.AlarmClockInfo clockInfo =
                    new AlarmManager.AlarmClockInfo(targetAlarmTimeMs, showPendingIntent);

            try {
                alarmManager.setAlarmClock(clockInfo, pendingIntent);
                java.text.DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);
                String formattedTime = timeFormat.format(new Date(targetAlarmTimeMs));
                EventLogger.log(this, "Wake-Up Goal Alarm '" + ALARM_SEARCH_NAME + "' scheduled for " + formattedTime);
            } catch (Exception e) {
                EventLogger.log(this, "Failed to schedule wake-up alarm: " + e.getMessage());
            }
        }
    }

    private void dismissAutoSleepAlarm() {
        if (alarmManager != null) {
            Intent alarmTriggerIntent = new Intent(this, SleepTimerService.class).setAction(ACTION_WAKEUP_ALARM_EXPIRY);
            PendingIntent operationIntent = PendingIntent.getService(this, 101, alarmTriggerIntent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (operationIntent != null) {
                alarmManager.cancel(operationIntent);
                operationIntent.cancel();
            }
        }

        if (preferences != null) {
            preferences.edit().remove(KEY_WAKEUP_LAST_SCHEDULED_MS).apply();
        }
    }

    private void cancelSnoozeAlarm() {
        if (alarmManager != null) {
            Intent snoozeTriggerIntent = new Intent(this, SleepTimerService.class).setAction(ACTION_WAKEUP_ALARM_EXPIRY);
            PendingIntent snoozeOperation = PendingIntent.getService(this, 106, snoozeTriggerIntent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (snoozeOperation != null) {
                alarmManager.cancel(snoozeOperation);
                snoozeOperation.cancel();
            }
        }
    }

    @Override
    public void onTriggerVibration() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                vibrator.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK));
            } else if (android.os.Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(70L, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(70L);
            }
        }
    }

    @Override
    public void onPersistState(boolean enabled, int durationMinutes, long timerEndsAt) {
        if (preferences == null) {
            return;
        }
        android.content.SharedPreferences.Editor editor = preferences.edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putInt(KEY_DURATION_MINUTES, durationMinutes);
        if (timerEndsAt > 0L) {
            editor.putLong(KEY_TIMER_ENDS_AT, timerEndsAt);
            long sleepStartTime = preferences.getLong("sleep_start_time_ms", 0L);
            long now = System.currentTimeMillis();
            if (sleepStartTime <= 0L || (now - sleepStartTime >= 14 * 3600_000L)) {
                editor.putLong("sleep_start_time_ms", now);
            }
        } else {
            editor.remove(KEY_TIMER_ENDS_AT);
        }
        editor.apply();
    }

    @Override
    public void onUpdateNotification() {
        updateNotification();
    }

    @Override
    public void onTimerRescheduled() {
        long newTimerEndsAt = stateMachine.getTimerEndsAt();
        if (lastTimerEndsAt > 0L && newTimerEndsAt > lastTimerEndsAt && isNapActive()) {
            long deltaMs = newTimerEndsAt - lastTimerEndsAt;
            napAlarmEndsAt += deltaMs;
            if (preferences != null) {
                preferences.edit().putLong(KEY_NAP_ALARM_ENDS_AT, napAlarmEndsAt).apply();
            }
            scheduleNapAlarm(napAlarmEndsAt);
            EventLogger.log(this, EventLogger.LEVEL_HIGH, "Pushed nap alarm forward by " + (deltaMs / 60_000L) + "m");
        }
        lastTimerEndsAt = newTimerEndsAt;
        scheduleExpiry();

        if (isWakeAlarmEnabled() && preferences != null) {
            int minSleepMin = preferences.getInt("min_sleep_duration_minutes", 450);
            long now = System.currentTimeMillis();
            long sleepStartTime = preferences.getLong("sleep_start_time_ms", 0L);
            long baseTime;
            if (sleepStartTime > 0L && (now - sleepStartTime < 14 * 3600_000L)) {
                baseTime = sleepStartTime;
            } else {
                baseTime = newTimerEndsAt > 0L ? newTimerEndsAt : now;
            }
            long requiredWakeTime = baseTime + minSleepMin * 60_000L;

            int goalHour = preferences.getInt("wake_up_goal_hour", 6);
            int goalMin = preferences.getInt("wake_up_goal_minute", 30);
            int currentHour = preferences.getInt("current_wake_hour", goalHour);
            int currentMin = preferences.getInt("current_wake_minute", goalMin);

            Calendar calCurrent = Calendar.getInstance();
            calCurrent.setTimeInMillis(now);
            calCurrent.set(Calendar.HOUR_OF_DAY, currentHour);
            calCurrent.set(Calendar.MINUTE, currentMin);
            calCurrent.set(Calendar.SECOND, 0);
            calCurrent.set(Calendar.MILLISECOND, 0);
            if (calCurrent.getTimeInMillis() <= now) {
                calCurrent.add(Calendar.DAY_OF_YEAR, 1);
            }

            if (requiredWakeTime > calCurrent.getTimeInMillis()) {
                Calendar calRequired = Calendar.getInstance();
                calRequired.setTimeInMillis(requiredWakeTime);
                int pushedHour = calRequired.get(Calendar.HOUR_OF_DAY);
                int pushedMin = calRequired.get(Calendar.MINUTE);
                preferences.edit()
                        .putInt("current_wake_hour", pushedHour)
                        .putInt("current_wake_minute", pushedMin)
                        .remove(KEY_WAKEUP_LAST_SCHEDULED_MS)
                        .apply();
                EventLogger.log(this, EventLogger.LEVEL_HIGH, "Pushed wake alarm forward to " + formatTime(pushedHour, pushedMin) + " due to min sleep safeguard");
            }
        }

        checkAndScheduleSmartWakeUpAlarm(stateMachine.getTimerEndsAt());
    }

    private boolean isNapActive() {
        return napAlarmEndsAt > System.currentTimeMillis();
    }

    private void startNapAlarm(int durationMinutes) {
        long now = System.currentTimeMillis();
        napAlarmEndsAt = now + durationMinutes * 60_000L;
        if (preferences != null) {
            preferences.edit()
                    .putInt(KEY_NAP_DURATION_MINUTES, durationMinutes)
                    .putLong(KEY_NAP_ALARM_ENDS_AT, napAlarmEndsAt)
                    .putLong("nap_start_time_ms", now)
                    .apply();
        }
        scheduleNapAlarm(napAlarmEndsAt);
        EventLogger.log(this, EventLogger.LEVEL_HIGH, "Nap alarm started for " + formatDurationString(durationMinutes));
        android.widget.Toast.makeText(this, getString(R.string.toast_nap_started, formatDurationString(durationMinutes)), android.widget.Toast.LENGTH_SHORT).show();
        updateNotification();
    }

    private void scheduleNapAlarm(long triggerAtMs) {
        if (alarmManager == null) return;
        Intent intent = new Intent(this, SleepTimerService.class).setAction(ACTION_NAP_EXPIRY);
        PendingIntent pendingIntent = PendingIntent.getService(this, 107, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent showIntent = new Intent(this, MainActivity.class);
        PendingIntent showPendingIntent = PendingIntent.getActivity(this, 102, showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager.AlarmClockInfo clockInfo = new AlarmManager.AlarmClockInfo(triggerAtMs, showPendingIntent);
        try {
            alarmManager.setAlarmClock(clockInfo, pendingIntent);
        } catch (Exception e) {
            EventLogger.log(this, "Failed to schedule nap alarm: " + e.getMessage());
        }
    }

    private void setNapAlarmRinging(boolean ringing) {
        this.isNapAlarmRinging = ringing;
        if (preferences != null) {
            if (ringing) {
                preferences.edit().putBoolean(KEY_NAP_ALARM_RINGING, true).apply();
            } else {
                preferences.edit().remove(KEY_NAP_ALARM_RINGING).apply();
            }
        }
    }

    private void cancelNapAlarm(boolean showToast) {
        if (alarmManager != null) {
            Intent intent = new Intent(this, SleepTimerService.class).setAction(ACTION_NAP_EXPIRY);
            PendingIntent pendingIntent = PendingIntent.getService(this, 107, intent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent);
                pendingIntent.cancel();
            }
        }
        napAlarmEndsAt = 0L;
        if (preferences != null) {
            preferences.edit()
                    .remove(KEY_NAP_ALARM_ENDS_AT)
                    .apply();
            if (showToast) {
                preferences.edit().remove("nap_start_time_ms").apply();
            }
        }
        if (showToast) {
            setNapAlarmRinging(false);
            EventLogger.log(this, EventLogger.LEVEL_HIGH, "Nap alarm cancelled");
            android.widget.Toast.makeText(this, R.string.toast_nap_cancelled, android.widget.Toast.LENGTH_SHORT).show();
            updateNotification();
        }
    }

    private void ensureAudibleAlarmStreamVolume() {
        if (audioManager == null) {
            return;
        }
        int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
        if (maxVol <= 0) {
            return;
        }
        int currentVol = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
        int minAudibleVol = Math.max(1, (int) Math.round(maxVol * 0.3));
        if (currentVol < minAudibleVol) {
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, minAudibleVol, 0);
                EventLogger.log(this, "Adjusted STREAM_ALARM volume from " + currentVol + " to " + minAudibleVol + " for wake-up alarm");
            } catch (Exception e) {
                EventLogger.log(this, "Failed to adjust STREAM_ALARM volume: " + e.getMessage());
            }
        }
    }

    private void playWakeUpAlarmSound() {
        stopWakeUpAlarmSound();
        try {
            ensureAudibleAlarmStreamVolume();
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            currentAlarmRingtone = getRingtone(getApplicationContext(), alarmUri);
            if (currentAlarmRingtone != null) {
                AlarmAudioUtils.configureAlarmAudioAttributes(currentAlarmRingtone);
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    currentAlarmRingtone.setVolume(0.0f);
                }
                currentAlarmRingtone.play();
                EventLogger.log(this, "Wake-Up Goal alarm tone started playing");
                startWakeUpAlarmCrescendo();
            }
        } catch (Exception e) {
            EventLogger.log(this, "Failed to play wake-up alarm sound: " + e.getMessage());
        }
    }

    private void startWakeUpAlarmCrescendo() {
        if (alarmCrescendoRunnable != null) {
            handler.removeCallbacks(alarmCrescendoRunnable);
        }
        alarmCrescendoStartTimeMs = System.currentTimeMillis();
        alarmCrescendoRunnable = this::runWakeUpAlarmCrescendoStep;
        handler.post(alarmCrescendoRunnable);
    }

    private void runWakeUpAlarmCrescendoStep() {
        if (currentAlarmRingtone == null) {
            return;
        }
        long elapsedTimeMs = System.currentTimeMillis() - alarmCrescendoStartTimeMs;
        float linearProgress = Math.min(1.0f, (float) elapsedTimeMs / ALARM_CRESCENDO_DURATION_MS);
        // Exponential gain curve (linearProgress^2) matching human psychoacoustic loudness perception
        // and sleep stage arousal transitions for startle-free wake-up experience
        float gain = linearProgress * linearProgress;

        if (android.os.Build.VERSION.SDK_INT >= 28) {
            try {
                currentAlarmRingtone.setVolume(gain);
            } catch (Exception ignored) {
            }
        }

        if (elapsedTimeMs < ALARM_CRESCENDO_DURATION_MS && isWakeUpAlarmRinging) {
            handler.postDelayed(alarmCrescendoRunnable, ALARM_CRESCENDO_INTERVAL_MS);
        } else {
            alarmCrescendoRunnable = null;
        }
    }

    Ringtone getRingtone(Context context, Uri uri) {
        Ringtone ringtone = RingtoneManager.getRingtone(context, uri);
        if (ringtone == null) {
            try {
                java.lang.reflect.Constructor<Ringtone> constructor =
                        Ringtone.class.getDeclaredConstructor(Context.class, boolean.class);
                constructor.setAccessible(true);
                ringtone = constructor.newInstance(context, false);
            } catch (Exception ignored) {
            }
        }
        return ringtone;
    }

    private void stopWakeUpAlarmSound() {
        if (alarmCrescendoRunnable != null) {
            handler.removeCallbacks(alarmCrescendoRunnable);
            alarmCrescendoRunnable = null;
        }
        if (currentAlarmRingtone != null) {
            try {
                if (currentAlarmRingtone.isPlaying()) {
                    currentAlarmRingtone.stop();
                }
            } catch (Exception ignored) {
            }
            currentAlarmRingtone = null;
        }
    }


    private void snoozeWakeUpAlarm() {
        if (alarmManager == null) {
            return;
        }
        long snoozeTimeMs = System.currentTimeMillis() + SNOOZE_DURATION_MS;

        Intent intent = new Intent(this, SleepTimerService.class).setAction(ACTION_WAKEUP_ALARM_EXPIRY);
        PendingIntent pendingIntent = PendingIntent.getService(this, 106, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent showIntent = new Intent(this, MainActivity.class);
        PendingIntent showPendingIntent = PendingIntent.getActivity(this, 102, showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager.AlarmClockInfo clockInfo = new AlarmManager.AlarmClockInfo(snoozeTimeMs, showPendingIntent);

        try {
            alarmManager.setAlarmClock(clockInfo, pendingIntent);
            java.text.DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);
            String formattedTime = timeFormat.format(new Date(snoozeTimeMs));
            EventLogger.log(this, "Wake-Up Goal alarm snoozed until " + formattedTime);
        } catch (Exception e) {
            EventLogger.log(this, "Failed to schedule snooze alarm: " + e.getMessage());
        }
    }

    private void snoozeWakeUpAlarmViaFlip() {
        EventLogger.log(this, "Wake-Up Goal alarm snoozed via flip gesture");
        stopWakeUpAlarmSound();
        snoozeWakeUpAlarm();
        isWakeUpAlarmRinging = false;
        isWakeUpAlarmSnoozed = true;
        onTriggerVibration();
        updateListenersRegistration();
        updateNotification();
    }

    private void dismissWakeUpAlarmViaVolumeKey() {
        EventLogger.log(this, EventLogger.LEVEL_HIGH, "Wake-Up Goal alarm dismissed via volume button");
        processSleepSessionOnAlarmDismissal();
        if (!isNapAlarmRinging) {
            updateNextWakeUpTimeOnDismissOrExpiry();
        }
        stopWakeUpAlarmSound();
        cancelSnoozeAlarm();
        cancelNapAlarm(false);
        setNapAlarmRinging(false);
        isWakeUpAlarmRinging = false;
        isWakeUpAlarmSnoozed = false;
        onTriggerVibration();
        updateListenersRegistration();
        updateNotification();
        android.widget.Toast.makeText(this, R.string.toast_alarm_dismissed, android.widget.Toast.LENGTH_SHORT).show();
    }

    private void pauseMediaViaAudioFocus() {
        if (audioManager == null) {
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            android.media.AudioFocusRequest focusRequest = new android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(new android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .build();
            audioManager.requestAudioFocus(focusRequest);
        } else {
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
    }

    private Notification buildNotification() {
        String title;
        String contentText;
        String formattedDurationStr = formatDurationString(stateMachine.getConfiguredDurationMinutes());

        long now = System.currentTimeMillis();
        Calendar scheduledAlarm = calculateScheduledAlarm(this, now, stateMachine.getTimerEndsAt());
        boolean showWakeAlarm = isWakeAlarmEnabled() && scheduledAlarm != null &&
                (scheduledAlarm.getTimeInMillis() - now) >= (long) (stateMachine.getConfiguredDurationMinutes() * 1.5 * 60_000L);

        if (isWakeUpAlarmRinging) {
            title = getString(R.string.wakeup_alarm_title);
            contentText = getString(R.string.wakeup_alarm_text);
        } else if (isWakeUpAlarmSnoozed) {
            title = getString(R.string.wakeup_alarm_title);
            contentText = getString(R.string.wakeup_alarm_snoozed_text);
        } else if (!stateMachine.isEnabled()) {
            title = getString(R.string.timer_off);
            if (showWakeAlarm) {
                String formattedAlarmTime = formatTime(scheduledAlarm.get(Calendar.HOUR_OF_DAY), scheduledAlarm.get(Calendar.MINUTE));
                contentText = getString(R.string.timer_off_expanded_alarm, formattedDurationStr, formattedAlarmTime);
            } else {
                contentText = getString(R.string.timer_off_collapsed, formattedDurationStr);
            }
        } else if (stateMachine.isFading()) {
            title = getString(R.string.fading_title);
            contentText = getString(R.string.fading_collapsed);
        } else if (stateMachine.isActive()) {
            String targetTimeStr = formatTargetTime();
            title = getString(R.string.active_title);

            if (showWakeAlarm) {
                String formattedAlarmTime = formatTime(scheduledAlarm.get(Calendar.HOUR_OF_DAY), scheduledAlarm.get(Calendar.MINUTE));
                contentText = getString(R.string.active_expanded_alarm, targetTimeStr, formattedDurationStr, formattedAlarmTime);
            } else {
                contentText = getString(R.string.active_collapsed, targetTimeStr, formattedDurationStr);
            }
        } else {
            title = getString(R.string.waiting_title);
            if (showWakeAlarm) {
                String formattedAlarmTime = formatTime(scheduledAlarm.get(Calendar.HOUR_OF_DAY), scheduledAlarm.get(Calendar.MINUTE));
                contentText = getString(R.string.waiting_expanded_alarm, formattedDurationStr, formattedAlarmTime);
            } else {
                contentText = getString(R.string.waiting_collapsed, formattedDurationStr);
            }
        }

        if (!isWakeUpAlarmRinging && !isWakeUpAlarmSnoozed && isNapActive()) {
            java.text.DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);
            String formattedNapTime = timeFormat.format(new Date(napAlarmEndsAt));
            contentText += getString(R.string.notification_nap_suffix, formattedNapTime);
        }

        Intent contentIntent = new Intent(this, MainActivity.class);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(this, 0, contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_zzz)
                .setContentTitle(title)
                .setContentText(contentText)
                .setContentIntent(contentPendingIntent)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setShowWhen(false);

        if (isWakeUpAlarmRinging) {
            builder.addAction(new Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    getString(R.string.action_dismiss_alarm),
                    dismissWakeUpAlarmIntent())
                    .build());
            builder.addAction(new Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_lock_idle_alarm),
                    getString(R.string.action_snooze_alarm),
                    snoozeWakeUpAlarmIntent())
                    .build());
        } else if (isWakeUpAlarmSnoozed) {
            builder.addAction(new Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    getString(R.string.action_dismiss_alarm),
                    dismissWakeUpAlarmIntent())
                    .build());
        } else {
            Notification.Action toggleAction;
            if (stateMachine.isEnabled()) {
                toggleAction = new Notification.Action.Builder(
                        Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                        getString(R.string.action_turn_off),
                        turnOffIntent())
                        .build();
            } else {
                toggleAction = new Notification.Action.Builder(
                        Icon.createWithResource(this, android.R.drawable.ic_media_play),
                        getString(R.string.action_turn_on),
                        turnOnIntent())
                        .build();
            }
            builder.addAction(toggleAction);

            Notification.Action secondAction;
            if (isNapActive()) {
                secondAction = new Notification.Action.Builder(
                        Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                        getString(R.string.action_cancel_nap),
                        cancelNapIntent())
                        .build();
            } else if (hasActiveSleepSession() && isWakeAlarmEnabled()) {
                secondAction = new Notification.Action.Builder(
                        Icon.createWithResource(this, android.R.drawable.ic_lock_idle_alarm),
                        getString(R.string.action_awake),
                        awakeIntent())
                        .build();
            } else {
                secondAction = new Notification.Action.Builder(
                        Icon.createWithResource(this, android.R.drawable.ic_lock_idle_alarm),
                        getString(R.string.action_nap),
                        startNapIntent())
                        .build();
            }
            builder.addAction(secondAction);
        }

        return builder.build();
    }

    private void reloadSettingsAndUpdate() {
        if (preferences == null || stateMachine == null) {
            updateNotification();
            return;
        }

        boolean savedEnabled = preferences.getBoolean(KEY_ENABLED, true);
        int savedDuration = preferences.getInt(KEY_DURATION_MINUTES, SleepTimerStateMachine.DEFAULT_DURATION_MINUTES);
        long now = System.currentTimeMillis();
        boolean musicActive = audioManager != null && audioManager.isMusicActive();

        stateMachine.reloadSettings(savedEnabled, savedDuration, musicActive, now);

        boolean goalEnabled = preferences.getBoolean("wake_up_goal_enabled", false);
        if (goalEnabled) {
            checkAndScheduleSmartWakeUpAlarm(stateMachine.getTimerEndsAt());
        } else {
            dismissAutoSleepAlarm();
        }

        updateNotification();
    }

    private void showOrHideNotification() {
        startForeground(NOTIFICATION_ID, buildNotification());
        isForeground = true;
    }

    private void updateNotification() {
        showOrHideNotification();
    }

    private PendingIntent durationIntent() {
        Intent intent = new Intent(this, SleepTimerService.class).setAction(ACTION_SET_DURATION);
        return PendingIntent.getService(this, 3, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
    }

    private PendingIntent clearGoalIntent() {
        Intent intent = new Intent(this, SleepTimerService.class).setAction(ACTION_CLEAR_GOAL);
        return PendingIntent.getService(this, 11, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent turnOffIntent() {
        Intent intent = new Intent(this, SleepTimerService.class).setAction(ACTION_TURN_OFF);
        return PendingIntent.getService(this, 5, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent turnOnIntent() {
        Intent intent = new Intent(this, SleepTimerService.class).setAction(ACTION_TURN_ON);
        return PendingIntent.getService(this, 7, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent startNapIntent() {
        Intent intent = new Intent(this, NapDialogActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(this, 12, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent cancelNapIntent() {
        Intent intent = new Intent(this, SleepTimerService.class).setAction(ACTION_CANCEL_NAP);
        return PendingIntent.getService(this, 13, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent dismissWakeUpAlarmIntent() {
        Intent intent = new Intent(this, SleepTimerService.class).setAction(ACTION_DISMISS_WAKEUP_ALARM);
        return PendingIntent.getService(this, 14, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent snoozeWakeUpAlarmIntent() {
        Intent intent = new Intent(this, SleepTimerService.class).setAction(ACTION_SNOOZE_WAKEUP_ALARM);
        return PendingIntent.getService(this, 15, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent awakeIntent() {
        Intent intent = new Intent(this, AwakeDialogActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(this, 16, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private String formatTargetTime() {
        long endsAt = stateMachine.getTimerEndsAt();
        if (endsAt <= 0L) {
            return "";
        }
        java.text.DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);
        return timeFormat.format(new Date(endsAt));
    }

    private String formatTime(int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        java.text.DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);
        return timeFormat.format(cal.getTime());
    }

    private void createNotificationChannel() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            NotificationChannel existingChannel = manager.getNotificationChannel(CHANNEL_ID);
            if (existingChannel != null && existingChannel.getImportance() != NotificationManager.IMPORTANCE_LOW) {
                manager.deleteNotificationChannel(CHANNEL_ID);
            }
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.notification_channel_description));
            manager.createNotificationChannel(channel);

        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event != null && event.sensor != null && event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            long now = System.currentTimeMillis();
            if (now - lastSensorEventTimeMs < SENSOR_THROTTLE_MS) {
                return;
            }
            lastSensorEventTimeMs = now;

            float z = event.values[2];
            int currentOrientation = ORIENTATION_UNKNOWN;
            if (z < -8.5f) {
                currentOrientation = ORIENTATION_FACE_DOWN;
            } else if (z > 8.5f) {
                currentOrientation = ORIENTATION_FACE_UP;
            }

            if (currentOrientation != ORIENTATION_UNKNOWN) {
                if (lastOrientation != ORIENTATION_UNKNOWN && lastOrientation != currentOrientation) {
                    handler.post(() -> {
                        if (isWakeUpAlarmRinging) {
                            snoozeWakeUpAlarmViaFlip();
                        } else {
                            stateMachine.onPhoneFlipped(now);
                        }
                    });
                }
                lastOrientation = currentOrientation;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onDestroy() {
        EventLogger.log(this, EventLogger.LEVEL_LOW, "SleepTimerService destroyed");
        stopWakeUpAlarmSound();
        isWakeUpAlarmRinging = false;
        isWakeUpAlarmSnoozed = false;
        setNapAlarmRinging(false);
        unregisterSensorListener();
        unregisterVolumeObserver();
        unregisterDndReceiver();
        unregisterAudioPlaybackCallback();
        cancelTimerCallbacks();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
