package com.bas080.autosleepdroid;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;

public class SleepTimerService extends Service implements SensorEventListener, SleepTimerStateMachine.Callback {
    public static final String ACTION_SET_DURATION = "com.bas080.autosleepdroid.SET_DURATION";
    public static final String ACTION_TURN_OFF = "com.bas080.autosleepdroid.TURN_OFF";
    public static final String ACTION_TURN_ON = "com.bas080.autosleepdroid.TURN_ON";
    public static final String ACTION_ALARM_EXPIRY = "com.bas080.autosleepdroid.ALARM_EXPIRY";
    public static final String ACTION_REDRAW_NOTIFICATION = "com.bas080.autosleepdroid.REDRAW_NOTIFICATION";
    public static final String EXTRA_DURATION = "com.bas080.autosleepdroid.DURATION";
    private static final String CHANNEL_ID = "sleep_timer";
    private static final int NOTIFICATION_ID = 1001;
    private static final String PREFERENCES = "sleep_timer";
    private static final String KEY_ENABLED = "active";
    private static final String KEY_DURATION_MINUTES = "duration_minutes";
    private static final String KEY_TIMER_ENDS_AT = "timer_ends_at";
    private static final String REMOTE_INPUT_KEY = "duration_minutes";
    private static final long PAUSE_RESET_DELAY_MS = 500L;
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
    private boolean sensorListenerRegistered = false;
    private android.os.Vibrator vibrator;

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
        registerAudioPlaybackCallback();

