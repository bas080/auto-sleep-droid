package com.bas080.autosleepdroid

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlarmAudioUtilsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testConfigureAlarmAudioAttributesNullRingtoneHandledGracefully() {
        AlarmAudioUtils.configureAlarmAudioAttributes(null)
    }

    @Test
    fun testConfigureAlarmAudioAttributesConfiguresAlarmUsageToBypassDnd() {
        val constructor = Ringtone::class.java.getDeclaredConstructor(Context::class.java, Boolean::class.javaPrimitiveType)
        constructor.isAccessible = true
        val mockRingtone = constructor.newInstance(context, false)

        AlarmAudioUtils.configureAlarmAudioAttributes(mockRingtone)

        if (android.os.Build.VERSION.SDK_INT >= 21) {
            val attributes = mockRingtone.audioAttributes
            assertNotNull("AudioAttributes must be configured on Ringtone", attributes)
            assertEquals("AudioAttributes usage must be USAGE_ALARM so Android Do Not Disturb does not block alarm sound",
                AudioAttributes.USAGE_ALARM, attributes.usage)
            assertEquals("AudioAttributes content type must be CONTENT_TYPE_SONIFICATION",
                AudioAttributes.CONTENT_TYPE_SONIFICATION, attributes.contentType)
        } else {
            @Suppress("DEPRECATION")
            assertEquals("Stream type must be STREAM_ALARM",
                AudioManager.STREAM_ALARM, mockRingtone.streamType)
        }
    }
}
