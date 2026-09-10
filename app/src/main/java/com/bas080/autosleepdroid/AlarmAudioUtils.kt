package com.bas080.autosleepdroid

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.os.Build

object AlarmAudioUtils {

    /**
     * Configures a Ringtone with alarm audio attributes (USAGE_ALARM and CONTENT_TYPE_SONIFICATION on API 21+,
     * or STREAM_ALARM on older API levels) to ensure Android framework treats the audio playback as an alarm
     * and Do Not Disturb (DND) policies do not prevent or silence the alarm sound.
     *
     * @param ringtone the Ringtone instance to configure
     */
    fun configureAlarmAudioAttributes(ringtone: Ringtone?) {
        if (ringtone == null) {
            return
        }
        ringtone.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}
