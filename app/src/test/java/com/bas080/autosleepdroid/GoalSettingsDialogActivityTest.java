package com.bas080.autosleepdroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
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
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowToast;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34})
public class GoalSettingsDialogActivityTest {

    private Context context;
    private SharedPreferences preferences;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        preferences = context.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
    }

    @Test
    public void testInvalidMinSleepInputShowsToastAndDoesNotPersist() {
        preferences.edit()
                .putInt("min_sleep_duration_minutes", 450)
                .putBoolean("wake_up_goal_enabled", false)
                .commit();

        ActivityController<GoalSettingsDialogActivity> controller =
                Robolectric.buildActivity(GoalSettingsDialogActivity.class);
        GoalSettingsDialogActivity activity = controller.create().start().resume().get();
        assertNotNull(activity);

        android.app.AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);

        List<EditText> editTexts = new ArrayList<>();
        if (dialog.getWindow() != null) {
            findViewsOfType(dialog.getWindow().getDecorView(), EditText.class, editTexts);
        }
        assertFalse(editTexts.isEmpty());
        EditText inputMinSleep = editTexts.get(0);

        inputMinSleep.setText("abc");

        Button okButton = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        assertNotNull(okButton);
        okButton.performClick();

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertEquals(450, preferences.getInt("min_sleep_duration_minutes", -1));
        assertFalse(preferences.getBoolean("wake_up_goal_enabled", false));
        assertEquals(activity.getString(R.string.toast_duration_invalid), ShadowToast.getTextOfLatestToast());
        assertTrue(activity.isFinishing());
    }

    @Test
    public void testValidMinSleepInputPersistsAndSetsGoal() {
        ActivityController<GoalSettingsDialogActivity> controller =
                Robolectric.buildActivity(GoalSettingsDialogActivity.class);
        GoalSettingsDialogActivity activity = controller.create().start().resume().get();
        assertNotNull(activity);

        android.app.AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);

        List<EditText> editTexts = new ArrayList<>();
        if (dialog.getWindow() != null) {
            findViewsOfType(dialog.getWindow().getDecorView(), EditText.class, editTexts);
        }
        assertFalse(editTexts.isEmpty());
        EditText inputMinSleep = editTexts.get(0);

        inputMinSleep.setText("8h");

        Button okButton = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        assertNotNull(okButton);
        okButton.performClick();

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertEquals(480, preferences.getInt("min_sleep_duration_minutes", -1));
        assertTrue(preferences.getBoolean("wake_up_goal_enabled", false));
        assertTrue(activity.isFinishing());
    }

    private <T extends View> void findViewsOfType(View root, Class<T> clazz, List<T> outList) {
        if (clazz.isInstance(root)) {
            outList.add(clazz.cast(root));
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                findViewsOfType(group.getChildAt(i), clazz, outList);
            }
        }
    }
}
