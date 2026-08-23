package com.bas080.autosleepdroid;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.RemoteInput;
import android.content.ComponentName;
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
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;

import java.util.Locale;

public class SleepTimerService extends Service implements SensorEventListener {
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
    private static final long FADE_DURATION_MS = 30_000L;
    private static final long FADE_STEP_INTERVAL_MS = 1_000L;
    private static final int TOTAL_FADE_STEPS = (int) (FADE_DURATION_MS / FADE_STEP_INTERVAL_MS);
    private static final long PAUSE_RESET_DELAY_MS = 500L;
    private static final int DEFAULT_DURATION_MINUTES = 20;
    private static final long INPUT_POLL_INTERVAL_MS = 60_000L;
    private static final int MINUTES_MIN = 1;
    private static final int MINUTES_MAX = 24 * 60;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private AudioManager audioManager;
    private android.app.AlarmManager alarmManager;
    private android.content.SharedPreferences preferences;
    private Runnable expiryRunnable;
    private Runnable notificationRunnable;
    private Runnable fadeRunnable;
    private Runnable inputPollRunnable;
    private Runnable restoreVolumeRunnable;
    private long timerEndsAt;
    private int configuredDurationMinutes;
    private int volumeBeforeFade;
    private boolean enabled;
    private boolean active;
    private boolean fading;
    private boolean suppressVolumeReset;
    private int fadeStep;
    private int lastFadeVolume;
    private int lastObservedVolume;
    private boolean lastObservedMediaActive;

    private static final int ORIENTATION_UNKNOWN = 0;
    private static final int ORIENTATION_FACE_UP = 1;
    private static final int ORIENTATION_FACE_DOWN = 2;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private int lastOrientation = ORIENTATION_UNKNOWN;
    private boolean flipDetected;
    private android.os.Vibrator vibrator;

    @Override
    public void onCreate() {
        super.onCreate();
        EventLogger.log(this, "SleepTimerService created");
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        alarmManager = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
        preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        vibrator = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
        createNotificationChannel();

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }

