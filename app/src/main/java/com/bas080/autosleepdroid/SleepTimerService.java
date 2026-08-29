package com.bas080.autosleepdroid;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.RemoteInput;
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
    public static final String ACTION_REDRAW_NOTIFICATION = "com.bas080.autosleepdroid.REDRAW_NOTIFICATION";
    public static final String ACTION_CLEAR_GOAL = "com.bas080.autosleepdroid.CLEAR_GOAL";
    public static final String EXTRA_DURATION = "com.bas080.autosleepdroid.DURATION";
    public static final String ALARM_SEARCH_NAME = "Auto Sleep";
    public static final String KEY_WAKEUP_LAST_SCHEDULED_MS = "wakeup_last_scheduled_ms";

    private static final String CHANNEL_ID = "sleep_timer";
    private static final String WAKEUP_CHANNEL_ID = "wakeup_alarm";
    private static final int NOTIFICATION_ID = 1001;
    private static final int WAKEUP_NOTIFICATION_ID = 1002;
    private static final long SNOOZE_DURATION_MS = 9 * 60_000L;
    private static final String PREFERENCES = "sleep_timer";
    private static final String KEY_ENABLED = "active";
    private static final String KEY_DURATION_MINUTES = "duration_minutes";
    private static final String KEY_TIMER_ENDS_AT = "timer_ends_at";
    private static final String REMOTE_INPUT_KEY = "duration_minutes";
    private static final long PAUSE_RESET_DELAY_MS = 500L;
    private static final long SENSOR_THROTTLE_MS = 300L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AudioManager audioManager;
    private android.app.AlarmManager alarmManager;
    private android.content.SharedPreferences preferences;
    private Runnable expiryRunnable;
    private Runnable fadeRunnable;
    private Runnable restoreVolumeRunnable;
    private AudioManager.AudioPlaybackCallback audioPlaybackCallback;
    private android.content.BroadcastReceiver volumeReceiver;

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
    private boolean isWakeUpAlarmRinging = false;

    private SleepTimerStateMachine stateMachine;

    @Override
    public void onCreate() {
        super.onCreate();
        EventLogger.log(this, "SleepTimerService created");
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
        int currentVolume = audioManager != null ? audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) : 0;
        boolean musicActive = audioManager != null && audioManager.isMusicActive();

        EventLogger.log(this, "SleepTimerService state initialized (enabled: " + savedEnabled + ", duration: " + savedDuration + "m)");

        startForeground(NOTIFICATION_ID, buildNotification());

        stateMachine.initialize(savedEnabled, savedDuration, savedEndsAt, currentVolume, musicActive, System.currentTimeMillis());
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

    private void registerVolumeObserver() {
        if (volumeReceiver == null) {
            volumeReceiver = new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if ("android.media.VOLUME_CHANGED_ACTION".equals(intent.getAction())) {
                        int streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1);
                        if (streamType == AudioManager.STREAM_MUSIC || streamType == -1) {
                            if (audioManager != null) {
                                int currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                                stateMachine.onVolumeChanged(currentVol, System.currentTimeMillis());
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
                EventLogger.log(this, "Timer turned off");
                stateMachine.handleTurnOff(true);
                android.widget.Toast.makeText(this, R.string.toast_timer_turned_off, android.widget.Toast.LENGTH_SHORT).show();
            } else if (ACTION_TURN_ON.equals(intent.getAction())) {
                EventLogger.log(this, "Timer turned on");
                boolean musicActive = audioManager != null && audioManager.isMusicActive();
                stateMachine.handleTurnOn(musicActive, System.currentTimeMillis(), true);
                android.widget.Toast.makeText(this, R.string.toast_timer_turned_on, android.widget.Toast.LENGTH_SHORT).show();
            } else if (ACTION_SET_DURATION.equals(intent.getAction())) {
                handleDurationReply(intent);
            } else if (ACTION_ALARM_EXPIRY.equals(intent.getAction())) {
                EventLogger.log(this, "AlarmManager trigger received");
                int currentVol = audioManager != null ? audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) : 0;
                stateMachine.handleAlarmExpiry(currentVol);
            } else if (ACTION_WAKEUP_ALARM_EXPIRY.equals(intent.getAction())) {
                EventLogger.log(this, "Auto Sleep wake-up alarm triggered");
                isWakeUpAlarmRinging = true;
                updateListenersRegistration();
                playWakeUpAlarmSound();
                showWakeUpAlarmNotification();
            } else if (ACTION_DISMISS_WAKEUP_ALARM.equals(intent.getAction())) {
                EventLogger.log(this, "Wake-Up Goal alarm dismissed");
                stopWakeUpAlarmSound();
                cancelWakeUpAlarmNotification();
                isWakeUpAlarmRinging = false;
                updateListenersRegistration();
                android.widget.Toast.makeText(this, R.string.toast_alarm_dismissed, android.widget.Toast.LENGTH_SHORT).show();
            } else if (ACTION_SNOOZE_WAKEUP_ALARM.equals(intent.getAction())) {
                EventLogger.log(this, "Wake-Up Goal alarm snoozed for 9m");
                stopWakeUpAlarmSound();
                cancelWakeUpAlarmNotification();
                snoozeWakeUpAlarm();
                isWakeUpAlarmRinging = false;
                updateListenersRegistration();
                android.widget.Toast.makeText(this, R.string.toast_alarm_snoozed, android.widget.Toast.LENGTH_SHORT).show();
            } else if (ACTION_CLEAR_GOAL.equals(intent.getAction())) {
                dismissAutoSleepAlarm();
                updateNotification();
            } else if (ACTION_REDRAW_NOTIFICATION.equals(intent.getAction())) {
                updateNotification();
            }
        }
        return START_STICKY;
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
        if (input == null) {
            return -1;
        }
        String s = input.replaceAll("\\s+", "").toLowerCase(java.util.Locale.US);
        if (s.isEmpty()) {
            return -1;
        }

        try {
            if (s.matches("^[0-9]+$")) {
                long val = Long.parseLong(s);
                return (val > 0 && val <= Integer.MAX_VALUE) ? (int) val : -1;
            }

            java.util.regex.Matcher m;

            m = java.util.regex.Pattern.compile("^([0-9]+)h$").matcher(s);
            if (m.matches()) {
                long hours = Long.parseLong(m.group(1));
                long total = hours * 60L;
                return (total > 0 && total <= Integer.MAX_VALUE) ? (int) total : -1;
            }

            m = java.util.regex.Pattern.compile("^([0-9]+)m$").matcher(s);
            if (m.matches()) {
                long mins = Long.parseLong(m.group(1));
                return (mins > 0 && mins <= Integer.MAX_VALUE) ? (int) mins : -1;
            }

            m = java.util.regex.Pattern.compile("^([0-9]+)h([0-9]+)m$").matcher(s);
            if (m.matches()) {
                long hours = Long.parseLong(m.group(1));
                long mins = Long.parseLong(m.group(2));
                long total = hours * 60L + mins;
                return (total > 0 && total <= Integer.MAX_VALUE) ? (int) total : -1;
            }

            m = java.util.regex.Pattern.compile("^([0-9]+)m[0-9]+s$").matcher(s);
            if (m.matches()) {
                long mins = Long.parseLong(m.group(1));
                return (mins > 0 && mins <= Integer.MAX_VALUE) ? (int) mins : -1;
            }

            m = java.util.regex.Pattern.compile("^([0-9]+)h[0-9]+s$").matcher(s);
            if (m.matches()) {
                long hours = Long.parseLong(m.group(1));
                long total = hours * 60L;
                return (total > 0 && total <= Integer.MAX_VALUE) ? (int) total : -1;
            }

            m = java.util.regex.Pattern.compile("^([0-9]+)h([0-9]+)m[0-9]+s$").matcher(s);
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

    private void handleDurationReply(Intent intent) {
        CharSequence reply = RemoteInput.getResultsFromIntent(intent) == null
                ? null
                : RemoteInput.getResultsFromIntent(intent).getCharSequence(REMOTE_INPUT_KEY);

        int duration = parseDurationMinutes(reply != null ? reply.toString() : null);

        boolean musicActive = audioManager != null && audioManager.isMusicActive();
        stateMachine.handleDurationReply(duration, musicActive, System.currentTimeMillis(), true);
        EventLogger.log(this, "Duration set to " + stateMachine.getConfiguredDurationMinutes() + "m (input: '" + reply + "')");

        if (duration != -1) {
            String formattedStr = formatDurationString(stateMachine.getConfiguredDurationMinutes());
            android.widget.Toast.makeText(this, getString(R.string.toast_duration_set, formattedStr), android.widget.Toast.LENGTH_SHORT).show();
        } else {
            android.widget.Toast.makeText(this, R.string.toast_duration_invalid, android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void startFadeRunnable() {
        EventLogger.log(this, "Fade-out started");
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
        boolean needSensorAndVolume = stateMachine.isActive() || isWakeUpAlarmRinging;
        if (needSensorAndVolume) {
            registerSensorListener();
            registerVolumeObserver();
        } else {
            unregisterSensorListener();
            unregisterVolumeObserver();
        }
    }

    @Override
    public void onStateChanged(SleepTimerStateMachine.State newState) {
        cancelTimerCallbacks();
        if (newState == SleepTimerStateMachine.State.OFF) {
            unregisterAudioPlaybackCallback();
            onCancelAlarm();
            dismissAutoSleepAlarm();
            updateListenersRegistration();
            startForeground(NOTIFICATION_ID, buildNotification());
        } else if (newState == SleepTimerStateMachine.State.WAITING) {
            registerAudioPlaybackCallback();
            onCancelAlarm();
            updateListenersRegistration();
            startForeground(NOTIFICATION_ID, buildNotification());
        } else if (newState == SleepTimerStateMachine.State.FADING) {
            unregisterAudioPlaybackCallback();
            updateListenersRegistration();
            startForeground(NOTIFICATION_ID, buildNotification());
            startFadeRunnable();
        } else if (newState == SleepTimerStateMachine.State.ACTIVE) {
            unregisterAudioPlaybackCallback();
            updateListenersRegistration();
            checkAndScheduleSmartWakeUpAlarm(stateMachine.getTimerEndsAt());
            startForeground(NOTIFICATION_ID, buildNotification());
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
        EventLogger.log(this, "Timer expired: pausing media");
        pauseMediaViaAudioFocus();

        restoreVolumeRunnable = () -> stateMachine.restoreVolumeAfterPause();
        handler.postDelayed(restoreVolumeRunnable, PAUSE_RESET_DELAY_MS);
    }

    public static Calendar calculateScheduledAlarm(Context context, long now, long timerEndsAt) {
        if (context == null) {
            return null;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        boolean enabled = prefs.getBoolean("wake_up_goal_enabled", false);
        if (!enabled) {
            return null;
        }

        int goalHour = prefs.getInt("wake_up_goal_hour", 6);
        int goalMin = prefs.getInt("wake_up_goal_minute", 30);
        int minSleepMin = prefs.getInt("min_sleep_duration_minutes", 450);

        Calendar calGoal = Calendar.getInstance();
        calGoal.setTimeInMillis(now);
        calGoal.set(Calendar.HOUR_OF_DAY, goalHour);
        calGoal.set(Calendar.MINUTE, goalMin);
        calGoal.set(Calendar.SECOND, 0);
        calGoal.set(Calendar.MILLISECOND, 0);

        if (calGoal.getTimeInMillis() <= now) {
            calGoal.add(Calendar.DAY_OF_YEAR, 1);
        }

        long targetGoalMillis = calGoal.getTimeInMillis();
        long diffMillis = targetGoalMillis - now;
        long TWELVE_HOURS_MS = 12 * 60 * 60_000L;

        if (diffMillis > TWELVE_HOURS_MS) {
            return null;
        }

        long minWakeTimeMillis = (timerEndsAt > 0L ? timerEndsAt : now) + minSleepMin * 60_000L;
        long scheduledAlarmMillis = Math.max(targetGoalMillis, minWakeTimeMillis);

        Calendar calAlarm = Calendar.getInstance();
        calAlarm.setTimeInMillis(scheduledAlarmMillis);
        return calAlarm;
    }

    private void checkAndScheduleSmartWakeUpAlarm(long timerEndsAt) {
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
        scheduleExpiry();
        checkAndScheduleSmartWakeUpAlarm(stateMachine.getTimerEndsAt());
    }

    private void playWakeUpAlarmSound() {
        stopWakeUpAlarmSound();
        try {
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            currentAlarmRingtone = RingtoneManager.getRingtone(getApplicationContext(), alarmUri);
            if (currentAlarmRingtone != null) {
                if (android.os.Build.VERSION.SDK_INT >= 21) {
                    currentAlarmRingtone.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build());
                } else {
                    currentAlarmRingtone.setStreamType(AudioManager.STREAM_ALARM);
                }
                currentAlarmRingtone.play();
                EventLogger.log(this, "Wake-Up Goal alarm tone started playing");
            }
        } catch (Exception e) {
            EventLogger.log(this, "Failed to play wake-up alarm sound: " + e.getMessage());
        }
    }

    private void stopWakeUpAlarmSound() {
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

    private void showWakeUpAlarmNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }

        Intent dismissIntent = new Intent(this, SleepTimerService.class).setAction(ACTION_DISMISS_WAKEUP_ALARM);
        PendingIntent dismissPendingIntent = PendingIntent.getService(this, 103, dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent snoozeIntent = new Intent(this, SleepTimerService.class).setAction(ACTION_SNOOZE_WAKEUP_ALARM);
        PendingIntent snoozePendingIntent = PendingIntent.getService(this, 104, snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent fullScreenIntent = new Intent(this, MainActivity.class);
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(this, 105, fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = new Notification.Builder(this, WAKEUP_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_zzz)
                .setContentTitle(getString(R.string.wakeup_alarm_title))
                .setContentText(getString(R.string.wakeup_alarm_text))
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_HIGH)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                        getString(R.string.action_dismiss_alarm),
                        dismissPendingIntent).build())
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, android.R.drawable.ic_popup_reminder),
                        getString(R.string.action_snooze_alarm),
                        snoozePendingIntent).build());

        manager.notify(WAKEUP_NOTIFICATION_ID, builder.build());
    }

    private void cancelWakeUpAlarmNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(WAKEUP_NOTIFICATION_ID);
        }
    }

    private void snoozeWakeUpAlarm() {
        if (alarmManager == null) {
            return;
        }
        long snoozeTimeMs = System.currentTimeMillis() + SNOOZE_DURATION_MS;

        Intent intent = new Intent(this, SleepTimerService.class).setAction(ACTION_WAKEUP_ALARM_EXPIRY);
        PendingIntent pendingIntent = PendingIntent.getService(this, 101, intent,
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
        cancelWakeUpAlarmNotification();
        snoozeWakeUpAlarm();
        isWakeUpAlarmRinging = false;
        onTriggerVibration();
        updateListenersRegistration();
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
        String configuredDuration = getString(R.string.configured_duration, stateMachine.getConfiguredDurationMinutes());
        String collapsedText;
        String expandedText;

        if (!stateMachine.isEnabled()) {
            collapsedText = getString(R.string.timer_off_collapsed);
            expandedText = getString(R.string.timer_off_expanded);
        } else if (stateMachine.isFading()) {
            collapsedText = getString(R.string.fading_collapsed);
            expandedText = getString(R.string.fading_expanded);
        } else if (stateMachine.isActive()) {
            String targetTimeStr = formatTargetTime();
            collapsedText = getString(R.string.active_collapsed, targetTimeStr);

            Calendar scheduledAlarm = calculateScheduledAlarm(this, System.currentTimeMillis(), stateMachine.getTimerEndsAt());

            if (scheduledAlarm != null) {
                String formattedAlarmTime = formatTime(scheduledAlarm.get(Calendar.HOUR_OF_DAY), scheduledAlarm.get(Calendar.MINUTE));
                expandedText = getString(R.string.active_expanded_alarm, targetTimeStr, configuredDuration, formattedAlarmTime);
            } else {
                expandedText = getString(R.string.active_expanded, targetTimeStr, configuredDuration);
            }
        } else {
            collapsedText = getString(R.string.waiting_collapsed);
            expandedText = getString(R.string.waiting_expanded, configuredDuration);
        }

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_zzz)
                .setContentTitle(collapsedText)
                .setContentText(collapsedText)
                .setStyle(new Notification.BigTextStyle().bigText(expandedText))
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setShowWhen(false);

        String durationStr = String.valueOf(stateMachine.getConfiguredDurationMinutes());
        builder.getExtras().putString(Notification.EXTRA_REMOTE_INPUT_DRAFT, durationStr);

        RemoteInput remoteInput = new RemoteInput.Builder(REMOTE_INPUT_KEY)
                .setLabel(getString(R.string.set_timer_input_label, durationStr))
                .build();
        remoteInput.getExtras().putInt("android.intent.extra.inputType", InputType.TYPE_CLASS_NUMBER);
        remoteInput.getExtras().putInt("inputType", InputType.TYPE_CLASS_NUMBER);

        String formattedDurationStr = formatDurationString(stateMachine.getConfiguredDurationMinutes());
        String actionTitle = getString(R.string.action_sleep_duration, formattedDurationStr);

        Notification.Action setTimerAction = new Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_input_add),
                actionTitle,
                durationIntent())
                .addRemoteInput(remoteInput)
                .build();
        builder.addAction(setTimerAction);

        if (stateMachine.isEnabled()) {
            Notification.Action turnOffAction = new Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    getString(R.string.action_turn_off),
                    turnOffIntent())
                    .build();
            builder.addAction(turnOffAction);
        } else {
            Notification.Action turnOnAction = new Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_play),
                    getString(R.string.action_turn_on),
                    turnOnIntent())
                    .build();
            builder.addAction(turnOnAction);
        }

        boolean goalEnabled = preferences != null && preferences.getBoolean("wake_up_goal_enabled", false);
        String goalTitle;
        if (goalEnabled && preferences != null) {
            int goalHour = preferences.getInt("wake_up_goal_hour", 6);
            int goalMin = preferences.getInt("wake_up_goal_minute", 30);
            String formattedGoalTime = formatTime(goalHour, goalMin);
            goalTitle = getString(R.string.action_goal_time, formattedGoalTime);
        } else {
            goalTitle = getString(R.string.action_set_goal);
        }

        Notification.Action goalAction = new Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_menu_my_calendar),
                goalTitle,
                goalIntent())
                .build();
        builder.addAction(goalAction);

        return builder.build();
    }

    private void updateNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    private PendingIntent durationIntent() {
        Intent intent = new Intent(this, SleepTimerService.class).setAction(ACTION_SET_DURATION);
        return PendingIntent.getService(this, 3, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
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

    private PendingIntent goalIntent() {
        Intent intent = new Intent(this, GoalSettingsDialogActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return PendingIntent.getActivity(this, 10, intent,
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

            NotificationChannel wakeupChannel = new NotificationChannel(
                    WAKEUP_CHANNEL_ID, getString(R.string.wakeup_alarm_title), NotificationManager.IMPORTANCE_HIGH);
            wakeupChannel.setDescription(getString(R.string.wakeup_alarm_text));
            wakeupChannel.setSound(null, null);
            manager.createNotificationChannel(wakeupChannel);
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
        EventLogger.log(this, "SleepTimerService destroyed");
        stopWakeUpAlarmSound();
        cancelWakeUpAlarmNotification();
        isWakeUpAlarmRinging = false;
        unregisterSensorListener();
        unregisterVolumeObserver();
        unregisterAudioPlaybackCallback();
        cancelTimerCallbacks();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
