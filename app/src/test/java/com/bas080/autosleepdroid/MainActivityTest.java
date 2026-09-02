package com.bas080.autosleepdroid;

import android.Manifest;
import android.app.Application;
import android.content.Intent;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.robolectric.shadows.ShadowApplication;

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
    public void testManualLinkClickShowsFullScreenView() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().get();
        android.widget.Button btnManual = activity.findViewById(R.id.btn_manual);
        assertNotNull(btnManual);

        android.view.View manualOverlay = activity.findViewById(R.id.manual_overlay_container);
        android.view.View mainContent = activity.findViewById(R.id.main_content_container);
        assertEquals(android.view.View.GONE, manualOverlay.getVisibility());
        assertEquals(android.view.View.VISIBLE, mainContent.getVisibility());

        btnManual.performClick();

        assertEquals(android.view.View.VISIBLE, manualOverlay.getVisibility());
        assertEquals(android.view.View.GONE, mainContent.getVisibility());

        android.widget.Button btnBack = activity.findViewById(R.id.btn_manual_back);
        assertNotNull(btnBack);
        btnBack.performClick();

        assertEquals(android.view.View.GONE, manualOverlay.getVisibility());
        assertEquals(android.view.View.VISIBLE, mainContent.getVisibility());
    }

    @Test
    public void testLogsLinkClickShowsFullScreenView() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().get();
        android.widget.Button btnLogs = activity.findViewById(R.id.btn_logs);
        assertNotNull(btnLogs);

        android.view.View logsOverlay = activity.findViewById(R.id.logs_overlay_container);
        android.view.View mainContent = activity.findViewById(R.id.main_content_container);
        assertEquals(android.view.View.GONE, logsOverlay.getVisibility());
        assertEquals(android.view.View.VISIBLE, mainContent.getVisibility());

        btnLogs.performClick();

        assertEquals(android.view.View.VISIBLE, logsOverlay.getVisibility());
        assertEquals(android.view.View.GONE, mainContent.getVisibility());

        activity.onBackPressed();

        assertEquals(android.view.View.GONE, logsOverlay.getVisibility());
        assertEquals(android.view.View.VISIBLE, mainContent.getVisibility());
    }

    @Test
    public void testFeedbackLinkClick() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().get();
        android.widget.Button btnIssues = activity.findViewById(R.id.btn_issues);
        assertNotNull(btnIssues);
        assertEquals(activity.getString(R.string.link_feedback), btnIssues.getText().toString());

        btnIssues.performClick();

        Intent intent = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(intent);
        assertEquals(Intent.ACTION_VIEW, intent.getAction());
        assertEquals("https://github.com/bas080/auto-sleep-droid/issues", intent.getDataString());
    }

    @Test
    public void testDonateLinkClick() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().get();
        activity.findViewById(R.id.btn_donate).performClick();

        Intent intent = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(intent);
        assertEquals(Intent.ACTION_VIEW, intent.getAction());
        assertEquals("https://liberapay.com/bas080", intent.getDataString());
    }

    @Test
    public void testExportButtonClickLaunchesShareIntent() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().get();
        activity.findViewById(R.id.btn_export).performClick();

        Intent chooserIntent = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(chooserIntent);
        assertEquals(Intent.ACTION_CHOOSER, chooserIntent.getAction());

        Intent sendIntent = chooserIntent.getParcelableExtra(Intent.EXTRA_INTENT);
        assertNotNull(sendIntent);
        assertEquals(Intent.ACTION_SEND, sendIntent.getAction());
        assertTrue(sendIntent.getStringExtra(Intent.EXTRA_TEXT).contains("\"version\":1"));
    }

    @Test
    public void testImportButtonClickShowsDialogAndImportsValidJSON() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().get();
        activity.findViewById(R.id.btn_import).performClick();

        android.app.AlertDialog dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);

        android.widget.EditText editText = null;
        if (dialog.getWindow() != null) {
            java.util.ArrayList<android.widget.EditText> list = new java.util.ArrayList<>();
            findViewsOfType(dialog.getWindow().getDecorView(), android.widget.EditText.class, list);
            if (!list.isEmpty()) {
                editText = list.get(0);
            }
        }
        assertNotNull(editText);

        String json = "{\"version\":1,\"duration_minutes\":45,\"active\":true,\"wake_up_goal_enabled\":true,\"wake_up_goal_hour\":7,\"wake_up_goal_minute\":15,\"min_sleep_duration_minutes\":480}";
        editText.setText(json);

        android.widget.Button importBtn = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        assertNotNull(importBtn);
        importBtn.performClick();

        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        android.content.SharedPreferences prefs = activity.getSharedPreferences("sleep_timer", android.content.Context.MODE_PRIVATE);
        assertEquals(45, prefs.getInt("duration_minutes", -1));
        assertTrue(prefs.getBoolean("active", false));
        assertTrue(prefs.getBoolean("wake_up_goal_enabled", false));
        assertEquals(7, prefs.getInt("wake_up_goal_hour", -1));
        assertEquals(15, prefs.getInt("wake_up_goal_minute", -1));
        assertEquals(480, prefs.getInt("min_sleep_duration_minutes", -1));
    }

    private <T extends android.view.View> void findViewsOfType(android.view.View root, Class<T> clazz, java.util.List<T> outList) {
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

        // Simulate returning from Settings (onResume)
        controller.resume();

        assertFalse(activity.isFinishing());
        assertNotNull(activity.findViewById(R.id.switch_enable_timer));
    }

    @Test
    public void testTargetTimeButtonClickOpensDialogAndSavesTime() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().resume().get();

        android.widget.Button btnTargetTime = activity.findViewById(R.id.btn_target_time);
        assertNotNull(btnTargetTime);
        assertFalse(btnTargetTime.getText().toString().isEmpty());

        btnTargetTime.performClick();

        android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertTrue(dialog instanceof android.app.TimePickerDialog);

        android.app.TimePickerDialog timePickerDialog = (android.app.TimePickerDialog) dialog;
        timePickerDialog.updateTime(7, 45);

        android.widget.Button okButton = timePickerDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        assertNotNull(okButton);
        okButton.performClick();

        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        android.content.SharedPreferences prefs = activity.getSharedPreferences("sleep_timer", android.content.Context.MODE_PRIVATE);
        assertEquals(7, prefs.getInt("wake_up_goal_hour", -1));
        assertEquals(45, prefs.getInt("wake_up_goal_minute", -1));
    }

    @Test
    public void testConfigControlsUpdatesSharedPreferences() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().resume().get();

        android.widget.Switch switchEnable = activity.findViewById(R.id.switch_enable_timer);
        android.widget.EditText inputDuration = activity.findViewById(R.id.input_duration);
        android.widget.Switch switchShowNotif = activity.findViewById(R.id.switch_show_notification);
        android.widget.Switch switchGoal = activity.findViewById(R.id.switch_enable_goal);

        assertNotNull(switchEnable);
        assertNotNull(inputDuration);
        assertNotNull(switchShowNotif);
        assertNotNull(switchGoal);

        assertFalse(switchShowNotif.isChecked());

        switchEnable.setChecked(false);
        inputDuration.setText("45m");
        switchShowNotif.setChecked(true);
        switchGoal.setChecked(true);

        android.content.SharedPreferences prefs = activity.getSharedPreferences("sleep_timer", android.content.Context.MODE_PRIVATE);
        assertFalse(prefs.getBoolean("active", true));
        assertEquals(45, prefs.getInt("duration_minutes", -1));
        assertTrue(prefs.getBoolean("show_notification", false));
        assertTrue(prefs.getBoolean("wake_up_goal_enabled", false));
    }

    @Test
    public void testShowNotificationToggleRequestsPermissionWhenNotGranted() {
        Application application = ApplicationProvider.getApplicationContext();
        Shadows.shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);

        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().resume().get();

        android.widget.Switch switchShowNotif = activity.findViewById(R.id.switch_show_notification);
        assertNotNull(switchShowNotif);
        assertFalse(switchShowNotif.isChecked());

        switchShowNotif.setChecked(true);

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
    public void testInvalidDurationInputOnFocusLossShowsToastAndRevertsText() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().resume().get();

        android.widget.EditText inputDuration = activity.findViewById(R.id.input_duration);
        assertNotNull(inputDuration);

        // Initially 20m
        assertEquals("20m", inputDuration.getText().toString());

        // Change text to invalid input
        inputDuration.setText("1h 20x");

        // Trigger focus loss
        if (inputDuration.getOnFocusChangeListener() != null) {
            inputDuration.getOnFocusChangeListener().onFocusChange(inputDuration, false);
        }

        android.content.SharedPreferences prefs = activity.getSharedPreferences("sleep_timer", android.content.Context.MODE_PRIVATE);
        assertEquals(20, prefs.getInt("duration_minutes", 20));
        assertEquals("20m", inputDuration.getText().toString());
        assertEquals(activity.getString(R.string.toast_duration_invalid), org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void testInvalidMinSleepInputOnFocusLossShowsToastAndRevertsText() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().resume().get();

        android.widget.EditText inputMinSleep = activity.findViewById(R.id.input_min_sleep);
        assertNotNull(inputMinSleep);

        // Initially 7h 30m
        assertEquals("7h 30m", inputMinSleep.getText().toString());

        // Change text to invalid input
        inputMinSleep.setText("0");

        // Trigger focus loss
        if (inputMinSleep.getOnFocusChangeListener() != null) {
            inputMinSleep.getOnFocusChangeListener().onFocusChange(inputMinSleep, false);
        }

        android.content.SharedPreferences prefs = activity.getSharedPreferences("sleep_timer", android.content.Context.MODE_PRIVATE);
        assertEquals(450, prefs.getInt("min_sleep_duration_minutes", 450));
        assertEquals("7h 30m", inputMinSleep.getText().toString());
        assertEquals(activity.getString(R.string.toast_duration_invalid), org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void testOnResumeStartsRedrawServiceIntent() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        controller.create();

        Application app = ApplicationProvider.getApplicationContext();
        ShadowApplication shadowApp = Shadows.shadowOf(app);

        // Consume service intents queued during onCreate
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
}
