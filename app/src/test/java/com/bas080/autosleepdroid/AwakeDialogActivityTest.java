package com.bas080.autosleepdroid;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.widget.Button;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34})
public class AwakeDialogActivityTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void testConfirmAwakeButtonStartsServiceWithAwakeActionAndFinishes() {
        ActivityController<AwakeDialogActivity> controller = Robolectric.buildActivity(AwakeDialogActivity.class);
        AwakeDialogActivity activity = controller.create().get();

        AlertDialog dialog = activity.getAlertDialog();
        assertNotNull(dialog);

        Button btnAwake = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        assertNotNull(btnAwake);

        btnAwake.performClick();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        ShadowApplication shadowApp = Shadows.shadowOf(activity.getApplication());
        Intent startedIntent = shadowApp.getNextStartedService();
        assertNotNull(startedIntent);
        assertEquals(SleepTimerService.ACTION_AWAKE, startedIntent.getAction());
        assertTrue(activity.isFinishing());
    }

    @Test
    public void testCancelButtonFinishesActivityWithoutStartingService() {
        ActivityController<AwakeDialogActivity> controller = Robolectric.buildActivity(AwakeDialogActivity.class);
        AwakeDialogActivity activity = controller.create().get();

        AlertDialog dialog = activity.getAlertDialog();
        assertNotNull(dialog);

        Button btnCancel = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
        assertNotNull(btnCancel);

        btnCancel.performClick();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertTrue(activity.isFinishing());
    }
}
