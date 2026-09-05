package com.bas080.autosleepdroid;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.Button;
import android.widget.NumberPicker;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;

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

        AlertDialog dialog = activity.getAlertDialog();
        assertNotNull(dialog);

        List<DurationInputView> list = new ArrayList<>();
        findViewsOfType(dialog.getWindow().getDecorView(), DurationInputView.class, list);
        assertEquals(1, list.size());
        DurationInputView inputNapDuration = list.get(0);

        NumberPicker pickerHours = inputNapDuration.getHoursPicker();
        NumberPicker pickerMinutes = inputNapDuration.getMinutesPicker();

        assertNotNull(pickerHours);
        assertNotNull(pickerMinutes);
        assertEquals(0, pickerHours.getValue());
        assertEquals(45, pickerMinutes.getValue());
    }

    @Test
    public void testStartNapButtonPersistsDurationAndFinishes() {
        ActivityController<NapDialogActivity> controller = Robolectric.buildActivity(NapDialogActivity.class);
        NapDialogActivity activity = controller.create().get();

        AlertDialog dialog = activity.getAlertDialog();
        assertNotNull(dialog);

        List<DurationInputView> list = new ArrayList<>();
        findViewsOfType(dialog.getWindow().getDecorView(), DurationInputView.class, list);
        assertEquals(1, list.size());
        DurationInputView inputNapDuration = list.get(0);

        NumberPicker pickerHours = inputNapDuration.getHoursPicker();
        NumberPicker pickerMinutes = inputNapDuration.getMinutesPicker();
        Button btnStart = dialog.getButton(DialogInterface.BUTTON_POSITIVE);

        pickerHours.setValue(1);
        pickerMinutes.setValue(15);

        btnStart.performClick();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertEquals(75, preferences.getInt("nap_duration_minutes", -1));
        assertTrue(activity.isFinishing());
    }

    @Test
    public void testCancelButtonFinishesActivity() {
        ActivityController<NapDialogActivity> controller = Robolectric.buildActivity(NapDialogActivity.class);
        NapDialogActivity activity = controller.create().get();

        AlertDialog dialog = activity.getAlertDialog();
        assertNotNull(dialog);

        Button btnCancel = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
        btnCancel.performClick();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertTrue(activity.isFinishing());
    }

    private <T extends View> void findViewsOfType(View root, Class<T> clazz, List<T> outList) {
        if (clazz.isInstance(root)) {
            outList.add(clazz.cast(root));
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                findViewsOfType(group.getChildAt(i), clazz, outList);
            }
        }
    }
}
