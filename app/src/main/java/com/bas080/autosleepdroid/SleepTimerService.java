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
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;

import java.util.Locale;

public class SleepTimerService extends Service {
    public static final String ACTION_DISABLE = "com.bas080.autosleepdroid.DISABLE";
    public static final String ACTION_SET_DURATION = "com.bas080.autosleepdroid.SET_DURATION";
    public static final String EXTRA_DURATION = "com.bas080.autosleepdroid.DURATION";
    private static final String CHANNEL_ID = "sleep_timer";
    private static final int NOTIFICATION_ID = 1001;
    private static final String PREFERENCES = "sleep_timer";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_DURATION_MINUTES = "duration_minutes";
    private static final String REMOTE_INPUT_KEY = "duration_minutes";
    private static final long FADE_DURATION_MS = 15_000L;
    private static final int DEFAULT_DURATION_MINUTES = 20;
    private static final long INPUT_POLL_INTERVAL_MS = 60_000L;
    private static final int MINUTES_MIN = 1;
    private static final int MINUTES_MAX = 24 * 60;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private AudioManager audioManager;
    private android.content.SharedPreferences preferences;
    private Runnable expiryRunnable;
    private Runnable notificationRunnable;
    private Runnable fadeRunnable;
    private Runnable inputPollRunnable;
    private long timerEndsAt;
    private int configuredDurationMinutes;
    private int volumeBeforeFade;
    private boolean active;
    private boolean fading;
    private boolean suppressVolumeReset;
    private int fadeStep;
    private int lastFadeVolume;
    private int lastObservedVolume;
    private boolean lastObservedMediaActive;

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        createNotificationChannel();
        lastObservedVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        lastObservedMediaActive = false;
        scheduleInputPoll();
        startForeground(NOTIFICATION_ID, buildNotification());

