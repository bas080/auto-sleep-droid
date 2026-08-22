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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;

import java.util.Locale;

public class SleepTimerService extends Service {
    public static final String ACTION_SET_DURATION = "com.bas080.autosleepdroid.SET_DURATION";
    public static final String ACTION_TURN_OFF = "com.bas080.autosleepdroid.TURN_OFF";
    public static final String ACTION_REDRAW_NOTIFICATION = "com.bas080.autosleepdroid.REDRAW_NOTIFICATION";
    public static final String EXTRA_DURATION = "com.bas080.autosleepdroid.DURATION";
    private static final String CHANNEL_ID = "sleep_timer";
    private static final int NOTIFICATION_ID = 1001;
    private static final String PREFERENCES = "sleep_timer";
    private static final String KEY_ENABLED = "active";
    private static final String KEY_DURATION_MINUTES = "duration_minutes";
    private static final String REMOTE_INPUT_KEY = "duration_minutes";
    private static final long FADE_DURATION_MS = 30_000L;
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
    private boolean enabled;
    private boolean active;
    private boolean fading;
    private boolean suppressVolumeReset;
    private int fadeStep;
    private int lastFadeVolume;
    private int lastObservedVolume;
    private boolean lastObservedMediaActive;
    private boolean wasPermissionsPending;

    @Override
    public void onCreate() {
        super.onCreate();
        EventLogger.log(this, "SleepTimerService created");
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        createNotificationChannel();

        if (!hasNotificationAccess()) {
            wasPermissionsPending = true;
            EventLogger.log(this, "Notification access pending");
            startForeground(NOTIFICATION_ID, buildPermissionsPendingNotification());
            scheduleInputPoll();
            return;
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

        EventLogger.log(this, "SleepTimerService state initialized (enabled: " + enabled + ", duration: " + configuredDurationMinutes + "m)");

        startForeground(NOTIFICATION_ID, buildNotification());

        if (enabled && audioManager != null && audioManager.isMusicActive()) {
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

        if (!hasNotificationAccess()) {
            wasPermissionsPending = true;
            startForeground(NOTIFICATION_ID, buildPermissionsPendingNotification());
            return START_STICKY;
        }

        if (wasPermissionsPending) {
            wasPermissionsPending = false;
            initializeStateAndNotification();
        }

        if (intent != null) {
            if (ACTION_TURN_OFF.equals(intent.getAction())) {
                handleTurnOff();
            } else if (ACTION_SET_DURATION.equals(intent.getAction())) {
                handleDurationReply(intent);
            } else if (ACTION_REDRAW_NOTIFICATION.equals(intent.getAction())) {
                updateNotification();
            }
        }
        return START_STICKY;
    }

    private void handleTurnOff() {
        EventLogger.log(this, "Timer turned off");
        enabled = false;
        active = false;
        fading = false;
        cancelTimerCallbacks();
        preferences.edit().putBoolean(KEY_ENABLED, false).apply();
        updateNotification();
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
        preferences.edit().putBoolean(KEY_ENABLED, true).apply();
        scheduleExpiry();
        updateNotification();
    }

    private void resetTimerForVolumeChange() {
        if (!fading && isValidDuration(configuredDurationMinutes)) {
            EventLogger.log(this, "Timer reset due to volume change");
            startTimer(configuredDurationMinutes);
        }
    }

    private void startTimerFromConfiguredDuration() {
        if (isValidDuration(configuredDurationMinutes)) {
            startTimer(configuredDurationMinutes);
        }
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

        EventLogger.log(this, "Fade step " + fadeStep + "/15 (volume: " + nextVolume + ")");

        if (fadeStep >= 15) {
            finishExpiry();
        } else {
            handler.postDelayed(fadeRunnable, FADE_DURATION_MS / 15L);
        }
    }

    private void finishExpiry() {
        EventLogger.log(this, "Timer expired: pausing media and restoring volume to " + volumeBeforeFade);
        MediaSessionAccessService.pauseAll(this);
        if (audioManager != null) {
            lastObservedMediaActive = audioManager.isMusicActive();
            suppressVolumeReset = true;
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeBeforeFade, 0);
            suppressVolumeReset = false;
        }
        active = false;
        fading = false;
        enabled = true;
        preferences.edit().putBoolean(KEY_ENABLED, true).apply();
        cancelTimerCallbacks();
        updateNotification();
    }

    private void cancelFadeForVolumeChange() {
        EventLogger.log(this, "Fade cancelled due to volume change");
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
        if (!hasNotificationAccess()) {
            return;
        }
        if (wasPermissionsPending) {
            wasPermissionsPending = false;
            initializeStateAndNotification();
            return;
        }
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

        if (enabled) {
            if (fading && volumeChanged) {
                cancelFadeForVolumeChange();
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

    private Notification buildPermissionsPendingNotification() {
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.setup_required))
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setContentIntent(permissionsSettingsIntent());
        return builder.build();
    }

    private Notification buildNotification() {
        if (!hasNotificationAccess()) {
            return buildPermissionsPendingNotification();
        }

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
        }

        return builder.build();
    }

    private boolean hasNotificationAccess() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        return manager != null && manager.isNotificationListenerAccessGranted(
                new ComponentName(this, MediaSessionAccessService.class));
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

    private PendingIntent permissionsSettingsIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        return PendingIntent.getActivity(this, 6, intent,
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
    public void onDestroy() {
        EventLogger.log(this, "SleepTimerService destroyed");
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
