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

    private View rowNapDnd;
    private Switch switchNapDnd;
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
    private View inputHcMinDuration;
    private TextView textHcMinDurationValue;
    private View btnNap;
    private TextView textNapStatus;
    private View btnVersion;
    private View btnLinks;
    private ScrollView eventScrollView;
    private TextView eventLogText;

    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private PreferenceManager.EffectHandle uiEffectsHandle;
    private PreferenceManager preferenceManager;
    private MainService boundService;
    private boolean isBound = false;
    private boolean isUpdatingUi = false;
    private boolean isUserInitiatedAutoTimer = false;
    private boolean isUserInitiatedHealthConnect = false;
    private boolean isRequestingHealthConnectPermission = false;

    private final android.content.ServiceConnection serviceConnection = new android.content.ServiceConnection() {
        @Override
        public void onServiceConnected(android.content.ComponentName name, android.os.IBinder service) {
            MainService.LocalBinder binder = (MainService.LocalBinder) service;
            boundService = binder.getService();
            isBound = true;
            registerPreferenceListeners();
        }

        @Override
        public void onServiceDisconnected(android.content.ComponentName name) {
            boundService = null;
            isBound = false;
        }
    };

    private interface OnDurationSavedListener {
        void onSaved(int minutes);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferenceManager = new PreferenceManager(this, PreferenceKeys.PREFERENCES_NAME);

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

        rowNapDnd = findViewById(R.id.row_nap_dnd);
        switchNapDnd = findViewById(R.id.switch_nap_dnd);
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
        inputHcMinDuration = findViewById(R.id.input_hc_min_duration);
        textHcMinDurationValue = findViewById(R.id.text_hc_min_duration_value);
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
            List<String> events = EventLogger.getEvents(this);
            if (!events.isEmpty()) {
                bodyBuilder.append("Logs:\n");
                for (String event : events) {
                    bodyBuilder.append(EventLogger.formatColoredEvent(this, event).toString()).append("\n");
                }
                bodyBuilder.append("\n");
            }
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
        int currentMinutes = preferenceManager.getInt(prefKey, defaultMinutes);

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
                preferenceManager.putInt(prefKey, minutes);
                if (listener != null) {
                    listener.onSaved(minutes);
                }
            } else {
                Toast.makeText(MainActivity.this, R.string.toast_duration_invalid, Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(R.string.dialog_cancel, (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void setupConfigControls() {
        if (rowNapDnd != null && switchNapDnd != null) {
            rowNapDnd.setOnClickListener(v -> {
                switchNapDnd.setPressed(true);
                switchNapDnd.toggle();
                switchNapDnd.setPressed(false);
            });
        }

        if (switchNapDnd != null) {
            switchNapDnd.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isUpdatingUi) return;
                preferenceManager.putBoolean(PreferenceKeys.KEY_NAP_DND_ENABLED, isChecked);
                boolean isUserInitiated = buttonView.isPressed();
                if (isChecked && isUserInitiated && !isDndPermissionGranted()) {
                    EventLogger.log(this, EventLogger.LEVEL_HIGH, "Nap DND enabled; DND policy permission missing, opening settings");
                    openDndPermissionSettings();
                } else {
                    EventLogger.log(this, EventLogger.LEVEL_HIGH, isChecked ? "Nap DND enabled" : "Nap DND disabled");
                }
            });
        }

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
                preferenceManager.putBoolean(PreferenceKeys.KEY_ACTIVE, isChecked);
                boolean goalEnabled = preferenceManager.getBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, false);
                updateInputEnabledStates(isChecked, goalEnabled);
                EventLogger.log(this, EventLogger.LEVEL_HIGH, isChecked ? "Timer enabled from UI" : "Timer disabled from UI");
            });
        }

        if (inputDuration != null) {
            inputDuration.setOnClickListener(v -> showDurationDialog(
                    R.string.label_duration,
                    PreferenceKeys.KEY_DURATION_MINUTES,
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
                isUserInitiatedAutoTimer = true;
                switchAutoTimer.setPressed(true);
                switchAutoTimer.toggle();
                switchAutoTimer.setPressed(false);
            });
        }

        if (switchAutoTimer != null) {
            switchAutoTimer.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isUpdatingUi) return;
                preferenceManager.putBoolean(PreferenceKeys.KEY_AUTO_TIMER_ENABLED, isChecked);
                boolean isUserInitiated = buttonView.isPressed() || isUserInitiatedAutoTimer;
                isUserInitiatedAutoTimer = false;
                if (isChecked) {
                    boolean dndActive = isDndActive();
                    preferenceManager.putBoolean(PreferenceKeys.KEY_ACTIVE, dndActive);
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
                preferenceManager.putBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, isChecked);
                preferenceManager.remove(PreferenceKeys.KEY_WAKEUP_LAST_SCHEDULED_MS);
                boolean timerActive = preferenceManager.getBoolean(PreferenceKeys.KEY_ACTIVE, true);
                updateInputEnabledStates(timerActive, isChecked);
                EventLogger.log(this, EventLogger.LEVEL_HIGH, isChecked ? "Wake-up goal enabled" : "Wake-up goal disabled");
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
                    PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES,
                    450,
                    0, 16, 15,
                    minutes -> {
                        preferenceManager.remove(PreferenceKeys.KEY_WAKEUP_LAST_SCHEDULED_MS);
                        if (textMinSleepValue != null) {
                            textMinSleepValue.setText(DurationUtils.formatDurationString(minutes));
                        }
                    }
            ));
        }

        if (rowHealthConnect != null && switchHealthConnect != null) {
            rowHealthConnect.setOnClickListener(v -> {
                isUserInitiatedHealthConnect = true;
                switchHealthConnect.setPressed(true);
                switchHealthConnect.toggle();
                switchHealthConnect.setPressed(false);
            });
        }

        if (switchHealthConnect != null) {
            switchHealthConnect.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isUpdatingUi) return;
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
                    preferenceManager.putBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, true);
                    Toast.makeText(this, R.string.toast_health_connect_enabled, Toast.LENGTH_SHORT).show();
                    if (isUserInitiated) {
                        isRequestingHealthConnectPermission = true;
                        HealthConnectManager.hasSleepWritePermission(this, hasPermission -> {
                            if (!hasPermission) {
                                EventLogger.log(this, EventLogger.LEVEL_HIGH, "Health Connect sync enabled; opening permissions settings");
                                HealthConnectManager.openHealthConnectPermissions(this);
                            } else {
                                isRequestingHealthConnectPermission = false;
                                EventLogger.log(this, EventLogger.LEVEL_HIGH, "Health Connect sync enabled");
                            }
                        });
                    } else {
                        EventLogger.log(this, EventLogger.LEVEL_HIGH, "Health Connect sync enabled");
                    }
                } else {
                    if (isUserInitiated) {
                        isRequestingHealthConnectPermission = false;
                        preferenceManager.putBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, false);
                        EventLogger.log(this, EventLogger.LEVEL_HIGH, "Health Connect sync disabled; revoking permissions");
                        Toast.makeText(this, R.string.toast_health_connect_disabled, Toast.LENGTH_SHORT).show();
                        HealthConnectManager.revokeAllPermissions(this);
                    } else {
                        preferenceManager.putBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, false);
                    }
                }
                boolean active = preferenceManager.getBoolean(PreferenceKeys.KEY_ACTIVE, true);
                boolean goalEnabled = preferenceManager.getBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, false);
                updateInputEnabledStates(active, goalEnabled, isChecked);
            });
        }

        if (inputHcMinDuration != null) {
            inputHcMinDuration.setOnClickListener(v -> showDurationDialog(
                    R.string.label_hc_min_duration,
                    PreferenceKeys.KEY_HC_MIN_DURATION_MINUTES,
                    15,
                    0, 2, 5,
                    minutes -> {
                        if (textHcMinDurationValue != null) {
                            textHcMinDurationValue.setText(DurationUtils.formatDurationString(minutes));
                        }
                    }
            ));
        }
    }

    private void updateInputEnabledStates(boolean active, boolean goalEnabled) {
        boolean healthConnectEnabled = preferenceManager.getBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, false);
        updateInputEnabledStates(active, goalEnabled, healthConnectEnabled);
    }

    private void updateInputEnabledStates(boolean active, boolean goalEnabled, boolean healthConnectEnabled) {
        setRowEnabled(headerNap, true);
        setRowEnabled(headerTimer, true);
        setRowEnabled(headerAlarm, true);
        setRowEnabled(headerHealthConnect, true);
        setRowEnabled(headerAbout, true);

        setRowEnabled(rowNapDnd, true);
        setRowEnabled(rowEnableTimer, true);
        setRowEnabled(inputDuration, true);
        setRowEnabled(rowAutoTimer, true);
        setRowEnabled(rowEnableGoal, true);

        setRowEnabled(btnTargetTime, goalEnabled);
        setRowEnabled(btnCurrentWakeTime, goalEnabled);
        setRowEnabled(inputMinSleep, goalEnabled);
        setRowEnabled(rowHealthConnect, true);
        setRowEnabled(inputHcMinDuration, healthConnectEnabled);
        setRowEnabled(btnVersion, true);

        if (goalContainer != null) {
            goalContainer.setVisibility(View.VISIBLE);
        }
    }

    private boolean isDndPermissionGranted() {
        if (Build.VERSION.SDK_INT >= 23) {
            android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            return nm != null && nm.isNotificationPolicyAccessGranted();
        }
        return true;
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

    private void openDndPermissionSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS);
                startActivity(intent);
            } catch (Exception ex) {
                Toast.makeText(this, "Could not open DND settings", Toast.LENGTH_SHORT).show();
            }
        }
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
        int goalHour = preferenceManager.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR, 6);
        int goalMin = preferenceManager.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE, 30);
        boolean is24Hour = android.text.format.DateFormat.is24HourFormat(this);

        TimePickerDialog timePickerDialog = new TimePickerDialog(this,
                (view, hourOfDay, minute) -> {
                    preferenceManager.putInt(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR, hourOfDay);
                    preferenceManager.putInt(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE, minute);
                    if (!preferenceManager.contains(PreferenceKeys.KEY_CURRENT_WAKE_HOUR)) {
                        preferenceManager.putInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, hourOfDay);
                        preferenceManager.putInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, minute);
                    }
                    preferenceManager.remove(PreferenceKeys.KEY_WAKEUP_LAST_SCHEDULED_MS);
                    updateTargetTimeButtonText(hourOfDay, minute);
                    updateCurrentWakeTimeButtonText(preferenceManager.getInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, hourOfDay), preferenceManager.getInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, minute));
                    redrawNotification();
                }, goalHour, goalMin, is24Hour);
        timePickerDialog.show();
    }

    private void showCurrentWakeTimeDialog() {
        int goalHour = preferenceManager.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR, 6);
        int goalMin = preferenceManager.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE, 30);
        int currentHour = preferenceManager.getInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, goalHour);
        int currentMin = preferenceManager.getInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, goalMin);
        boolean is24Hour = android.text.format.DateFormat.is24HourFormat(this);

        TimePickerDialog timePickerDialog = new TimePickerDialog(this,
                (view, hourOfDay, minute) -> {
                    preferenceManager.putInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, hourOfDay);
                    preferenceManager.putInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, minute);
                    preferenceManager.remove(PreferenceKeys.KEY_WAKEUP_LAST_SCHEDULED_MS);
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

    private String getComputedDurationString(PreferenceManager pm, String key, int defaultMinutes) {
        if (pm == null) return DurationUtils.formatDurationString(defaultMinutes);
        return pm.getComputed(key, PreferenceComputations.formatDuration(key, defaultMinutes));
    }

    private void updateNapUi(PreferenceGetter getter) {
        boolean napDndEnabled = getter.getBoolean(PreferenceKeys.KEY_NAP_DND_ENABLED, false);
        int napDurationMinutes = getter.getInt(PreferenceKeys.KEY_NAP_DURATION_MINUTES, 20);
        long napEndsAt = getter.getLong(PreferenceKeys.KEY_NAP_ALARM_ENDS_AT, 0L);

        boolean isNapActive = preferenceManager != null
                ? Boolean.TRUE.equals(preferenceManager.getComputed(PreferenceComputations.IS_NAP_ACTIVE))
                : napEndsAt > System.currentTimeMillis();

        if (switchNapDnd != null) {
            switchNapDnd.setChecked(napDndEnabled);
        }
        if (btnNap != null && textNapStatus != null) {
            if (isNapActive) {
                textNapStatus.setText(R.string.action_cancel_nap);
                btnNap.setOnClickListener(v -> cancelNap());
            } else {
                textNapStatus.setText(getComputedDurationString(preferenceManager, PreferenceKeys.KEY_NAP_DURATION_MINUTES, napDurationMinutes));
                btnNap.setOnClickListener(v -> openNapDialog());
            }
        }
    }

    private void updateTimerUi(PreferenceGetter getter) {
        boolean active = getter.getBoolean(PreferenceKeys.KEY_ACTIVE, true);
        int durationMinutes = getter.getInt(PreferenceKeys.KEY_DURATION_MINUTES, SleepTimerStateMachine.DEFAULT_DURATION_MINUTES);
        boolean autoTimer = getter.getBoolean(PreferenceKeys.KEY_AUTO_TIMER_ENABLED, false);

        if (switchEnableTimer != null) {
            switchEnableTimer.setChecked(active);
        }
        if (textDurationValue != null) {
            textDurationValue.setText(getComputedDurationString(preferenceManager, PreferenceManager.KEY_DURATION_MINUTES, durationMinutes));
        }
        if (switchAutoTimer != null) {
            switchAutoTimer.setChecked(autoTimer);
        }
    }

    private void updateGoalUi(PreferenceGetter getter) {
        boolean active = getter.getBoolean(PreferenceKeys.KEY_ACTIVE, true);
        boolean goalEnabled = getter.getBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, false);
        boolean healthConnectEnabled = getter.getBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, false);
        int goalHour = getter.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR, 6);
        int goalMin = getter.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE, 30);
        int currentHour = getter.getInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, goalHour);
        int currentMin = getter.getInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, goalMin);
        int minSleepMin = getter.getInt(PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES, 450);

        if (switchEnableGoal != null) {
            switchEnableGoal.setChecked(goalEnabled);
        }
        updateTargetTimeButtonText(goalHour, goalMin);
        updateCurrentWakeTimeButtonText(currentHour, currentMin);
        if (textMinSleepValue != null) {
            textMinSleepValue.setText(getComputedDurationString(preferenceManager, PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES, minSleepMin));
        }
        updateInputEnabledStates(active, goalEnabled, healthConnectEnabled);
    }

    private void updateHealthConnectUi(PreferenceGetter getter) {
        boolean healthConnectEnabled = getter.getBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, false);
        int hcMinDurationMin = getter.getInt(PreferenceKeys.KEY_HC_MIN_DURATION_MINUTES, 15);

        if (switchHealthConnect != null) {
            switchHealthConnect.setChecked(healthConnectEnabled);
        }
        if (textHcMinDurationValue != null) {
            textHcMinDurationValue.setText(getComputedDurationString(preferenceManager, PreferenceKeys.KEY_HC_MIN_DURATION_MINUTES, hcMinDurationMin));
        }
        if (healthConnectEnabled) {
            if (!isRequestingHealthConnectPermission) {
                HealthConnectManager.hasSleepWritePermission(this, hasPermission -> {
                    if (!hasPermission) {
                        preferenceManager.putBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, false);
                        if (switchHealthConnect != null) {
                            isUpdatingUi = true;
                            switchHealthConnect.setChecked(false);
                            isUpdatingUi = false;
                        }
                        EventLogger.log(this, EventLogger.LEVEL_HIGH, "Health Connect permission revoked; disabling sync");
                    }
                });
            }
        }
    }

    private void openNapDialog() {
        Intent intent = new Intent(this, NapDialogActivity.class);
        startActivity(intent);
    }

    private void cancelNap() {
        preferenceManager.remove(PreferenceKeys.KEY_NAP_ALARM_ENDS_AT);

        Intent serviceIntent = new Intent(this, MainService.class);
        serviceIntent.setAction(MainService.ACTION_CANCEL_NAP);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void exportSettings() {
        try {
            JSONObject json = new JSONObject();
            json.put("version", 1);
            json.put("nap_dnd_enabled", preferenceManager.getBoolean("nap_dnd_enabled", false));
            json.put("duration_minutes", preferenceManager.getInt("duration_minutes", SleepTimerStateMachine.DEFAULT_DURATION_MINUTES));
            json.put("active", preferenceManager.getBoolean("active", true));
            json.put("auto_timer_enabled", preferenceManager.getBoolean("auto_timer_enabled", false));
            json.put("wake_up_goal_enabled", preferenceManager.getBoolean("wake_up_goal_enabled", false));
            json.put("wake_up_goal_hour", preferenceManager.getInt("wake_up_goal_hour", 6));
            json.put("wake_up_goal_minute", preferenceManager.getInt("wake_up_goal_minute", 30));
            int goalHour = preferenceManager.getInt("wake_up_goal_hour", 6);
            int goalMin = preferenceManager.getInt("wake_up_goal_minute", 30);
            json.put("current_wake_hour", preferenceManager.getInt("current_wake_hour", goalHour));
            json.put("current_wake_minute", preferenceManager.getInt("current_wake_minute", goalMin));
            json.put("min_sleep_duration_minutes", preferenceManager.getInt("min_sleep_duration_minutes", 450));
            json.put("health_connect_enabled", preferenceManager.getBoolean("health_connect_enabled", false));
            json.put("hc_min_duration_minutes", preferenceManager.getInt("hc_min_duration_minutes", 15));

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

            boolean napDndEnabled = json.optBoolean("nap_dnd_enabled", false);
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
            int hcMinDurationMinutes = json.optInt("hc_min_duration_minutes", 15);
            if (hcMinDurationMinutes < 0 || hcMinDurationMinutes > 1440) {
                throw new JSONException("hc_min_duration_minutes out of range");
            }

            preferenceManager.getSharedPreferences().edit()
                    .putBoolean(PreferenceKeys.KEY_NAP_DND_ENABLED, napDndEnabled)
                    .putInt(PreferenceKeys.KEY_DURATION_MINUTES, durationMinutes)
                    .putBoolean(PreferenceKeys.KEY_ACTIVE, active)
                    .putBoolean(PreferenceKeys.KEY_AUTO_TIMER_ENABLED, autoTimerEnabled)
                    .putBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, wakeUpGoalEnabled)
                    .putInt(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR, wakeUpGoalHour)
                    .putInt(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE, wakeUpGoalMinute)
                    .putInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, currentWakeHour)
                    .putInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, currentWakeMinute)
                    .putInt(PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES, minSleepMinutes)
                    .putBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, healthConnectEnabled)
                    .putInt(PreferenceKeys.KEY_HC_MIN_DURATION_MINUTES, hcMinDurationMinutes)
                    .remove(PreferenceKeys.KEY_WAKEUP_LAST_SCHEDULED_MS)
                    .apply();

            Toast.makeText(this, R.string.toast_import_success, Toast.LENGTH_SHORT).show();
            EventLogger.log(this, EventLogger.LEVEL_HIGH, "Imported settings from string");

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
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, MainService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
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
        if (isRequestingHealthConnectPermission) {
            HealthConnectManager.hasSleepWritePermission(this, hasPermission -> {
                isRequestingHealthConnectPermission = false;
                if (hasPermission) {
                    preferenceManager.putBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, true);
                    if (switchHealthConnect != null) {
                        isUpdatingUi = true;
                        switchHealthConnect.setChecked(true);
                        isUpdatingUi = false;
                    }
                    EventLogger.log(this, EventLogger.LEVEL_HIGH, "Health Connect sync enabled and permission granted");
                } else {
                    preferenceManager.putBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, false);
                    if (switchHealthConnect != null) {
                        isUpdatingUi = true;
                        switchHealthConnect.setChecked(false);
                        isUpdatingUi = false;
                    }
                    Toast.makeText(this, R.string.toast_health_connect_disabled, Toast.LENGTH_SHORT).show();
                    EventLogger.log(this, EventLogger.LEVEL_HIGH, "Health Connect permission not granted; disabling sync");
                }
            });
        }
        redrawNotification();
        registerPreferenceListeners();
    }

    private void registerPreferenceListeners() {
        if (preferenceManager == null) return;

        if (uiEffectsHandle == null) {
            uiEffectsHandle = preferenceManager.watchEffects(
                getter -> {
                    isUpdatingUi = true;
                    updateNapUi(getter);
                    isUpdatingUi = false;
                },
                getter -> {
                    isUpdatingUi = true;
                    updateTimerUi(getter);
                    isUpdatingUi = false;
                },
                getter -> {
                    isUpdatingUi = true;
                    updateGoalUi(getter);
                    isUpdatingUi = false;
                },
                getter -> {
                    isUpdatingUi = true;
                    updateHealthConnectUi(getter);
                    isUpdatingUi = false;
                }
            );
        }
    }

    private void unregisterPreferenceListeners() {
        if (uiEffectsHandle != null) {
            uiEffectsHandle.dispose();
            uiEffectsHandle = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        EventLogger.setListener(null);
        unregisterPreferenceListeners();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isBound) {
            unregisterPreferenceListeners();
            unbindService(serviceConnection);
            isBound = false;
            boundService = null;
        }
    }

    @Override
    protected void onDestroy() {
        if (preferenceManager != null) {
            preferenceManager.shutdown();
            preferenceManager = null;
        }
        super.onDestroy();
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
        Intent serviceIntent = new Intent(this, MainService.class);
        serviceIntent.setAction(MainService.ACTION_REDRAW_NOTIFICATION);
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
        Intent serviceIntent = new Intent(this, MainService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }
}
