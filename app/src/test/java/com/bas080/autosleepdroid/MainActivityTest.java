package com.bas080.autosleepdroid;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Application;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowApplication;
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
public class MainActivityTest {

    @Before
    public void setUp() {
        Application application = ApplicationProvider.getApplicationContext();
        Shadows.shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        EventLogger.clear(application);
    }

    @Test
    public void testActivityCreation() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().get();
        assertNotNull(activity);
    }

    @Test
    public void testVersionDisplay() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().get();
        TextView versionView = activity.findViewById(R.id.app_version_text);
        assertNotNull(versionView);
        assertTrue(versionView.getText().toString().contains(BuildConfig.VERSION_NAME));
    }

    @Test
    public void testInlineEventLogs() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().resume().get();

        EventLogger.log(activity, EventLogger.LEVEL_HIGH, "Test event log entry");
        TextView eventLogText = activity.findViewById(R.id.event_log_text);
        assertNotNull(eventLogText);
        assertTrue(eventLogText.getText().toString().contains("Test event log entry"));
        assertTrue("Expected event_log_text to be selectable", eventLogText.isTextSelectable());
    }

    @Test
    public void testLinksDialogManualShowsFullScreenView() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().get();
        View btnLinks = activity.findViewById(R.id.btn_links);
        assertNotNull(btnLinks);

        View manualOverlay = activity.findViewById(R.id.manual_overlay_container);
        View mainContent = activity.findViewById(R.id.main_content_container);
        assertEquals(View.GONE, manualOverlay.getVisibility());
        assertEquals(View.VISIBLE, mainContent.getVisibility());

        btnLinks.performClick();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);
        Shadows.shadowOf(dialog).clickOnItem(0);

        assertEquals(View.VISIBLE, manualOverlay.getVisibility());
        assertEquals(View.GONE, mainContent.getVisibility());

        android.widget.Button btnBack = activity.findViewById(R.id.btn_manual_back);
        assertNotNull(btnBack);
        btnBack.performClick();

        assertEquals(View.GONE, manualOverlay.getVisibility());
        assertEquals(View.VISIBLE, mainContent.getVisibility());
    }

    @Test
    public void testLinksDialogLogsShowsFullScreenView() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().get();
        View btnLinks = activity.findViewById(R.id.btn_links);
        assertNotNull(btnLinks);

        View logsOverlay = activity.findViewById(R.id.logs_overlay_container);
        View mainContent = activity.findViewById(R.id.main_content_container);
        assertEquals(View.GONE, logsOverlay.getVisibility());
        assertEquals(View.VISIBLE, mainContent.getVisibility());

        btnLinks.performClick();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);
        Shadows.shadowOf(dialog).clickOnItem(1);

        assertEquals(View.VISIBLE, logsOverlay.getVisibility());
        assertEquals(View.GONE, mainContent.getVisibility());

        activity.onBackPressed();

        assertEquals(View.GONE, logsOverlay.getVisibility());
        assertEquals(View.VISIBLE, mainContent.getVisibility());
    }

    @Test
    public void testLinksDialogFeedbackLaunchesIntent() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().get();
        View btnLinks = activity.findViewById(R.id.btn_links);
        assertNotNull(btnLinks);

        btnLinks.performClick();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);
        Shadows.shadowOf(dialog).clickOnItem(2);

        Intent chooserIntent = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(chooserIntent);
        assertEquals(Intent.ACTION_CHOOSER, chooserIntent.getAction());

        Intent sendIntent = chooserIntent.getParcelableExtra(Intent.EXTRA_INTENT);
        assertNotNull(sendIntent);
        assertEquals(Intent.ACTION_SENDTO, sendIntent.getAction());
        assertEquals("mailto:bas080@hotmail.com", sendIntent.getDataString());
        assertTrue(sendIntent.getStringExtra(Intent.EXTRA_SUBJECT).contains("Auto Sleep Droid Feedback"));
    }

    @Test
    public void testLinksDialogDonateLaunchesIntent() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().get();
        View btnLinks = activity.findViewById(R.id.btn_links);
        assertNotNull(btnLinks);

        btnLinks.performClick();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);
        Shadows.shadowOf(dialog).clickOnItem(3);

        Intent intent = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(intent);
        assertEquals(Intent.ACTION_VIEW, intent.getAction());
        assertEquals("https://liberapay.com/bas080", intent.getDataString());
    }

    @Test
    public void testLinksDialogExportLaunchesShareIntent() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().get();
        View btnLinks = activity.findViewById(R.id.btn_links);
        assertNotNull(btnLinks);

        btnLinks.performClick();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);
        Shadows.shadowOf(dialog).clickOnItem(4);

        Intent chooserIntent = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(chooserIntent);
        assertEquals(Intent.ACTION_CHOOSER, chooserIntent.getAction());

        Intent sendIntent = chooserIntent.getParcelableExtra(Intent.EXTRA_INTENT);
        assertNotNull(sendIntent);
        assertEquals(Intent.ACTION_SEND, sendIntent.getAction());
        assertTrue(sendIntent.getStringExtra(Intent.EXTRA_TEXT).contains("\"version\":1"));
    }

    @Test
    public void testLinksDialogImportShowsImportDialogAndImportsJSON() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().get();
        View btnLinks = activity.findViewById(R.id.btn_links);
        assertNotNull(btnLinks);

        btnLinks.performClick();
        AlertDialog linksDialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(linksDialog);
        Shadows.shadowOf(linksDialog).clickOnItem(5);

        AlertDialog importDialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(importDialog);

        android.widget.EditText editText = null;
        if (importDialog.getWindow() != null) {
            List<android.widget.EditText> list = new ArrayList<>();
            findViewsOfType(importDialog.getWindow().getDecorView(), android.widget.EditText.class, list);
            if (!list.isEmpty()) {
                editText = list.get(0);
            }
        }
        assertNotNull(editText);

        String json = "{\"version\":1,\"duration_minutes\":45,\"active\":true,\"wake_up_goal_enabled\":true,\"wake_up_goal_hour\":7,\"wake_up_goal_minute\":15,\"min_sleep_duration_minutes\":480}";
        editText.setText(json);

        android.widget.Button importBtn = importDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        assertNotNull(importBtn);
        importBtn.performClick();

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        android.content.SharedPreferences prefs = activity.getSharedPreferences("sleep_timer", android.content.Context.MODE_PRIVATE);
        assertEquals(45, prefs.getInt("duration_minutes", -1));
        assertTrue(prefs.getBoolean("active", false));
        assertTrue(prefs.getBoolean("wake_up_goal_enabled", false));
        assertEquals(7, prefs.getInt("wake_up_goal_hour", -1));
        assertEquals(15, prefs.getInt("wake_up_goal_minute", -1));
        assertEquals(480, prefs.getInt("min_sleep_duration_minutes", -1));
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

    @Test
    public void testResumeRemainsOpen() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().get();

        controller.resume();

        assertFalse(activity.isFinishing());
        assertNotNull(activity.findViewById(R.id.switch_enable_timer));
    }

    @Test
    public void testTargetTimeButtonClickOpensDialogAndSavesTime() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().resume().get();

        View btnTargetTime = activity.findViewById(R.id.btn_target_time);
        TextView textTargetTimeValue = activity.findViewById(R.id.text_target_time_value);
        assertNotNull(btnTargetTime);
        assertNotNull(textTargetTimeValue);
        assertFalse(textTargetTimeValue.getText().toString().isEmpty());

        btnTargetTime.performClick();

        android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertTrue(dialog instanceof android.app.TimePickerDialog);

        android.app.TimePickerDialog timePickerDialog = (android.app.TimePickerDialog) dialog;
        timePickerDialog.updateTime(7, 45);

        android.widget.Button okButton = timePickerDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        assertNotNull(okButton);
        okButton.performClick();

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        android.content.SharedPreferences prefs = activity.getSharedPreferences("sleep_timer", android.content.Context.MODE_PRIVATE);
        assertEquals(7, prefs.getInt("wake_up_goal_hour", -1));
        assertEquals(45, prefs.getInt("wake_up_goal_minute", -1));
    }

    @Test
    public void testConfigControlsUpdatesSharedPreferences() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().resume().get();

        android.widget.Switch switchEnable = activity.findViewById(R.id.switch_enable_timer);
        View inputDuration = activity.findViewById(R.id.input_duration);
        android.widget.Switch switchGoal = activity.findViewById(R.id.switch_enable_goal);

        assertNotNull(switchEnable);
        assertNotNull(inputDuration);
        assertNotNull(switchGoal);

        switchEnable.setChecked(false);

        inputDuration.performClick();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);
        List<DurationInputView> list = new ArrayList<>();
        if (dialog.getWindow() != null) {
            findViewsOfType(dialog.getWindow().getDecorView(), DurationInputView.class, list);
        }
        assertFalse(list.isEmpty());
        list.get(0).setTotalMinutes(45);
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        switchGoal.setChecked(true);

        android.content.SharedPreferences prefs = activity.getSharedPreferences("sleep_timer", android.content.Context.MODE_PRIVATE);
        assertFalse(prefs.getBoolean("active", true));
        assertEquals(45, prefs.getInt("duration_minutes", -1));
        assertTrue(prefs.getBoolean("wake_up_goal_enabled", false));
    }

    @Test
    public void testStartupRequestsNotificationPermissionWhenNotGranted() {
        Application application = ApplicationProvider.getApplicationContext();
        Shadows.shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);

        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().resume().get();

        org.robolectric.shadows.ShadowActivity shadowActivity = Shadows.shadowOf(activity);
        org.robolectric.shadows.ShadowActivity.PermissionsRequest request = shadowActivity.getLastRequestedPermission();
        assertNotNull(request);
        assertEquals(Manifest.permission.POST_NOTIFICATIONS, request.requestedPermissions[0]);
    }

    @Test
    public void testFormatColoredEvent() {
        CharSequence formatted = EventLogger.formatColoredEvent("8/29 14:30:00 \u0002Timer turned on");
        assertNotNull(formatted);
        assertTrue(formatted instanceof android.text.Spanned);
        assertFalse(formatted.toString().contains("\u0002"));

        android.text.Spanned spanned = (android.text.Spanned) formatted;
        android.text.style.ForegroundColorSpan[] colorSpans =
                spanned.getSpans(0, spanned.length(), android.text.style.ForegroundColorSpan.class);
        assertTrue(colorSpans.length >= 2);

        assertEquals(0xFF999999, colorSpans[0].getForegroundColor());
        assertEquals(0xFF000000, colorSpans[1].getForegroundColor());
    }

    @Test
    public void testNewIntentHandling() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().get();
        controller.newIntent(new Intent(ApplicationProvider.getApplicationContext(), MainActivity.class));
        assertNotNull(activity);
    }

    @Test
    public void testInvalidDurationInputShowsToastAndRevertsText() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().resume().get();

        View inputDuration = activity.findViewById(R.id.input_duration);
        TextView textDurationValue = activity.findViewById(R.id.text_duration_value);
        assertNotNull(inputDuration);
        assertNotNull(textDurationValue);

        assertEquals("20m", textDurationValue.getText().toString());

        inputDuration.performClick();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);
        List<DurationInputView> list = new ArrayList<>();
        if (dialog.getWindow() != null) {
            findViewsOfType(dialog.getWindow().getDecorView(), DurationInputView.class, list);
        }
        assertFalse(list.isEmpty());
        list.get(0).getHoursInput().setText("");
        list.get(0).getMinutesInput().setText("");

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        android.content.SharedPreferences prefs = activity.getSharedPreferences("sleep_timer", android.content.Context.MODE_PRIVATE);
        assertEquals(20, prefs.getInt("duration_minutes", 20));
        assertEquals("20m", textDurationValue.getText().toString());
        assertEquals(activity.getString(R.string.toast_duration_invalid), ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void testZeroDurationInputInDialogShowsToastAndRevertsText() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().resume().get();

        View inputDuration = activity.findViewById(R.id.input_duration);
        TextView textDurationValue = activity.findViewById(R.id.text_duration_value);
        assertNotNull(inputDuration);

        inputDuration.performClick();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);
        List<DurationInputView> list = new ArrayList<>();
        if (dialog.getWindow() != null) {
            findViewsOfType(dialog.getWindow().getDecorView(), DurationInputView.class, list);
        }
        assertFalse(list.isEmpty());
        list.get(0).getHoursInput().setText("0");
        list.get(0).getMinutesInput().setText("0");

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        android.content.SharedPreferences prefs = activity.getSharedPreferences("sleep_timer", android.content.Context.MODE_PRIVATE);
        assertEquals(20, prefs.getInt("duration_minutes", 20));
        assertEquals("20m", textDurationValue.getText().toString());
        assertEquals(activity.getString(R.string.toast_duration_invalid), ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void testInvalidMinSleepInputInDialogShowsToastAndRevertsText() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().resume().get();

        View inputMinSleep = activity.findViewById(R.id.input_min_sleep);
        TextView textMinSleepValue = activity.findViewById(R.id.text_min_sleep_value);
        assertNotNull(inputMinSleep);
        assertNotNull(textMinSleepValue);

        assertEquals("7h 30m", textMinSleepValue.getText().toString());

        inputMinSleep.performClick();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);
        List<DurationInputView> list = new ArrayList<>();
        if (dialog.getWindow() != null) {
            findViewsOfType(dialog.getWindow().getDecorView(), DurationInputView.class, list);
        }
        assertFalse(list.isEmpty());
        list.get(0).getHoursInput().setText("0");
        list.get(0).getMinutesInput().setText("0");

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        android.content.SharedPreferences prefs = activity.getSharedPreferences("sleep_timer", android.content.Context.MODE_PRIVATE);
        assertEquals(450, prefs.getInt("min_sleep_duration_minutes", 450));
        assertEquals("7h 30m", textMinSleepValue.getText().toString());
        assertEquals(activity.getString(R.string.toast_duration_invalid), ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void testDurationDialogUpdatesTotalMinutesAndText() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().resume().get();

        View inputDuration = activity.findViewById(R.id.input_duration);
        TextView textDurationValue = activity.findViewById(R.id.text_duration_value);
        assertNotNull(inputDuration);
        assertNotNull(textDurationValue);

        inputDuration.performClick();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);
        List<DurationInputView> list = new ArrayList<>();
        if (dialog.getWindow() != null) {
            findViewsOfType(dialog.getWindow().getDecorView(), DurationInputView.class, list);
        }
        assertFalse(list.isEmpty());
        list.get(0).setTotalMinutes(90);

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        android.content.SharedPreferences prefs = activity.getSharedPreferences("sleep_timer", android.content.Context.MODE_PRIVATE);
        assertEquals(90, prefs.getInt("duration_minutes", -1));
        assertEquals("1h 30m", textDurationValue.getText().toString());
    }

    @Test
    public void testNapButtonDisplaysNapWhenInactiveAndLaunchesDialog() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().resume().get();

        View btnNap = activity.findViewById(R.id.btn_nap);
        TextView textNapStatus = activity.findViewById(R.id.text_nap_status);
        assertNotNull(btnNap);
        assertNotNull(textNapStatus);
        assertEquals("20m", textNapStatus.getText().toString());

        btnNap.performClick();

        Intent startedActivity = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(startedActivity);
        assertEquals(NapDialogActivity.class.getName(), startedActivity.getComponent().getClassName());
    }

    @Test
    public void testNapButtonDisplaysCancelNapWhenActiveAndSendsCancelIntent() {
        android.content.SharedPreferences prefs = ApplicationProvider.getApplicationContext().getSharedPreferences("sleep_timer", android.content.Context.MODE_PRIVATE);
        prefs.edit().putLong("nap_alarm_ends_at", System.currentTimeMillis() + 600000L).commit();

        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().resume().get();

        Application app = ApplicationProvider.getApplicationContext();
        ShadowApplication shadowApp = Shadows.shadowOf(app);
        while (shadowApp.getNextStartedService() != null) {}

        View btnNap = activity.findViewById(R.id.btn_nap);
        TextView textNapStatus = activity.findViewById(R.id.text_nap_status);
        assertNotNull(btnNap);
        assertNotNull(textNapStatus);
        assertEquals(activity.getString(R.string.action_cancel_nap), textNapStatus.getText().toString());

        btnNap.performClick();

        Intent serviceIntent = shadowApp.getNextStartedService();
        assertNotNull(serviceIntent);
        assertEquals(SleepTimerService.ACTION_CANCEL_NAP, serviceIntent.getAction());
    }

    @Test
    public void testOnResumeStartsRedrawServiceIntent() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        controller.create();

        Application app = ApplicationProvider.getApplicationContext();
        ShadowApplication shadowApp = Shadows.shadowOf(app);

        while (shadowApp.getNextStartedService() != null) {}

        controller.resume();

        boolean foundRedrawIntent = false;
        Intent intent;
        while ((intent = shadowApp.getNextStartedService()) != null) {
            if (SleepTimerService.ACTION_REDRAW_NOTIFICATION.equals(intent.getAction())
                    && SleepTimerService.class.getName().equals(intent.getComponent().getClassName())) {
                foundRedrawIntent = true;
                break;
            }
        }
        assertTrue("Expected ACTION_REDRAW_NOTIFICATION intent when MainActivity is resumed", foundRedrawIntent);
    }

    @Test
    public void testInputsDisabledWhenSleepTimerIsDisabledHasReducedAlpha() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().resume().get();

        android.widget.Switch switchEnable = activity.findViewById(R.id.switch_enable_timer);
        View inputDuration = activity.findViewById(R.id.input_duration);
        View rowEnableGoal = activity.findViewById(R.id.row_enable_goal);
        android.widget.Switch switchGoal = activity.findViewById(R.id.switch_enable_goal);
        View btnTargetTime = activity.findViewById(R.id.btn_target_time);
        View inputMinSleep = activity.findViewById(R.id.input_min_sleep);

        switchEnable.setChecked(false);

        assertFalse(inputDuration.isEnabled());
        assertEquals(0.38f, inputDuration.getAlpha(), 0.01f);
        assertFalse(switchGoal.isEnabled());
        assertEquals(0.38f, rowEnableGoal.getAlpha(), 0.01f);
        assertFalse(btnTargetTime.isEnabled());
        assertEquals(0.38f, btnTargetTime.getAlpha(), 0.01f);
        assertFalse(inputMinSleep.isEnabled());
        assertEquals(0.38f, inputMinSleep.getAlpha(), 0.01f);

        switchEnable.setChecked(true);
        assertTrue(inputDuration.isEnabled());
        assertEquals(1.0f, inputDuration.getAlpha(), 0.01f);
        assertEquals(1.0f, rowEnableGoal.getAlpha(), 0.01f);
    }

    @Test
    public void testGoalContainerVisibleAndGoalInputsDisabledWhenGoalIsDisabled() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().resume().get();

        android.widget.Switch switchEnable = activity.findViewById(R.id.switch_enable_timer);
        android.widget.Switch switchGoal = activity.findViewById(R.id.switch_enable_goal);
        View goalContainer = activity.findViewById(R.id.goal_container);
        View btnTargetTime = activity.findViewById(R.id.btn_target_time);
        View inputMinSleep = activity.findViewById(R.id.input_min_sleep);

        switchEnable.setChecked(true);
        switchGoal.setChecked(false);

        assertEquals(View.VISIBLE, goalContainer.getVisibility());
        View inputDuration = activity.findViewById(R.id.input_duration);
        assertTrue(inputDuration.isEnabled());
        assertTrue(switchGoal.isEnabled());
        assertFalse(btnTargetTime.isEnabled());
        assertEquals(0.38f, btnTargetTime.getAlpha(), 0.01f);
        assertFalse(inputMinSleep.isEnabled());
        assertEquals(0.38f, inputMinSleep.getAlpha(), 0.01f);
    }

    @Test
    public void testButtonRowChildViewsAreNotClickableAndParentIsClickable() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().resume().get();

        android.widget.Switch switchGoal = activity.findViewById(R.id.switch_enable_goal);
        switchGoal.setChecked(true);

        int[] rowIds = new int[]{R.id.btn_nap, R.id.input_duration, R.id.btn_target_time, R.id.input_min_sleep, R.id.btn_links};
        for (int rowId : rowIds) {
            View parentRow = activity.findViewById(rowId);
            assertNotNull("Row should exist", parentRow);
            assertTrue("Parent row should be clickable when enabled", parentRow.isClickable());

            if (parentRow instanceof android.view.ViewGroup) {
                android.view.ViewGroup group = (android.view.ViewGroup) parentRow;
                List<View> childList = new ArrayList<>();
                findViewsOfType(group, View.class, childList);
                for (View child : childList) {
                    if (child != parentRow) {
                        assertFalse("Child view inside button row should not be clickable: " + child, child.isClickable());
                        assertFalse("Child view inside button row should not be focusable: " + child, child.isFocusable());
                    }
                }
            }
        }
    }
}