        initializeStateAndNotification();
    }

    private void initializeStateAndNotification() {
        if (audioManager != null) {
            lastObservedVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        }
        lastObservedMediaActive = false;
        scheduleInputPoll();

        configuredDurationMinutes = preferences.getInt(KEY_DURATION_MINUTES, DEFAULT_DURATION_MINUTES);
        if (!isValidDuration(configuredDurationMinutes)) {
            configuredDurationMinutes = DEFAULT_DURATION_MINUTES;
        }
        enabled = preferences.getBoolean(KEY_ENABLED, true);
        long savedEndsAt = preferences.getLong(KEY_TIMER_ENDS_AT, 0L);

        EventLogger.log(this, "SleepTimerService state initialized (enabled: " + enabled + ", duration: " + configuredDurationMinutes + "m)");

        startForeground(NOTIFICATION_ID, buildNotification());

        if (enabled && savedEndsAt > System.currentTimeMillis()) {
            active = true;
            fading = false;
            timerEndsAt = savedEndsAt;
            scheduleExpiry();
            updateNotification();
        } else if (enabled && savedEndsAt > 0L && savedEndsAt <= System.currentTimeMillis()) {
            active = true;
            fading = false;
            beginFadeOut();
        } else if (enabled && audioManager != null && audioManager.isMusicActive()) {
            startTimer(configuredDurationMinutes);
        } else {
            active = false;
            fading = false;
            updateNotification();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : "null";
        EventLogger.log(this, "SleepTimerService onStartCommand (action: " + action + ")");

        if (intent != null) {
            if (ACTION_TURN_OFF.equals(intent.getAction())) {
                handleTurnOff();
            } else if (ACTION_TURN_ON.equals(intent.getAction())) {
                handleTurnOn();
            } else if (ACTION_SET_DURATION.equals(intent.getAction())) {
                handleDurationReply(intent);
            } else if (ACTION_ALARM_EXPIRY.equals(intent.getAction())) {
                handleAlarmExpiry();
            } else if (ACTION_REDRAW_NOTIFICATION.equals(intent.getAction())) {
                updateNotification();
            }
        }
        return START_STICKY;
    }

    private void triggerFaintVibration() {
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

    private void handleTurnOff() {
        EventLogger.log(this, "Timer turned off");
        triggerFaintVibration();
        enabled = false;
        active = false;
        fading = false;
        cancelTimerCallbacks();
        preferences.edit()
                .putBoolean(KEY_ENABLED, false)
                .remove(KEY_TIMER_ENDS_AT)
                .apply();
        updateNotification();
    }

    private void handleAlarmExpiry() {
        EventLogger.log(this, "AlarmManager trigger received");
        if (enabled && active && !fading) {
            beginFadeOut();
        }
    }

    private void handleTurnOn() {
        EventLogger.log(this, "Timer turned on");
        triggerFaintVibration();
        enabled = true;
        preferences.edit().putBoolean(KEY_ENABLED, true).apply();
        if (audioManager != null && audioManager.isMusicActive()) {
            startTimer(configuredDurationMinutes);
        } else {
            active = false;
            fading = false;
            cancelTimerCallbacks();
            updateNotification();
        }
    }

    private void handleDurationReply(Intent intent) {
        triggerFaintVibration();
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

        if (isValidDuration(duration)) {
            configuredDurationMinutes = duration;
        } else if (!isValidDuration(configuredDurationMinutes)) {
            configuredDurationMinutes = DEFAULT_DURATION_MINUTES;
        }

        EventLogger.log(this, "Duration reply received: '" + reply + "' -> configured duration = " + configuredDurationMinutes + "m");

        enabled = true;
        preferences.edit()
                .putInt(KEY_DURATION_MINUTES, configuredDurationMinutes)
                .putBoolean(KEY_ENABLED, true)
                .apply();

        if (audioManager != null && audioManager.isMusicActive()) {
            startTimer(configuredDurationMinutes);
        } else {
            active = false;
            fading = false;
            cancelTimerCallbacks();
            updateNotification();
        }
    }

    private boolean isValidDuration(int minutes) {
        return minutes >= MINUTES_MIN && minutes <= MINUTES_MAX;
    }

    private void startTimer(int durationMinutes) {
        cancelTimerCallbacks();
        enabled = true;
        active = true;
        fading = false;
        timerEndsAt = System.currentTimeMillis() + durationMinutes * 60_000L;
        EventLogger.log(this, "Timer started for " + durationMinutes + "m");
        preferences.edit()
                .putBoolean(KEY_ENABLED, true)
                .putLong(KEY_TIMER_ENDS_AT, timerEndsAt)
                .apply();
        scheduleExpiry();
        updateNotification();
    }

    private void resetTimerForVolumeChange() {
        if (!fading && isValidDuration(configuredDurationMinutes)) {
            EventLogger.log(this, "Timer reset due to volume change");
            triggerFaintVibration();
            startTimer(configuredDurationMinutes);
        }
    }

    private void startTimerFromConfiguredDuration() {
        if (isValidDuration(configuredDurationMinutes)) {
            startTimer(configuredDurationMinutes);
        }
    }

    private void scheduleExpiry() {
        long delay = Math.max(0L, timerEndsAt - System.currentTimeMillis());
        expiryRunnable = this::beginFadeOut;
        handler.postDelayed(expiryRunnable, delay);
        scheduleAlarm(timerEndsAt);
        scheduleNotificationRefresh();
    }

    private void scheduleAlarm(long triggerAtMillis) {
        if (alarmManager == null) {
            return;
        }
        Intent intent = new Intent(this, SleepTimerService.class).setAction(ACTION_ALARM_EXPIRY);
        PendingIntent pendingIntent = PendingIntent.getService(this, 100, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (android.os.Build.VERSION.SDK_INT >= 23) {
            alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        } else {
            alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        }
    }

    private void cancelAlarm() {
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

    private void scheduleNotificationRefresh() {
        notificationRunnable = () -> {
            if (active && !fading) {
                updateNotification();
                scheduleNotificationRefresh();
            }
        };
        handler.postDelayed(notificationRunnable, 60_000L);
    }

    private void beginFadeOut() {
        if (!enabled || !active || fading) {
            return;
        }
        fading = true;
        volumeBeforeFade = audioManager != null ? audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) : 0;
        lastFadeVolume = volumeBeforeFade;
        fadeStep = 0;
        EventLogger.log(this, "Fade-out started (volume before fade: " + volumeBeforeFade + ")");
        updateNotification();
        fadeRunnable = this::runFadeStep;
        handler.post(fadeRunnable);
    }

    private void runFadeStep() {
        if (audioManager == null) {
            finishExpiry();
            return;
        }

        if (checkAndClearFlipDetected()) {
            cancelFadeForFlip();
            return;
        }

        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (currentVolume != lastFadeVolume) {
            cancelFadeForVolumeChange();
            return;
        }

        fadeStep++;
        int targetVolume = 0;
        float progress = (float) fadeStep / TOTAL_FADE_STEPS;
        float fraction = 1.0f - (1.0f - progress) * (1.0f - progress);
        int nextVolume = Math.round(volumeBeforeFade
                - (volumeBeforeFade - targetVolume) * fraction);
        suppressVolumeReset = true;
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nextVolume, 0);
        suppressVolumeReset = false;
        lastFadeVolume = nextVolume;

        EventLogger.log(this, "Fade step " + fadeStep + "/" + TOTAL_FADE_STEPS + " (volume: " + nextVolume + ")");

        if (fadeStep >= TOTAL_FADE_STEPS) {
            finishExpiry();
        } else {
            handler.postDelayed(fadeRunnable, FADE_STEP_INTERVAL_MS);
        }
    }

    private void finishExpiry() {
        EventLogger.log(this, "Timer expired: pausing media via audio focus loss");
        pauseMediaViaAudioFocus();

        restoreVolumeRunnable = () -> {
            EventLogger.log(this, "Restoring volume to " + volumeBeforeFade);
            if (audioManager != null) {
                lastObservedMediaActive = audioManager.isMusicActive();
                suppressVolumeReset = true;
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeBeforeFade, 0);
                suppressVolumeReset = false;
                lastObservedVolume = volumeBeforeFade;
            }
            active = false;
            fading = false;
            enabled = true;
            preferences.edit()
                    .putBoolean(KEY_ENABLED, true)
                    .remove(KEY_TIMER_ENDS_AT)
                    .apply();
            cancelTimerCallbacks();
            updateNotification();
        };
        handler.postDelayed(restoreVolumeRunnable, PAUSE_RESET_DELAY_MS);
    }

    private void cancelFadeForVolumeChange() {
        EventLogger.log(this, "Fade cancelled due to volume change");
        triggerFaintVibration();
        fading = false;
        cancelTimerCallbacks();
        if (isValidDuration(configuredDurationMinutes)) {
            startTimer(configuredDurationMinutes);
        } else {
            updateNotification();
        }
    }

    private void cancelFadeForFlip() {
        EventLogger.log(this, "Fade cancelled due to phone flip gesture (restoring volume to " + volumeBeforeFade + ")");
        triggerFaintVibration();
        fading = false;
        cancelTimerCallbacks();
        if (audioManager != null) {
            suppressVolumeReset = true;
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeBeforeFade, 0);
            suppressVolumeReset = false;
            lastObservedVolume = volumeBeforeFade;
        }
        if (isValidDuration(configuredDurationMinutes)) {
            startTimer(configuredDurationMinutes);
        } else {
            updateNotification();
        }
    }

    private void cancelTimerCallbacks() {
        cancelAlarm();
        if (expiryRunnable != null) {
            handler.removeCallbacks(expiryRunnable);
        }
        if (notificationRunnable != null) {
            handler.removeCallbacks(notificationRunnable);
        }
        if (fadeRunnable != null) {
            handler.removeCallbacks(fadeRunnable);
        }
        if (restoreVolumeRunnable != null) {
            handler.removeCallbacks(restoreVolumeRunnable);
        }
    }

    private void scheduleInputPoll() {
        if (inputPollRunnable != null) {
            handler.removeCallbacks(inputPollRunnable);
        }
        inputPollRunnable = () -> {
            pollInputs();
            scheduleInputPoll();
        };
        handler.postDelayed(inputPollRunnable, INPUT_POLL_INTERVAL_MS);
    }

    private void pollInputs() {
        if (audioManager == null) {
            return;
        }

        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        boolean mediaActive = audioManager.isMusicActive();
        boolean volumeChanged = currentVolume != lastObservedVolume;
        boolean playbackStateChanged = mediaActive != lastObservedMediaActive;
        boolean playbackStopped = !mediaActive && lastObservedMediaActive;

        if (volumeChanged) {
            EventLogger.log(this, "Volume changed: " + lastObservedVolume + " -> " + currentVolume);
        }
        if (playbackStateChanged) {
            EventLogger.log(this, "Media playback state changed: active = " + mediaActive);
        }

        lastObservedVolume = currentVolume;
        lastObservedMediaActive = mediaActive;

        boolean flipped = checkAndClearFlipDetected();

        if (enabled) {
            if (fading && flipped) {
                cancelFadeForFlip();
            } else if (fading && volumeChanged) {
                cancelFadeForVolumeChange();
            } else if (active && flipped && !suppressVolumeReset) {
                EventLogger.log(this, "Timer reset due to phone flip gesture");
                resetTimerForVolumeChange();
            } else if (active && volumeChanged && !suppressVolumeReset) {
                resetTimerForVolumeChange();
            } else if (!active && !fading && mediaActive) {
                startTimerFromConfiguredDuration();
            } else if (active && playbackStopped) {
                EventLogger.log(this, "Playback stopped while timer was active");
                active = false;
                cancelTimerCallbacks();
                updateNotification();
            }
        }
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
        String configuredDuration = getString(R.string.configured_duration, configuredDurationMinutes);
        String title;
        String text;

        if (!enabled) {
            title = getString(R.string.timer_off);
            text = getString(R.string.timer_off);
        } else if (fading) {
            title = getString(R.string.fading_title);
            text = getString(R.string.fading_text, configuredDuration);
        } else if (active) {
            title = getString(R.string.active_title);
            text = getString(R.string.active_text, formatRemaining(), configuredDuration);
        } else {
            title = getString(R.string.waiting_title);
            text = getString(R.string.waiting_text, configuredDuration);
        }

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false);

        String durationStr = String.valueOf(configuredDurationMinutes);
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

        if (enabled) {
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

    private String formatRemaining() {
        long remaining = Math.max(0L, timerEndsAt - System.currentTimeMillis());
        long totalMinutes = (remaining + 59_999L) / 60_000L;
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        if (hours > 0L) {
            return getString(R.string.time_format_hours_minutes, hours, minutes);
        }
        return getString(R.string.time_format_minutes, minutes);
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
            if (z < -8.5f) {
                if (lastOrientation == ORIENTATION_FACE_UP) {
                    flipDetected = true;
                }
                lastOrientation = ORIENTATION_FACE_DOWN;
            } else if (z > 8.5f) {
                if (lastOrientation == ORIENTATION_FACE_DOWN) {
                    flipDetected = true;
                }
                lastOrientation = ORIENTATION_FACE_UP;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private boolean checkAndClearFlipDetected() {
        if (flipDetected) {
            flipDetected = false;
            return true;
        }
        return false;
    }

    @Override
    public void onDestroy() {
        EventLogger.log(this, "SleepTimerService destroyed");
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        if (inputPollRunnable != null) {
            handler.removeCallbacks(inputPollRunnable);
        }
        cancelTimerCallbacks();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
