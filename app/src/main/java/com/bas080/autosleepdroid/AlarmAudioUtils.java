package com.bas080.autosleepdroid;

import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.os.Build;

public class AlarmAudioUtils {

    /**
     * Configures a Ringtone with alarm audio attributes (USAGE_ALARM and CONTENT_TYPE_SONIFICATION on API 21+,
     * or STREAM_ALARM on older API levels) to ensure Android framework treats the audio playback as an alarm
     * and Do Not Disturb (DND) policies do not prevent or silence the alarm sound.
     *
     * @param ringtone the Ringtone instance to configure
     */
    public static void configureAlarmAudioAttributes(Ringtone ringtone) {
        if (ringtone == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 21) {
            ringtone.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
        } else {
            ringtone.setStreamType(AudioManager.STREAM_ALARM);
        }
    }
}
