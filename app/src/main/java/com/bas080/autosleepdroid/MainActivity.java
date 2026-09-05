package com.bas080.autosleepdroid;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.net.Uri;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.app.TimePickerDialog;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.List;

public class MainActivity extends Activity implements EventLogger.Listener {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 100;

    private View mainContentContainer;
    private View manualOverlayContainer;
    private TextView manualTextContent;
    private View logsOverlayContainer;

    private View headerNap;
    private View headerTimer;
    private View headerAlarm;
    private View headerHealthConnect;
    private View headerAbout;

    private View rowEnableTimer;
    private Switch switchEnableTimer;
    private View inputDuration;
    private TextView textDurationValue;
    private View rowAutoTimer;
    private Switch switchAutoTimer;
    private View rowEnableGoal;
    private Switch switchEnableGoal;
    private View goalContainer;
    private View btnTargetTime;
    private TextView textTargetTimeValue;
    private View btnCurrentWakeTime;
    private TextView textCurrentWakeTimeValue;
    private View inputMinSleep;
    private TextView textMinSleepValue;
    private View rowHealthConnect;
    private Switch switchHealthConnect;
    private View btnNap;
    private TextView textNapStatus;
    private View btnVersion;
    private View btnLinks;
    private ScrollView eventScrollView;
    private TextView eventLogText;

    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean isUpdatingUi = false;
    private boolean isUserInitiatedAutoTimer = false;
    private boolean isUserInitiatedHealthConnect = false;

    private interface OnDurationSavedListener {
        void onSaved(int minutes);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        setupHeaderAndLinks();
        setupConfigControls();

        requestNotificationPermissionOnStartupIfNeeded();
        startTimerService();
        requestExactAlarmPermissionIfNeeded();
        checkAndPromptCrashReport();
    }

