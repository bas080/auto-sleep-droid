package com.bas080.autosleepdroid;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Button;
import android.widget.EditText;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34})
public class NapDialogActivityTest {

    private Context context;
    private SharedPreferences preferences;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        preferences = context.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
    }

    @Test
    public void testPrefillsPreviousNapDuration() {
        preferences.edit().putInt("nap_duration_minutes", 45).commit();

        ActivityController<NapDialogActivity> controller = Robolectric.buildActivity(NapDialogActivity.class);
        NapDialogActivity activity = controller.create().get();

        EditText inputHours = activity.findViewById(R.id.input_nap_hours);
        EditText inputMinutes = activity.findViewById(R.id.input_nap_minutes);

        assertNotNull(inputHours);
        assertNotNull(inputMinutes);
        assertEquals("", inputHours.getText().toString());
        assertEquals("45", inputMinutes.getText().toString());
    }

    @Test
    public void testStartNapButtonPersistsDurationAndFinishes() {
        ActivityController<NapDialogActivity> controller = Robolectric.buildActivity(NapDialogActivity.class);
        NapDialogActivity activity = controller.create().get();

        EditText inputHours = activity.findViewById(R.id.input_nap_hours);
        EditText inputMinutes = activity.findViewById(R.id.input_nap_minutes);
        Button btnStart = activity.findViewById(R.id.btn_nap_start);

        inputHours.setText("1");
        inputMinutes.setText("15");

        btnStart.performClick();

        assertEquals(75, preferences.getInt("nap_duration_minutes", -1));
        assertTrue(activity.isFinishing());
    }

    @Test
    public void testCancelButtonFinishesActivity() {
        ActivityController<NapDialogActivity> controller = Robolectric.buildActivity(NapDialogActivity.class);
        NapDialogActivity activity = controller.create().get();

        Button btnCancel = activity.findViewById(R.id.btn_nap_cancel);
        btnCancel.performClick();

        assertTrue(activity.isFinishing());
    }
}