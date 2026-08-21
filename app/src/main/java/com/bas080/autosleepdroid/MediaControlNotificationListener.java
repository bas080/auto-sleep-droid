package com.bas080.autosleepdroid;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.service.notification.NotificationListenerService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MediaControlNotificationListener extends NotificationListenerService {
    private MediaSessionManager sessionManager;
    private final Map<String, MediaController> sessions = new HashMap<>();
    private final Map<String, MediaController.Callback> callbacks = new HashMap<>();
    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener =
            this::updateSessionCallbacks;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        sessionManager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        if (sessionManager == null) {
            return;
        }
        try {
            ComponentName listenerComponent = new ComponentName(
                    this, MediaControlNotificationListener.class);
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent);
            updateSessionCallbacks(sessionManager.getActiveSessions(listenerComponent));
        } catch (SecurityException ignored) {
            // Notification access is not available.
        }
    }

    @Override
    public void onListenerDisconnected() {
        unregisterSessionCallbacks();
        if (sessionManager != null) {
            sessionManager.removeOnActiveSessionsChangedListener(sessionsListener);
        }
        sessionManager = null;
        super.onListenerDisconnected();
    }

    private void updateSessionCallbacks(List<MediaController> activeSessions) {
        Map<String, MediaController> currentSessions = new HashMap<>();
        for (MediaController controller : activeSessions) {
            currentSessions.put(controller.getSessionToken().toString(), controller);
        }

        for (String token : sessions.keySet().toArray(new String[0])) {
            if (!currentSessions.containsKey(token)) {
                MediaController controller = sessions.remove(token);
                MediaController.Callback callback = callbacks.remove(token);
                if (controller != null && callback != null) {
                    controller.unregisterCallback(callback);
                }
            }
        }

        for (MediaController controller : activeSessions) {
            String token = controller.getSessionToken().toString();
            if (!callbacks.containsKey(token)) {
                MediaController.Callback callback = new MediaController.Callback() {
                    @Override
                    public void onPlaybackStateChanged(PlaybackState state) {
                        if (isPlaying(state)) {
                            notifyTimerOfPlayback();
                        }
                    }
                };
                sessions.put(token, controller);
                callbacks.put(token, callback);
                controller.registerCallback(callback);
            }
            if (isPlaying(controller.getPlaybackState())) {
                notifyTimerOfPlayback();
            }
        }
    }

    private void unregisterSessionCallbacks() {
        for (String token : sessions.keySet()) {
            MediaController controller = sessions.get(token);
            MediaController.Callback callback = callbacks.get(token);
            if (controller != null && callback != null) {
                controller.unregisterCallback(callback);
            }
        }
        sessions.clear();
        callbacks.clear();
    }

    private boolean isPlaying(PlaybackState state) {
        return state != null && state.getState() == PlaybackState.STATE_PLAYING;
    }

    private void notifyTimerOfPlayback() {
        Intent intent = new Intent(this, SleepTimerService.class)
                .setAction(SleepTimerService.ACTION_MEDIA_PLAYING);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    public static void pauseAll(Context context) {
        MediaSessionManager sessionManager =
                (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        if (sessionManager == null) {
            return;
        }

        try {
            ComponentName listener = new ComponentName(context, MediaControlNotificationListener.class);
            List<MediaController> sessions = sessionManager.getActiveSessions(listener);
            for (MediaController controller : sessions) {
                if (controller.getTransportControls() != null) {
                    controller.getTransportControls().pause();
                }
            }
        } catch (SecurityException ignored) {
            // Notification access has not been granted yet.
        }
    }
}