        boolean savedEnabled = preferences.getBoolean(KEY_ENABLED, true);
        int savedDuration = preferences.getInt(KEY_DURATION_MINUTES, SleepTimerStateMachine.DEFAULT_DURATION_MINUTES);
        long savedEndsAt = preferences.getLong(KEY_TIMER_ENDS_AT, 0L);
        int currentVolume = audioManager != null ? audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) : 0;
        boolean musicActive = audioManager != null && audioManager.isMusicActive();

        EventLogger.log(this, "SleepTimerService state initialized (enabled: " + savedEnabled + ", duration: " + savedDuration + "m)");

        if (savedEnabled) {
            startForeground(NOTIFICATION_ID, buildNotification());
        }

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
            sensorListenerRegistered = true;
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private void unregisterSensorListener() {
        if (sensorListenerRegistered && sensorManager != null) {
            sensorListenerRegistered = false;
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : "null";
        EventLogger.log(this, "SleepTimerService onStartCommand (action: " + action + ")");

        if (intent != null) {
            if (ACTION_TURN_OFF.equals(intent.getAction())) {
                EventLogger.log(this, "Timer turned off");
                stateMachine.handleTurnOff(true);
            } else if (ACTION_TURN_ON.equals(intent.getAction())) {
                EventLogger.log(this, "Timer turned on");
                boolean musicActive = audioManager != null && audioManager.isMusicActive();
                stateMachine.handleTurnOn(musicActive, System.currentTimeMillis(), true);
            } else if (ACTION_SET_DURATION.equals(intent.getAction())) {
                handleDurationReply(intent);
            } else if (ACTION_ALARM_EXPIRY.equals(intent.getAction())) {
                EventLogger.log(this, "AlarmManager trigger received");
                int currentVol = audioManager != null ? audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) : 0;
                stateMachine.handleAlarmExpiry(currentVol);
            } else if (ACTION_REDRAW_NOTIFICATION.equals(intent.getAction())) {
                updateNotification();
            }
        }
        return START_STICKY;
    }

    private void handleDurationReply(Intent intent) {
        CharSequence reply = RemoteInput.getResultsFromIntent(intent) == null
                ? null
                : RemoteInput.getResultsFromIntent(intent).getCharSequence(REMOTE_INPUT_KEY);

        int duration = -1;
        if (!TextUtils.isEmpty(reply)) {
            try {
                duration = Integer.parseInt(reply.toString().trim());
            } catch (NumberFormatException ignored) {
                duration = -1;
            }
        }

        boolean musicActive = audioManager != null && audioManager.isMusicActive();
        stateMachine.handleDurationReply(duration, musicActive, System.currentTimeMillis(), true);
        EventLogger.log(this, "Duration reply received: '" + reply + "' -> configured duration = " + stateMachine.getConfiguredDurationMinutes() + "m");
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


    // Callback implementations from SleepTimerStateMachine.Callback
    @Override
    public void onStateChanged(SleepTimerStateMachine.State newState) {
        cancelTimerCallbacks();
        if (newState == SleepTimerStateMachine.State.OFF) {
            onCancelAlarm();
            unregisterSensorListener();
            unregisterVolumeObserver();
            startForeground(NOTIFICATION_ID, buildNotification());
        } else if (newState == SleepTimerStateMachine.State.WAITING) {
            onCancelAlarm();
            unregisterSensorListener();
            unregisterVolumeObserver();
            startForeground(NOTIFICATION_ID, buildNotification());
        } else if (newState == SleepTimerStateMachine.State.FADING) {
            registerSensorListener();
            registerVolumeObserver();
            startForeground(NOTIFICATION_ID, buildNotification());
            startFadeRunnable();
        } else if (newState == SleepTimerStateMachine.State.ACTIVE) {
            registerSensorListener();
            registerVolumeObserver();
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
                    EventLogger.log(this, "Exact alarm permission missing, falling back to setAndAllowWhileIdle");
                    alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                }
            } else if (android.os.Build.VERSION.SDK_INT >= 23) {
                alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }
        } catch (SecurityException e) {
            EventLogger.log(this, "SecurityException scheduling alarm; relying on foreground service polling");
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
        EventLogger.log(this, "Timer expired: pausing media via audio focus loss");
        pauseMediaViaAudioFocus();

        restoreVolumeRunnable = () -> stateMachine.restoreVolumeAfterPause();
        handler.postDelayed(restoreVolumeRunnable, PAUSE_RESET_DELAY_MS);
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
        String title;
        String text;

        if (!stateMachine.isEnabled()) {
            title = getString(R.string.timer_off);
            text = getString(R.string.timer_off);
        } else if (stateMachine.isFading()) {
            title = getString(R.string.fading_title);
            text = getString(R.string.fading_text, configuredDuration);
        } else if (stateMachine.isActive()) {
            title = getString(R.string.active_title);
            text = getString(R.string.active_text, formatTargetTime(), configuredDuration);
        } else {
            title = getString(R.string.waiting_title);
            text = getString(R.string.waiting_text, configuredDuration);
        }

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_zzz)
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false);

        String durationStr = String.valueOf(stateMachine.getConfiguredDurationMinutes());
        builder.getExtras().putString(Notification.EXTRA_REMOTE_INPUT_DRAFT, durationStr);

        RemoteInput remoteInput = new RemoteInput.Builder(REMOTE_INPUT_KEY)
                .setLabel(getString(R.string.set_timer_input_label, durationStr))
                .build();
        remoteInput.getExtras().putInt("android.intent.extra.inputType", InputType.TYPE_CLASS_NUMBER);
        remoteInput.getExtras().putInt("inputType", InputType.TYPE_CLASS_NUMBER);

        Notification.Action setTimerAction = new Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_input_add),
                getString(R.string.action_set_timer),
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

    private String formatTargetTime() {
        long endsAt = stateMachine.getTimerEndsAt();
        if (endsAt <= 0L) {
            return "";
        }
        java.text.DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);
        return timeFormat.format(new java.util.Date(endsAt));
    }

    private void createNotificationChannel() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.notification_channel_description));
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event != null && event.sensor != null && event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float z = event.values[2];
            int currentOrientation = ORIENTATION_UNKNOWN;
            if (z < -8.5f) {
                currentOrientation = ORIENTATION_FACE_DOWN;
            } else if (z > 8.5f) {
                currentOrientation = ORIENTATION_FACE_UP;
            }

            if (currentOrientation != ORIENTATION_UNKNOWN) {
                if (lastOrientation != ORIENTATION_UNKNOWN && lastOrientation != currentOrientation) {
                    stateMachine.onPhoneFlipped(System.currentTimeMillis());
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
