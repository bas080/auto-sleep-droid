package com.bas080.autosleepdroid;

import android.content.ComponentName;
import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.service.notification.NotificationListenerService;

import java.util.List;

public class MediaSessionAccessService extends NotificationListenerService {
    public static void pauseAll(Context context) {
        MediaSessionManager sessionManager =
                (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        if (sessionManager == null) {
            return;
        }

        try {
            ComponentName listener = new ComponentName(context, MediaSessionAccessService.class);
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