    private void checkAndPromptCrashReport() {
        SharedPreferences prefs = getSharedPreferences("crash_reports", MODE_PRIVATE);
        String pendingReport = prefs.getString("pending_crash_report", null);
        if (pendingReport != null) {
            prefs.edit().remove("pending_crash_report").apply();

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.dialog_crash_title);
            builder.setMessage(R.string.dialog_crash_message);
            builder.setPositiveButton(R.string.btn_send_report, (dialog, which) -> sendFeedbackEmail(pendingReport));
            builder.setNegativeButton(R.string.dialog_cancel, (dialog, which) -> dialog.dismiss());
            builder.show();
        }
    }

    private void requestNotificationPermissionOnStartupIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                EventLogger.log(this, EventLogger.LEVEL_LOW, "Requesting notification permission on app startup");
                requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_REQUEST);
            }
        }
    }

    private void bindViews() {
        mainContentContainer = findViewById(R.id.main_content_container);
        manualOverlayContainer = findViewById(R.id.manual_overlay_container);
        manualTextContent = findViewById(R.id.manual_text_content);
        logsOverlayContainer = findViewById(R.id.logs_overlay_container);

        headerNap = findViewById(R.id.header_nap);
        headerTimer = findViewById(R.id.header_timer);
        headerAlarm = findViewById(R.id.header_alarm);
        headerHealthConnect = findViewById(R.id.header_health_connect);
        headerAbout = findViewById(R.id.header_about);

        rowEnableTimer = findViewById(R.id.row_enable_timer);
        switchEnableTimer = findViewById(R.id.switch_enable_timer);
        inputDuration = findViewById(R.id.input_duration);
        textDurationValue = findViewById(R.id.text_duration_value);
        rowAutoTimer = findViewById(R.id.row_auto_timer);
        switchAutoTimer = findViewById(R.id.switch_auto_timer);
        rowEnableGoal = findViewById(R.id.row_enable_goal);
        switchEnableGoal = findViewById(R.id.switch_enable_goal);
        goalContainer = findViewById(R.id.goal_container);
        btnTargetTime = findViewById(R.id.btn_target_time);
        textTargetTimeValue = findViewById(R.id.text_target_time_value);
        btnCurrentWakeTime = findViewById(R.id.btn_current_wake_time);
        textCurrentWakeTimeValue = findViewById(R.id.text_current_wake_time_value);
        inputMinSleep = findViewById(R.id.input_min_sleep);
        textMinSleepValue = findViewById(R.id.text_min_sleep_value);
        rowHealthConnect = findViewById(R.id.row_health_connect);
        switchHealthConnect = findViewById(R.id.switch_health_connect);
        btnNap = findViewById(R.id.btn_nap);
        textNapStatus = findViewById(R.id.text_nap_status);
        btnVersion = findViewById(R.id.btn_version);
        btnLinks = findViewById(R.id.btn_links);
        eventScrollView = findViewById(R.id.event_scroll_view);
        eventLogText = findViewById(R.id.event_log_text);
    }

    private void setupHeaderAndLinks() {
        TextView versionText = findViewById(R.id.app_version_text);
        if (versionText != null) {
            versionText.setText(getString(R.string.version_label, BuildConfig.VERSION_NAME));
        }

        Button btnManualBack = findViewById(R.id.btn_manual_back);
        if (btnManualBack != null) {
            btnManualBack.setOnClickListener(v -> hideOverlays());
        }

        Button btnLogsBack = findViewById(R.id.btn_logs_back);
        if (btnLogsBack != null) {
            btnLogsBack.setOnClickListener(v -> hideOverlays());
        }

        if (btnVersion != null) {
            btnVersion.setOnClickListener(v -> openUrl("https://github.com/bas080/auto-sleep-droid/releases"));
        }

        if (btnLinks != null) {
            btnLinks.setOnClickListener(v -> showLinksDialog());
        }
    }

    private void showLinksDialog() {
        CharSequence[] options = new CharSequence[]{
                getString(R.string.link_manual),
                getString(R.string.link_logs),
                getString(R.string.link_feedback),
                getString(R.string.link_donate),
                getString(R.string.link_export),
                getString(R.string.link_import)
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.label_links);
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    showManualScreen();
                    break;
                case 1:
                    showLogsScreen();
                    break;
                case 2:
                    sendFeedbackEmail();
                    break;
                case 3:
                    openUrl("https://liberapay.com/bas080");
                    break;
                case 4:
                    exportSettings();
                    break;
                case 5:
                    showImportDialog();
                    break;
            }
        });
        builder.setNegativeButton(R.string.dialog_cancel, (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void sendFeedbackEmail() {
        sendFeedbackEmail(null);
    }

    private void sendFeedbackEmail(String crashReport) {
        String subject = "Auto Sleep Droid Feedback (v" + BuildConfig.VERSION_NAME + ")";
        StringBuilder bodyBuilder = new StringBuilder();
        if (crashReport != null && !crashReport.isEmpty()) {
            bodyBuilder.append("Crash Report:\n").append(crashReport).append("\n\n");
        }
        bodyBuilder.append("---\nApp Version: ").append(BuildConfig.VERSION_NAME)
                .append("\nAndroid Version: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")")
                .append("\nDevice: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL);

        String bodyTemplate = bodyBuilder.toString();

        Uri mailtoUri = Uri.parse("mailto:bas080@hotmail.com" +
                "?subject=" + Uri.encode(subject) +
                "&body=" + Uri.encode(bodyTemplate));

        Intent intent = new Intent(Intent.ACTION_SENDTO, mailtoUri);
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, bodyTemplate);

        try {
            startActivity(Intent.createChooser(intent, getString(R.string.link_feedback)));
        } catch (Exception e) {
            EventLogger.log(this, "Failed to launch email client: " + e.getMessage());
            Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show();
        }
    }

    private void showManualScreen() {
        loadManualTextIfNeeded();
        if (manualOverlayContainer != null) {
            manualOverlayContainer.setVisibility(View.VISIBLE);
        }
        if (logsOverlayContainer != null) {
            logsOverlayContainer.setVisibility(View.GONE);
        }
        if (mainContentContainer != null) {
            mainContentContainer.setVisibility(View.GONE);
        }
    }

    private void showLogsScreen() {
        refreshEventLog();
        if (logsOverlayContainer != null) {
            logsOverlayContainer.setVisibility(View.VISIBLE);
        }
        if (manualOverlayContainer != null) {
            manualOverlayContainer.setVisibility(View.GONE);
        }
        if (mainContentContainer != null) {
            mainContentContainer.setVisibility(View.GONE);
        }
    }

    private void hideOverlays() {
        if (manualOverlayContainer != null) {
            manualOverlayContainer.setVisibility(View.GONE);
        }
        if (logsOverlayContainer != null) {
            logsOverlayContainer.setVisibility(View.GONE);
        }
        if (mainContentContainer != null) {
            mainContentContainer.setVisibility(View.VISIBLE);
        }
    }

    private void loadManualTextIfNeeded() {
        if (manualTextContent == null || manualTextContent.getText().length() > 0) {
            return;
        }
        String htmlText = "";
        try (java.io.InputStream is = getAssets().open("manual.html");
             java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            htmlText = sb.toString();
        } catch (java.io.IOException e) {
            EventLogger.log(this, "Failed to load manual: " + e.getMessage());
            return;
        }

        CharSequence formattedText;
        if (Build.VERSION.SDK_INT >= 24) {
            formattedText = android.text.Html.fromHtml(htmlText, android.text.Html.FROM_HTML_MODE_LEGACY);
        } else {
            formattedText = android.text.Html.fromHtml(htmlText);
        }

        manualTextContent.setText(formattedText);
    }

    @Override
    public void onBackPressed() {
        if ((manualOverlayContainer != null && manualOverlayContainer.getVisibility() == View.VISIBLE)
                || (logsOverlayContainer != null && logsOverlayContainer.getVisibility() == View.VISIBLE)) {
            hideOverlays();
            return;
        }
        super.onBackPressed();
    }

    private void showDurationDialog(int titleResId, String prefKey, int defaultMinutes, OnDurationSavedListener listener) {
        showDurationDialog(titleResId, prefKey, defaultMinutes, 0, 24, 1, listener);
    }

    private void showDurationDialog(int titleResId, String prefKey, int defaultMinutes, int minHours, int maxHours, int minuteStep, OnDurationSavedListener listener) {
        SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
        int currentMinutes = prefs.getInt(prefKey, defaultMinutes);

        final DurationInputView durationInputView = new DurationInputView(this);
        durationInputView.configure(minHours, maxHours, minuteStep);
        durationInputView.setPadding(48, 24, 48, 24);
        durationInputView.setTotalMinutes(currentMinutes);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(titleResId);
        builder.setView(durationInputView);
        builder.setPositiveButton(R.string.dialog_ok, (dialog, which) -> {
            int minutes = durationInputView.getTotalMinutes();
            if (minutes > 0) {
                prefs.edit().putInt(prefKey, minutes).apply();
                if (listener != null) {
                    listener.onSaved(minutes);
                }
                redrawNotification();
            } else {
                Toast.makeText(MainActivity.this, R.string.toast_duration_invalid, Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(R.string.dialog_cancel, (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void setupConfigControls() {
        if (rowEnableTimer != null && switchEnableTimer != null) {
            rowEnableTimer.setOnClickListener(v -> {
                switchEnableTimer.setPressed(true);
                switchEnableTimer.toggle();
                switchEnableTimer.setPressed(false);
            });
        }

        if (switchEnableTimer != null) {
            switchEnableTimer.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isUpdatingUi) return;
                SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
                prefs.edit().putBoolean("active", isChecked).apply();
                boolean goalEnabled = prefs.getBoolean("wake_up_goal_enabled", false);
                updateInputEnabledStates(isChecked, goalEnabled);
                EventLogger.log(this, EventLogger.LEVEL_HIGH, isChecked ? "Timer enabled from UI" : "Timer disabled from UI");
                redrawNotification();
            });
        }

        if (inputDuration != null) {
            inputDuration.setOnClickListener(v -> showDurationDialog(
                    R.string.label_duration,
                    "duration_minutes",
                    SleepTimerStateMachine.DEFAULT_DURATION_MINUTES,
                    0, 12, 5,
                    minutes -> {
                        if (textDurationValue != null) {
                            textDurationValue.setText(DurationUtils.formatDurationString(minutes));
                        }
                    }
            ));
        }

        if (rowAutoTimer != null && switchAutoTimer != null) {
            rowAutoTimer.setOnClickListener(v -> {
                switchAutoTimer.setPressed(true);
                switchAutoTimer.toggle();
                switchAutoTimer.setPressed(false);
            });
        }

        if (switchAutoTimer != null) {
            switchAutoTimer.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isUpdatingUi) return;
                SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
                prefs.edit().putBoolean("auto_timer_enabled", isChecked).apply();
                boolean isUserInitiated = buttonView.isPressed() || isUserInitiatedAutoTimer;
                isUserInitiatedAutoTimer = false;
                if (isChecked) {
                    boolean dndActive = isDndActive();
                    prefs.edit().putBoolean("active", dndActive).apply();
                    if (switchEnableTimer != null) {
                        switchEnableTimer.setChecked(dndActive);
                    }
                    if (isUserInitiated) {
                        openDndSettings();
                    }
                }
                if (isChecked) {
                    EventLogger.log(this, EventLogger.LEVEL_HIGH, isUserInitiated ? "Auto sleep timer (DND) enabled; opening DND settings" : "Auto sleep timer (DND) enabled");
                } else {
                    EventLogger.log(this, EventLogger.LEVEL_HIGH, "Auto sleep timer (DND) disabled");
                }
                redrawNotification();
            });
        }

        if (rowEnableGoal != null && switchEnableGoal != null) {
            rowEnableGoal.setOnClickListener(v -> {
                switchEnableGoal.setPressed(true);
                switchEnableGoal.toggle();
                switchEnableGoal.setPressed(false);
            });
        }

        if (switchEnableGoal != null) {
            switchEnableGoal.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isUpdatingUi) return;
                SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
                prefs.edit()
                        .putBoolean("wake_up_goal_enabled", isChecked)
                        .remove(SleepTimerService.KEY_WAKEUP_LAST_SCHEDULED_MS)
                        .apply();
                boolean timerActive = prefs.getBoolean("active", true);
                updateInputEnabledStates(timerActive, isChecked);
                EventLogger.log(this, EventLogger.LEVEL_HIGH, isChecked ? "Wake-up goal enabled" : "Wake-up goal disabled");
                redrawNotification();
            });
        }

        if (btnTargetTime != null) {
            btnTargetTime.setOnClickListener(v -> showTargetTimeDialog());
        }

        if (btnCurrentWakeTime != null) {
            btnCurrentWakeTime.setOnClickListener(v -> showCurrentWakeTimeDialog());
        }

        if (inputMinSleep != null) {
            inputMinSleep.setOnClickListener(v -> showDurationDialog(
                    R.string.label_min_sleep,
                    "min_sleep_duration_minutes",
                    450,
                    0, 16, 15,
                    minutes -> {
                        SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
                        prefs.edit().remove(SleepTimerService.KEY_WAKEUP_LAST_SCHEDULED_MS).apply();
                        if (textMinSleepValue != null) {
                            textMinSleepValue.setText(DurationUtils.formatDurationString(minutes));
                        }
                    }
            ));
        }

        if (rowHealthConnect != null && switchHealthConnect != null) {
            rowHealthConnect.setOnClickListener(v -> {
                switchHealthConnect.setPressed(true);
                switchHealthConnect.toggle();
                switchHealthConnect.setPressed(false);
            });
        }

        if (switchHealthConnect != null) {
            switchHealthConnect.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isUpdatingUi) return;
                SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
                boolean isUserInitiated = buttonView.isPressed() || isUserInitiatedHealthConnect;
                isUserInitiatedHealthConnect = false;
                if (isChecked) {
                    if (!HealthConnectManager.isHealthConnectAvailable(this)) {
                        isUpdatingUi = true;
                        switchHealthConnect.setChecked(false);
                        isUpdatingUi = false;
                        Toast.makeText(this, R.string.toast_health_connect_not_available, Toast.LENGTH_SHORT).show();
                        EventLogger.log(this, EventLogger.LEVEL_HIGH, "Health Connect requested but SDK is unavailable");
                        return;
                    }
                    prefs.edit().putBoolean("health_connect_enabled", true).apply();
                    Toast.makeText(this, R.string.toast_health_connect_enabled, Toast.LENGTH_SHORT).show();
                    if (isUserInitiated) {
                        EventLogger.log(this, EventLogger.LEVEL_HIGH, "Health Connect sync enabled; opening permissions settings");
                        HealthConnectManager.openHealthConnectPermissions(this);
                    } else {
                        EventLogger.log(this, EventLogger.LEVEL_HIGH, "Health Connect sync enabled");
                    }
                } else {
                    prefs.edit().putBoolean("health_connect_enabled", false).apply();
                    EventLogger.log(this, EventLogger.LEVEL_HIGH, "Health Connect sync disabled; revoking permissions");
                    Toast.makeText(this, R.string.toast_health_connect_disabled, Toast.LENGTH_SHORT).show();
                    HealthConnectManager.revokeAllPermissions(this);
                }
            });
        }
    }

    private void updateInputEnabledStates(boolean active, boolean goalEnabled) {
        setRowEnabled(headerNap, true);
        setRowEnabled(headerTimer, true);
        setRowEnabled(headerAlarm, true);
        setRowEnabled(headerHealthConnect, true);
        setRowEnabled(headerAbout, true);

        setRowEnabled(rowEnableTimer, true);
        setRowEnabled(inputDuration, true);
        setRowEnabled(rowAutoTimer, true);
        setRowEnabled(rowEnableGoal, true);

        setRowEnabled(btnTargetTime, goalEnabled);
        setRowEnabled(btnCurrentWakeTime, goalEnabled);
        setRowEnabled(inputMinSleep, goalEnabled);
        setRowEnabled(rowHealthConnect, true);
        setRowEnabled(btnVersion, true);

        if (goalContainer != null) {
            goalContainer.setVisibility(View.VISIBLE);
        }
    }

    private boolean isDndActive() {
        if (Build.VERSION.SDK_INT >= 23) {
            android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                return nm.getCurrentInterruptionFilter() != android.app.NotificationManager.INTERRUPTION_FILTER_ALL;
            }
        }
        return false;
    }

    private void openDndSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                startActivity(intent);
            } catch (Exception ex) {
                Toast.makeText(this, "Could not open DND settings", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setRowEnabled(View view, boolean enabled) {
        if (view == null) return;
        view.setEnabled(enabled);
        if (!(view instanceof SettingRowView)) {
            view.setClickable(enabled);
            view.setFocusable(enabled);
            view.setAlpha(enabled ? 1.0f : 0.38f);
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    setChildViewsEnabled(group.getChildAt(i), enabled);
                }
            }
        }
    }

    private void setChildViewsEnabled(View view, boolean enabled) {
        if (view == null) return;
        view.setEnabled(enabled);
        if (view instanceof Switch) {
            view.setClickable(enabled);
            view.setFocusable(enabled);
        } else {
            view.setClickable(false);
            view.setFocusable(false);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                setChildViewsEnabled(group.getChildAt(i), enabled);
            }
        }
    }

    private void showTargetTimeDialog() {
        SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
        int goalHour = prefs.getInt("wake_up_goal_hour", 6);
        int goalMin = prefs.getInt("wake_up_goal_minute", 30);
        boolean is24Hour = android.text.format.DateFormat.is24HourFormat(this);

        TimePickerDialog timePickerDialog = new TimePickerDialog(this,
                (view, hourOfDay, minute) -> {
                    SharedPreferences prefs1 = getSharedPreferences("sleep_timer", MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs1.edit()
                            .putInt("wake_up_goal_hour", hourOfDay)
                            .putInt("wake_up_goal_minute", minute);
                    if (!prefs1.contains("current_wake_hour")) {
                        editor.putInt("current_wake_hour", hourOfDay)
                              .putInt("current_wake_minute", minute);
                    }
                    editor.remove(SleepTimerService.KEY_WAKEUP_LAST_SCHEDULED_MS).apply();
                    updateTargetTimeButtonText(hourOfDay, minute);
                    updateCurrentWakeTimeButtonText(prefs1.getInt("current_wake_hour", hourOfDay), prefs1.getInt("current_wake_minute", minute));
                    redrawNotification();
                }, goalHour, goalMin, is24Hour);
        timePickerDialog.show();
    }

    private void showCurrentWakeTimeDialog() {
        SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
        int goalHour = prefs.getInt("wake_up_goal_hour", 6);
        int goalMin = prefs.getInt("wake_up_goal_minute", 30);
        int currentHour = prefs.getInt("current_wake_hour", goalHour);
        int currentMin = prefs.getInt("current_wake_minute", goalMin);
        boolean is24Hour = android.text.format.DateFormat.is24HourFormat(this);

        TimePickerDialog timePickerDialog = new TimePickerDialog(this,
                (view, hourOfDay, minute) -> {
                    SharedPreferences prefs1 = getSharedPreferences("sleep_timer", MODE_PRIVATE);
                    prefs1.edit()
                            .putInt("current_wake_hour", hourOfDay)
                            .putInt("current_wake_minute", minute)
                            .remove(SleepTimerService.KEY_WAKEUP_LAST_SCHEDULED_MS)
                            .apply();
                    updateCurrentWakeTimeButtonText(hourOfDay, minute);
                    redrawNotification();
                }, currentHour, currentMin, is24Hour);
        timePickerDialog.show();
    }

    private void updateTargetTimeButtonText(int hour, int minute) {
        if (textTargetTimeValue != null) {
            textTargetTimeValue.setText(formatTime(hour, minute));
        }
    }

    private void updateCurrentWakeTimeButtonText(int hour, int minute) {
        if (textCurrentWakeTimeValue != null) {
            textCurrentWakeTimeValue.setText(formatTime(hour, minute));
        }
    }

    private String formatTime(int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        java.text.DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);
        return timeFormat.format(cal.getTime());
    }

    private void loadPreferencesIntoUi() {
        isUpdatingUi = true;
        SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);

        boolean active = prefs.getBoolean("active", true);
        int durationMinutes = prefs.getInt("duration_minutes", SleepTimerStateMachine.DEFAULT_DURATION_MINUTES);
        boolean autoTimer = prefs.getBoolean("auto_timer_enabled", false);
        boolean goalEnabled = prefs.getBoolean("wake_up_goal_enabled", false);
        boolean healthConnectEnabled = prefs.getBoolean("health_connect_enabled", false);
        int goalHour = prefs.getInt("wake_up_goal_hour", 6);
        int goalMin = prefs.getInt("wake_up_goal_minute", 30);
        int currentHour = prefs.getInt("current_wake_hour", goalHour);
        int currentMin = prefs.getInt("current_wake_minute", goalMin);
        int minSleepMin = prefs.getInt("min_sleep_duration_minutes", 450);
        int napDurationMinutes = prefs.getInt(SleepTimerService.KEY_NAP_DURATION_MINUTES, 20);
        long napEndsAt = prefs.getLong("nap_alarm_ends_at", 0L);
        boolean isNapActive = napEndsAt > System.currentTimeMillis();

        if (switchEnableTimer != null) {
            switchEnableTimer.setChecked(active);
        }
        if (textDurationValue != null) {
            textDurationValue.setText(DurationUtils.formatDurationString(durationMinutes));
        }
        if (switchAutoTimer != null) {
            switchAutoTimer.setChecked(autoTimer);
        }
        if (switchEnableGoal != null) {
            switchEnableGoal.setChecked(goalEnabled);
        }
        if (switchHealthConnect != null) {
            switchHealthConnect.setChecked(healthConnectEnabled);
        }
        if (healthConnectEnabled) {
            HealthConnectManager.hasSleepWritePermission(this, hasPermission -> {
                if (!hasPermission) {
                    SharedPreferences prefs1 = getSharedPreferences("sleep_timer", MODE_PRIVATE);
                    prefs1.edit().putBoolean("health_connect_enabled", false).apply();
                    if (switchHealthConnect != null) {
                        isUpdatingUi = true;
                        switchHealthConnect.setChecked(false);
                        isUpdatingUi = false;
                    }
                    EventLogger.log(this, EventLogger.LEVEL_HIGH, "Health Connect permission revoked; disabling sync");
                }
            });
        }
        updateTargetTimeButtonText(goalHour, goalMin);
        updateCurrentWakeTimeButtonText(currentHour, currentMin);
        if (textMinSleepValue != null) {
            textMinSleepValue.setText(DurationUtils.formatDurationString(minSleepMin));
        }
        if (btnNap != null && textNapStatus != null) {
            if (isNapActive) {
                textNapStatus.setText(R.string.action_cancel_nap);
                btnNap.setOnClickListener(v -> cancelNap());
            } else {
                textNapStatus.setText(DurationUtils.formatDurationString(napDurationMinutes));
                btnNap.setOnClickListener(v -> openNapDialog());
            }
        }
        updateInputEnabledStates(active, goalEnabled);
        isUpdatingUi = false;
    }

    private void openNapDialog() {
        Intent intent = new Intent(this, NapDialogActivity.class);
        startActivity(intent);
    }

    private void cancelNap() {
        SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
        prefs.edit().remove("nap_alarm_ends_at").apply();

        Intent serviceIntent = new Intent(this, SleepTimerService.class);
        serviceIntent.setAction(SleepTimerService.ACTION_CANCEL_NAP);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        loadPreferencesIntoUi();
    }

    private void exportSettings() {
        SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
        try {
            JSONObject json = new JSONObject();
            json.put("version", 1);
            json.put("duration_minutes", prefs.getInt("duration_minutes", SleepTimerStateMachine.DEFAULT_DURATION_MINUTES));
            json.put("active", prefs.getBoolean("active", true));
            json.put("auto_timer_enabled", prefs.getBoolean("auto_timer_enabled", false));
            json.put("wake_up_goal_enabled", prefs.getBoolean("wake_up_goal_enabled", false));
            json.put("wake_up_goal_hour", prefs.getInt("wake_up_goal_hour", 6));
            json.put("wake_up_goal_minute", prefs.getInt("wake_up_goal_minute", 30));
            int goalHour = prefs.getInt("wake_up_goal_hour", 6);
            int goalMin = prefs.getInt("wake_up_goal_minute", 30);
            json.put("current_wake_hour", prefs.getInt("current_wake_hour", goalHour));
            json.put("current_wake_minute", prefs.getInt("current_wake_minute", goalMin));
            json.put("min_sleep_duration_minutes", prefs.getInt("min_sleep_duration_minutes", 450));
            json.put("health_connect_enabled", prefs.getBoolean("health_connect_enabled", false));

            String exportStr = json.toString();
            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, exportStr);
            sendIntent.setType("text/plain");
            startActivity(Intent.createChooser(sendIntent, getString(R.string.link_export)));

            EventLogger.log(this, EventLogger.LEVEL_HIGH, "Exported settings via system share sheet");
        } catch (JSONException e) {
            EventLogger.log(this, "Failed to export settings: " + e.getMessage());
        }
    }

    private void showImportDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_import_title);
        builder.setMessage(R.string.dialog_import_message);

        final EditText input = new EditText(this);
        input.setSingleLine(false);
        input.setLines(4);

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip()) {
            ClipData clipData = clipboard.getPrimaryClip();
            if (clipData != null && clipData.getItemCount() > 0) {
                CharSequence text = clipData.getItemAt(0).getText();
                if (text != null) {
                    String str = text.toString().trim();
                    if (str.startsWith("{") && str.endsWith("}")) {
                        input.setText(str);
                    }
                }
            }
        }

        builder.setView(input);

        builder.setPositiveButton(R.string.dialog_import_action, (dialog, which) -> {
            String importStr = input.getText().toString().trim();
            importSettings(importStr);
        });
        builder.setNegativeButton(R.string.dialog_cancel, (dialog, which) -> dialog.dismiss());

        builder.show();
    }

    private void importSettings(String jsonStr) {
        if (jsonStr == null || jsonStr.isEmpty()) {
            Toast.makeText(this, R.string.toast_import_invalid, Toast.LENGTH_SHORT).show();
            EventLogger.log(this, "Failed to import settings: empty input");
            return;
        }

        try {
            JSONObject json = new JSONObject(jsonStr);
            if (!json.has("version") || json.getInt("version") != 1) {
                throw new JSONException("Unsupported schema version");
            }

            int durationMinutes = json.getInt("duration_minutes");
            if (durationMinutes < 1 || durationMinutes > 1440) {
                throw new JSONException("duration_minutes out of range");
            }

            boolean active = json.optBoolean("active", false);
            boolean autoTimerEnabled = json.optBoolean("auto_timer_enabled", false);
            boolean wakeUpGoalEnabled = json.getBoolean("wake_up_goal_enabled");
            int wakeUpGoalHour = json.getInt("wake_up_goal_hour");
            if (wakeUpGoalHour < 0 || wakeUpGoalHour > 23) {
                throw new JSONException("wake_up_goal_hour out of range");
            }

            int wakeUpGoalMinute = json.getInt("wake_up_goal_minute");
            if (wakeUpGoalMinute < 0 || wakeUpGoalMinute > 59) {
                throw new JSONException("wake_up_goal_minute out of range");
            }

            int currentWakeHour = json.optInt("current_wake_hour", wakeUpGoalHour);
            if (currentWakeHour < 0 || currentWakeHour > 23) {
                throw new JSONException("current_wake_hour out of range");
            }

            int currentWakeMinute = json.optInt("current_wake_minute", wakeUpGoalMinute);
            if (currentWakeMinute < 0 || currentWakeMinute > 59) {
                throw new JSONException("current_wake_minute out of range");
            }

            int minSleepMinutes = json.getInt("min_sleep_duration_minutes");
            if (minSleepMinutes < 1 || minSleepMinutes > 1440) {
                throw new JSONException("min_sleep_duration_minutes out of range");
            }

            boolean healthConnectEnabled = json.optBoolean("health_connect_enabled", false);

            SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
            prefs.edit()
                    .putInt("duration_minutes", durationMinutes)
                    .putBoolean("active", active)
                    .putBoolean("auto_timer_enabled", autoTimerEnabled)
                    .putBoolean("wake_up_goal_enabled", wakeUpGoalEnabled)
                    .putInt("wake_up_goal_hour", wakeUpGoalHour)
                    .putInt("wake_up_goal_minute", wakeUpGoalMinute)
                    .putInt("current_wake_hour", currentWakeHour)
                    .putInt("current_wake_minute", currentWakeMinute)
                    .putInt("min_sleep_duration_minutes", minSleepMinutes)
                    .putBoolean("health_connect_enabled", healthConnectEnabled)
                    .remove(SleepTimerService.KEY_WAKEUP_LAST_SCHEDULED_MS)
                    .apply();

            Toast.makeText(this, R.string.toast_import_success, Toast.LENGTH_SHORT).show();
            EventLogger.log(this, EventLogger.LEVEL_HIGH, "Imported settings from string");

            loadPreferencesIntoUi();
            redrawNotification();
        } catch (JSONException e) {
            Toast.makeText(this, R.string.toast_import_invalid, Toast.LENGTH_SHORT).show();
            EventLogger.log(this, "Failed to import settings: invalid format (" + e.getMessage() + ")");
        }
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    private void requestExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 31) {
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                EventLogger.log(this, EventLogger.LEVEL_LOW, "Opening exact alarm settings");
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        EventLogger.log(this, EventLogger.LEVEL_LOW, "MainActivity new intent");
        startTimerService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        EventLogger.setListener(this);
        refreshEventLog();
        loadPreferencesIntoUi();
        redrawNotification();
    }

    @Override
    protected void onPause() {
        super.onPause();
        EventLogger.setListener(null);
    }

    private void refreshEventLog() {
        List<String> events = EventLogger.getEvents(this);
        android.text.SpannableStringBuilder ssb = new android.text.SpannableStringBuilder();
        for (String event : events) {
            ssb.append(EventLogger.formatColoredEvent(this, event)).append("\n");
        }
        if (eventLogText != null) {
            eventLogText.setText(ssb);
            scrollToBottom();
        }
    }

    @Override
    public void onEventLogged(String event) {
        if (eventLogText != null) {
            eventLogText.append(EventLogger.formatColoredEvent(this, event));
            eventLogText.append("\n");
            scrollToBottom();
        }
    }

    private void scrollToBottom() {
        if (eventScrollView != null) {
            eventScrollView.post(() -> eventScrollView.fullScroll(ScrollView.FOCUS_DOWN));
        }
    }

    private void redrawNotification() {
        Intent serviceIntent = new Intent(this, SleepTimerService.class);
        serviceIntent.setAction(SleepTimerService.ACTION_REDRAW_NOTIFICATION);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            EventLogger.log(this, EventLogger.LEVEL_LOW, "Notification permission granted: " + granted);
            if (granted) {
                startTimerService();
            }
            redrawNotification();
        }
    }

    private void startTimerService() {
        Intent serviceIntent = new Intent(this, SleepTimerService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }
}
