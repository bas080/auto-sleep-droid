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
import android.widget.TimePicker;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 100;

    private Switch switchEnableTimer;
    private EditText inputDuration;
    private Switch switchShowNotification;
    private Switch switchEnableGoal;
    private View goalContainer;
    private TimePicker timePickerGoal;
    private EditText inputMinSleep;

    private boolean isUpdatingUi = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        setupHeaderAndLinks();
        setupConfigControls();

        startOrRequestNotificationPermission();
        requestExactAlarmPermissionIfNeeded();
    }

    private void bindViews() {
        switchEnableTimer = findViewById(R.id.switch_enable_timer);
        inputDuration = findViewById(R.id.input_duration);
        switchShowNotification = findViewById(R.id.switch_show_notification);
        switchEnableGoal = findViewById(R.id.switch_enable_goal);
        goalContainer = findViewById(R.id.goal_container);
        timePickerGoal = findViewById(R.id.time_picker_goal);
        inputMinSleep = findViewById(R.id.input_min_sleep);

        if (timePickerGoal != null) {
            timePickerGoal.setIs24HourView(false);
        }
    }

    private void setupHeaderAndLinks() {
        TextView versionText = findViewById(R.id.app_version_text);
        if (versionText != null) {
            versionText.setText(getString(R.string.version_label, BuildConfig.VERSION_NAME));
        }

        Button btnManual = findViewById(R.id.btn_manual);
        if (btnManual != null) {
            btnManual.setOnClickListener(v -> showManualDialog());
        }

        Button btnLogs = findViewById(R.id.btn_logs);
        if (btnLogs != null) {
            btnLogs.setOnClickListener(v -> {
                Intent intent = new Intent(this, LogActivity.class);
                startActivity(intent);
            });
        }

        setupLinkButton(R.id.btn_releases, "https://github.com/bas080/auto-sleep-droid/releases");
        setupLinkButton(R.id.btn_github, "https://github.com/bas080/auto-sleep-droid");
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

    private void setupConfigControls() {
        if (switchEnableTimer != null) {
            switchEnableTimer.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isUpdatingUi) return;
                SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
                prefs.edit().putBoolean("active", isChecked).apply();
                EventLogger.log(this, EventLogger.LEVEL_HIGH, isChecked ? "Timer enabled from UI" : "Timer disabled from UI");
                redrawNotification();
            });
        }

        if (inputDuration != null) {
            inputDuration.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus && !isUpdatingUi) {
                    saveDurationFromInput();
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
                        saveDurationFromInput();
                    }
                }
            });
        }

        if (switchShowNotification != null) {
            switchShowNotification.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isUpdatingUi) return;
                SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
                prefs.edit().putBoolean("show_notification", isChecked).apply();
                EventLogger.log(this, EventLogger.LEVEL_NORMAL, "Show notification setting set to: " + isChecked);
                redrawNotification();
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
                if (goalContainer != null) {
                    goalContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                }
                EventLogger.log(this, EventLogger.LEVEL_HIGH, isChecked ? "Wake-up goal enabled" : "Wake-up goal disabled");
                redrawNotification();
            });
        }

        if (timePickerGoal != null) {
            timePickerGoal.setOnTimeChangedListener((view, hourOfDay, minute) -> {
                if (isUpdatingUi) return;
                SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
                prefs.edit()
                        .putInt("wake_up_goal_hour", hourOfDay)
                        .putInt("wake_up_goal_minute", minute)
                        .remove(SleepTimerService.KEY_WAKEUP_LAST_SCHEDULED_MS)
                        .apply();
                redrawNotification();
            });
        }

        if (inputMinSleep != null) {
            inputMinSleep.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus && !isUpdatingUi) {
                    saveMinSleepFromInput();
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
                        saveMinSleepFromInput();
                    }
                }
            });
        }
    }

    private void saveDurationFromInput() {
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

    private void saveMinSleepFromInput() {
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

    private void loadPreferencesIntoUi() {
        isUpdatingUi = true;
        SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);

        boolean active = prefs.getBoolean("active", true);
        int durationMinutes = prefs.getInt("duration_minutes", SleepTimerStateMachine.DEFAULT_DURATION_MINUTES);
        boolean showNotification = prefs.getBoolean("show_notification", true);
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
        if (switchShowNotification != null) {
            switchShowNotification.setChecked(showNotification);
        }
        if (switchEnableGoal != null) {
            switchEnableGoal.setChecked(goalEnabled);
        }
        if (goalContainer != null) {
            goalContainer.setVisibility(goalEnabled ? View.VISIBLE : View.GONE);
        }
        if (timePickerGoal != null) {
            if (Build.VERSION.SDK_INT >= 23) {
                timePickerGoal.setHour(goalHour);
                timePickerGoal.setMinute(goalMin);
            } else {
                timePickerGoal.setCurrentHour(goalHour);
                timePickerGoal.setCurrentMinute(goalMin);
            }
        }
        if (inputMinSleep != null && !inputMinSleep.hasFocus()) {
            inputMinSleep.setText(DurationUtils.formatDurationString(minSleepMin));
        }
        isUpdatingUi = false;
    }

    private void exportSettings() {
        SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
        try {
            JSONObject json = new JSONObject();
            json.put("version", 1);
            json.put("duration_minutes", prefs.getInt("duration_minutes", SleepTimerStateMachine.DEFAULT_DURATION_MINUTES));
            json.put("active", prefs.getBoolean("active", true));
            json.put("show_notification", prefs.getBoolean("show_notification", true));
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
            boolean showNotification = json.optBoolean("show_notification", true);
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
                    .putBoolean("show_notification", showNotification)
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

    private void showManualDialog() {
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

        TextView textView = new TextView(this);
        int paddingPx = (int) (16 * getResources().getDisplayMetrics().density);
        textView.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
        textView.setText(formattedText);
        textView.setTextSize(14);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(textView);

        new AlertDialog.Builder(this)
                .setTitle(R.string.link_manual)
                .setView(scrollView)
                .setPositiveButton(R.string.dialog_ok, (dialog, which) -> dialog.dismiss())
                .show();
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
        startOrRequestNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPreferencesIntoUi();
        redrawNotification();
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

    private void startOrRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            EventLogger.log(this, EventLogger.LEVEL_LOW, "Requesting notification permission");
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST);
            return;
        }
        startTimerService();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            EventLogger.log(this, EventLogger.LEVEL_LOW, "Notification permission granted: " + granted);
            startOrRequestNotificationPermission();
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
