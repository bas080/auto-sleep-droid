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
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
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

    private Switch switchEnableTimer;
    private EditText inputDuration;
    private Switch switchEnableGoal;
    private View goalContainer;
    private Button btnTargetTime;
    private EditText inputMinSleep;
    private ScrollView eventScrollView;
    private TextView eventLogText;

    private boolean isUpdatingUi = false;

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

        switchEnableTimer = findViewById(R.id.switch_enable_timer);
        inputDuration = findViewById(R.id.input_duration);
        switchEnableGoal = findViewById(R.id.switch_enable_goal);
        goalContainer = findViewById(R.id.goal_container);
        btnTargetTime = findViewById(R.id.btn_target_time);
        inputMinSleep = findViewById(R.id.input_min_sleep);
        eventScrollView = findViewById(R.id.event_scroll_view);
        eventLogText = findViewById(R.id.event_log_text);
    }

    private void setupHeaderAndLinks() {
        TextView versionText = findViewById(R.id.app_version_text);
        if (versionText != null) {
            versionText.setText(getString(R.string.version_label, BuildConfig.VERSION_NAME));
        }

        Button btnManual = findViewById(R.id.btn_manual);
        if (btnManual != null) {
            btnManual.setOnClickListener(v -> showManualScreen());
        }

        Button btnLogs = findViewById(R.id.btn_logs);
        if (btnLogs != null) {
            btnLogs.setOnClickListener(v -> showLogsScreen());
        }

        Button btnManualBack = findViewById(R.id.btn_manual_back);
        if (btnManualBack != null) {
            btnManualBack.setOnClickListener(v -> hideOverlays());
        }

        Button btnLogsBack = findViewById(R.id.btn_logs_back);
        if (btnLogsBack != null) {
            btnLogsBack.setOnClickListener(v -> hideOverlays());
        }

        setupLinkButton(R.id.btn_issues, "https://github.com/bas080/auto-sleep-droid/issues");
        setupLinkButton(R.id.btn_donate, "https://liberapay.com/bas080");

        Button btnExport = findViewById(R.id.btn_export);
        if (btnExport != null) {
            btnExport.setOnClickListener(v -> exportSettings());
        }

        Button btnImport = findViewById(R.id.btn_import);
        if (btnImport != null) {
            btnImport.setOnClickListener(v -> showImportDialog());
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

    private void setupConfigControls() {
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
            inputDuration.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus && !isUpdatingUi) {
                    saveDurationFromInputOnFocusChange();
                }
            });
            inputDuration.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    if (!isUpdatingUi) {
                        saveDurationFromInputOnTextChanged();
                    }
                }
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

        if (inputMinSleep != null) {
            inputMinSleep.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus && !isUpdatingUi) {
                    saveMinSleepFromInputOnFocusChange();
                }
            });
            inputMinSleep.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    if (!isUpdatingUi) {
                        saveMinSleepFromInputOnTextChanged();
                    }
                }
            });
        }
    }

    private void updateInputEnabledStates(boolean active, boolean goalEnabled) {
        if (inputDuration != null) {
            inputDuration.setEnabled(active);
        }
        if (switchEnableGoal != null) {
            switchEnableGoal.setEnabled(active);
        }

        boolean goalInputsEnabled = active && goalEnabled;
        if (btnTargetTime != null) {
            btnTargetTime.setEnabled(goalInputsEnabled);
        }
        if (inputMinSleep != null) {
            inputMinSleep.setEnabled(goalInputsEnabled);
        }
        if (goalContainer != null) {
            goalContainer.setVisibility(View.VISIBLE);
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
                    prefs1.edit()
                            .putInt("wake_up_goal_hour", hourOfDay)
                            .putInt("wake_up_goal_minute", minute)
                            .remove(SleepTimerService.KEY_WAKEUP_LAST_SCHEDULED_MS)
                            .apply();
                    updateTargetTimeButtonText(hourOfDay, minute);
                    redrawNotification();
                }, goalHour, goalMin, is24Hour);
        timePickerDialog.show();
    }

    private void updateTargetTimeButtonText(int hour, int minute) {
        if (btnTargetTime != null) {
            btnTargetTime.setText(formatTime(hour, minute));
        }
    }

    private String formatTime(int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        java.text.DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);
        return timeFormat.format(cal.getTime());
    }

    private void saveDurationFromInputOnTextChanged() {
        if (inputDuration == null) return;
        String text = inputDuration.getText().toString().trim();
        int minutes = DurationUtils.parseDurationMinutes(text);
        if (minutes > 0) {
            SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
            if (prefs.getInt("duration_minutes", -1) != minutes) {
                prefs.edit().putInt("duration_minutes", minutes).apply();
                redrawNotification();
            }
        }
    }

    private void saveDurationFromInputOnFocusChange() {
        if (inputDuration == null) return;
        String text = inputDuration.getText().toString().trim();
        int minutes = DurationUtils.parseDurationMinutes(text);
        SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
        int savedMinutes = prefs.getInt("duration_minutes", SleepTimerStateMachine.DEFAULT_DURATION_MINUTES);

        if (minutes > 0) {
            if (savedMinutes != minutes) {
                prefs.edit().putInt("duration_minutes", minutes).apply();
                redrawNotification();
            }
        } else {
            Toast.makeText(this, R.string.toast_duration_invalid, Toast.LENGTH_SHORT).show();
            isUpdatingUi = true;
            inputDuration.setText(DurationUtils.formatDurationString(savedMinutes));
            isUpdatingUi = false;
        }
    }

    private void saveMinSleepFromInputOnTextChanged() {
        if (inputMinSleep == null) return;
        String text = inputMinSleep.getText().toString().trim();
        int minMinutes = DurationUtils.parseDurationMinutes(text, DurationUtils.DefaultUnit.HOURS);
        if (minMinutes > 0) {
            SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
            if (prefs.getInt("min_sleep_duration_minutes", -1) != minMinutes) {
                prefs.edit()
                        .putInt("min_sleep_duration_minutes", minMinutes)
                        .remove(SleepTimerService.KEY_WAKEUP_LAST_SCHEDULED_MS)
                        .apply();
                redrawNotification();
            }
        }
    }

    private void saveMinSleepFromInputOnFocusChange() {
        if (inputMinSleep == null) return;
        String text = inputMinSleep.getText().toString().trim();
        int minMinutes = DurationUtils.parseDurationMinutes(text, DurationUtils.DefaultUnit.HOURS);
        SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
        int savedMinMinutes = prefs.getInt("min_sleep_duration_minutes", 450);

        if (minMinutes > 0) {
            if (savedMinMinutes != minMinutes) {
                prefs.edit()
                        .putInt("min_sleep_duration_minutes", minMinutes)
                        .remove(SleepTimerService.KEY_WAKEUP_LAST_SCHEDULED_MS)
                        .apply();
                redrawNotification();
            }
        } else {
            Toast.makeText(this, R.string.toast_duration_invalid, Toast.LENGTH_SHORT).show();
            isUpdatingUi = true;
            inputMinSleep.setText(DurationUtils.formatDurationString(savedMinMinutes));
            isUpdatingUi = false;
        }
    }

    private void loadPreferencesIntoUi() {
        isUpdatingUi = true;
        SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);

        boolean active = prefs.getBoolean("active", true);
        int durationMinutes = prefs.getInt("duration_minutes", SleepTimerStateMachine.DEFAULT_DURATION_MINUTES);
        boolean goalEnabled = prefs.getBoolean("wake_up_goal_enabled", false);
        int goalHour = prefs.getInt("wake_up_goal_hour", 6);
        int goalMin = prefs.getInt("wake_up_goal_minute", 30);
        int minSleepMin = prefs.getInt("min_sleep_duration_minutes", 450);

        if (switchEnableTimer != null) {
            switchEnableTimer.setChecked(active);
        }
        if (inputDuration != null && !inputDuration.hasFocus()) {
            inputDuration.setText(DurationUtils.formatDurationString(durationMinutes));
        }
        if (switchEnableGoal != null) {
            switchEnableGoal.setChecked(goalEnabled);
        }
        updateTargetTimeButtonText(goalHour, goalMin);
        if (inputMinSleep != null && !inputMinSleep.hasFocus()) {
            inputMinSleep.setText(DurationUtils.formatDurationString(minSleepMin));
        }
        updateInputEnabledStates(active, goalEnabled);
        isUpdatingUi = false;
    }

    private void exportSettings() {
        SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
        try {
            JSONObject json = new JSONObject();
            json.put("version", 1);
            json.put("duration_minutes", prefs.getInt("duration_minutes", SleepTimerStateMachine.DEFAULT_DURATION_MINUTES));
            json.put("active", prefs.getBoolean("active", true));
            json.put("wake_up_goal_enabled", prefs.getBoolean("wake_up_goal_enabled", false));
            json.put("wake_up_goal_hour", prefs.getInt("wake_up_goal_hour", 6));
            json.put("wake_up_goal_minute", prefs.getInt("wake_up_goal_minute", 30));
            json.put("min_sleep_duration_minutes", prefs.getInt("min_sleep_duration_minutes", 450));

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
            boolean wakeUpGoalEnabled = json.getBoolean("wake_up_goal_enabled");
            int wakeUpGoalHour = json.getInt("wake_up_goal_hour");
            if (wakeUpGoalHour < 0 || wakeUpGoalHour > 23) {
                throw new JSONException("wake_up_goal_hour out of range");
            }

            int wakeUpGoalMinute = json.getInt("wake_up_goal_minute");
            if (wakeUpGoalMinute < 0 || wakeUpGoalMinute > 59) {
                throw new JSONException("wake_up_goal_minute out of range");
            }

            int minSleepMinutes = json.getInt("min_sleep_duration_minutes");
            if (minSleepMinutes < 1 || minSleepMinutes > 1440) {
                throw new JSONException("min_sleep_duration_minutes out of range");
            }

            SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
            prefs.edit()
                    .putInt("duration_minutes", durationMinutes)
                    .putBoolean("active", active)
                    .putBoolean("wake_up_goal_enabled", wakeUpGoalEnabled)
                    .putInt("wake_up_goal_hour", wakeUpGoalHour)
                    .putInt("wake_up_goal_minute", wakeUpGoalMinute)
                    .putInt("min_sleep_duration_minutes", minSleepMinutes)
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

    private void setupLinkButton(int buttonId, String url) {
        Button btn = findViewById(buttonId);
        if (btn != null) {
            btn.setOnClickListener(v -> openUrl(url));
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
