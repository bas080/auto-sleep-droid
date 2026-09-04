package com.bas080.autosleepdroid;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34})
public class AlarmAudioUtilsTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void testConfigureAlarmAudioAttributesNullRingtoneHandledGracefully() {
        // Should not throw any exception when given a null Ringtone
        AlarmAudioUtils.configureAlarmAudioAttributes(null);
    }

    @Test
    public void testConfigureAlarmAudioAttributesConfiguresAlarmUsageToBypassDnd() throws Exception {
        java.lang.reflect.Constructor<Ringtone> constructor =
                Ringtone.class.getDeclaredConstructor(Context.class, boolean.class);
        constructor.setAccessible(true);
        Ringtone mockRingtone = constructor.newInstance(context, false);

        AlarmAudioUtils.configureAlarmAudioAttributes(mockRingtone);

        if (android.os.Build.VERSION.SDK_INT >= 21) {
            AudioAttributes attributes = mockRingtone.getAudioAttributes();
            assertNotNull("AudioAttributes must be configured on Ringtone", attributes);
            assertEquals("AudioAttributes usage must be USAGE_ALARM so Android Do Not Disturb does not block alarm sound",
                    AudioAttributes.USAGE_ALARM, attributes.getUsage());
            assertEquals("AudioAttributes content type must be CONTENT_TYPE_SONIFICATION",
                    AudioAttributes.CONTENT_TYPE_SONIFICATION, attributes.getContentType());
        } else {
            assertEquals("Stream type must be STREAM_ALARM",
                    AudioManager.STREAM_ALARM, mockRingtone.getStreamType());
        }
    }
}