        configuredDurationMinutes = preferences.getInt(
            KEY_DURATION_MINUTES, DEFAULT_DURATION_MINUTES);
        active = preferences.getBoolean(KEY_ACTIVE, false);
        if (active && isValidDuration(configuredDurationMinutes)) {
            startTimer(configuredDurationMinutes);
        } else {
            active = false;
            updateNotification();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_DISABLE.equals(intent.getAction())) {
            disableTimer();
        } else if (intent != null && ACTION_SET_DURATION.equals(intent.getAction())) {
            handleDurationReply(intent);
        }
        return START_STICKY;
    }

    private void handleDurationReply(Intent intent) {
        CharSequence reply = RemoteInput.getResultsFromIntent(intent) == null
                ? null
                : RemoteInput.getResultsFromIntent(intent).getCharSequence(REMOTE_INPUT_KEY);
        if (TextUtils.isEmpty(reply)) {
            updateNotification();
            return;
        }

        int duration;
        try {
            duration = Integer.parseInt(reply.toString().trim());
        } catch (NumberFormatException exception) {
            updateNotification("Enter a duration from 1 to 1440 minutes.");
            return;
        }

        if (!isValidDuration(duration)) {
            updateNotification("Enter a duration from 1 to 1440 minutes.");
            return;
        }
        configuredDurationMinutes = duration;
        preferences.edit()
                .putInt(KEY_DURATION_MINUTES, configuredDurationMinutes)
                .putBoolean(KEY_ACTIVE, true)
                .apply();
        startTimer(configuredDurationMinutes);
    }

    private boolean isValidDuration(int minutes) {
        return minutes >= MINUTES_MIN && minutes <= MINUTES_MAX;
    }

    private void startTimer(int durationMinutes) {
        cancelTimerCallbacks();
        active = true;
        fading = false;
        timerEndsAt = System.currentTimeMillis() + durationMinutes * 60_000L;
        preferences.edit().putBoolean(KEY_ACTIVE, true).apply();
        scheduleExpiry();
        updateNotification();
    }

    private void resetTimerForVolumeChange() {
        if (!fading && isValidDuration(configuredDurationMinutes)) {
            startTimer(configuredDurationMinutes);
        }
    }

    private void startTimerFromConfiguredDuration() {
        if (isValidDuration(configuredDurationMinutes)) {
            startTimer(configuredDurationMinutes);
        }
    }

    private void disableTimer() {
        active = false;
        fading = false;
        lastObservedMediaActive = audioManager.isMusicActive();
        preferences.edit().putBoolean(KEY_ACTIVE, false).apply();
        cancelTimerCallbacks();
        updateNotification();
    }

    private void scheduleExpiry() {
        expiryRunnable = this::beginFadeOut;
        handler.postDelayed(expiryRunnable, Math.max(0L, timerEndsAt - System.currentTimeMillis()));
        scheduleNotificationRefresh();
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
        if (!active || fading) {
            return;
        }
        fading = true;
        volumeBeforeFade = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        lastFadeVolume = volumeBeforeFade;
        fadeStep = 0;
        fadeRunnable = this::runFadeStep;
        handler.post(fadeRunnable);
    }

    private void runFadeStep() {
        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (currentVolume != lastFadeVolume) {
            cancelFadeForVolumeChange();
            return;
        }

        fadeStep++;
        int targetVolume = volumeBeforeFade / 2;
        int nextVolume = Math.round(volumeBeforeFade
            - (volumeBeforeFade - targetVolume) * fadeStep / 15f);
        suppressVolumeReset = true;
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nextVolume, 0);
        suppressVolumeReset = false;
        lastFadeVolume = nextVolume;

        if (fadeStep >= 15) {
            finishExpiry();
        } else {
            handler.postDelayed(fadeRunnable, FADE_DURATION_MS / 15L);
        }
    }

    private void finishExpiry() {
        MediaSessionAccessService.pauseAll(this);
        lastObservedMediaActive = false;
        suppressVolumeReset = true;
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeBeforeFade, 0);
        suppressVolumeReset = false;
        active = false;
        fading = false;
        preferences.edit().putBoolean(KEY_ACTIVE, false).apply();
        cancelTimerCallbacks();
        updateNotification("Media paused. Timer is off.");
    }

    private void cancelFadeForVolumeChange() {
        fading = false;
        cancelTimerCallbacks();
        if (isValidDuration(configuredDurationMinutes)) {
            startTimer(configuredDurationMinutes);
        } else {
            updateNotification();
        }
    }

    private void cancelTimerCallbacks() {
        if (expiryRunnable != null) {
            handler.removeCallbacks(expiryRunnable);
        }
        if (notificationRunnable != null) {
            handler.removeCallbacks(notificationRunnable);
        }
        if (fadeRunnable != null) {
            handler.removeCallbacks(fadeRunnable);
        }
    }

    private void scheduleInputPoll() {
        inputPollRunnable = () -> {
            pollInputs();
            scheduleInputPoll();
        };
        handler.postDelayed(inputPollRunnable, INPUT_POLL_INTERVAL_MS);
    }

    private void pollInputs() {
        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        boolean mediaActive = audioManager.isMusicActive();
        boolean volumeChanged = currentVolume != lastObservedVolume;
        boolean playbackStarted = mediaActive && !lastObservedMediaActive;
        lastObservedVolume = currentVolume;
        lastObservedMediaActive = mediaActive;

        if (volumeChanged && !suppressVolumeReset) {
            resetTimerForVolumeChange();
        }
        if (playbackStarted) {
            startTimerFromConfiguredDuration();
        }
    }

    private Notification buildNotification() {
        return buildNotification(null);
    }

    private Notification buildNotification(String statusOverride) {
        String title = active && !fading ? "Sleep timer: " + formatRemaining() : "Sleep timer is off";
        String text = statusOverride != null
                ? statusOverride
                : active && !fading ? "Volume changes restart the timer." : "Reply with minutes to start the timer.";
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setContentIntent(contentIntent());

        if (active && !fading) {
            builder.addAction(new Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                    "Turn off",
                    disableIntent()).build());
        } else {
            String configuredDuration = String.valueOf(configuredDurationMinutes);
            RemoteInput remoteInput = new RemoteInput.Builder(REMOTE_INPUT_KEY)
                .setLabel("Minutes (default: " + configuredDuration + ")")
                .setChoices(new CharSequence[]{configuredDuration})
                    .build();
            Notification.Action action = new Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_input_add),
                "Set duration (" + configuredDuration + "m)",
                    durationIntent()).addRemoteInput(remoteInput).build();
            builder.addAction(action);
        }
        if (!hasNotificationAccess()) {
            builder.addAction(new Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_manage),
                    "Allow media control",
                    notificationAccessIntent()).build());
        }
        return builder.build();
    }

    private boolean hasNotificationAccess() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        return manager != null && manager.isNotificationListenerAccessGranted(
                new ComponentName(this, MediaSessionAccessService.class));
    }

    private PendingIntent notificationAccessIntent() {
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        return PendingIntent.getActivity(this, 5, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void updateNotification() {
        updateNotification(null);
    }

    private void updateNotification(String status) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, buildNotification(status));
    }

    private PendingIntent disableIntent() {
        Intent intent = new Intent(this, SleepTimerService.class).setAction(ACTION_DISABLE);
        return PendingIntent.getService(this, 2, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent durationIntent() {
        Intent intent = new Intent(this, SleepTimerService.class).setAction(ACTION_SET_DURATION);
        return PendingIntent.getService(this, 3, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
    }

    private PendingIntent contentIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        return PendingIntent.getActivity(this, 4, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private String formatRemaining() {
        long remaining = Math.max(0L, timerEndsAt - System.currentTimeMillis());
        long totalMinutes = (remaining + 59_999L) / 60_000L;
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        if (hours > 0L) {
            return String.format(Locale.US, "%dh %02dm remaining", hours, minutes);
        }
        return String.format(Locale.US, "%dm remaining", minutes);
    }

    private void createNotificationChannel() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Sleep timer", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Auto Sleep Droid timer status and controls");
        manager.createNotificationChannel(channel);
    }

    @Override
    public void onDestroy() {
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
