package com.bas080.autosleepdroid;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.net.Uri;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class MainActivity extends Activity implements EventLogger.Listener {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 100;
    private ScrollView scrollView;
    private TextView eventLogText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        scrollView = findViewById(R.id.event_scroll_view);
        eventLogText = findViewById(R.id.event_log_text);

        setupHeaderAndLinks();

        startOrRequestNotificationPermission();
        requestExactAlarmPermissionIfNeeded();
    }

    private void setupHeaderAndLinks() {
        TextView versionText = findViewById(R.id.app_version_text);
        if (versionText != null) {
            versionText.setText(getString(R.string.version_label, BuildConfig.VERSION_NAME));
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

    private void exportSettings() {
        SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
        try {
            JSONObject json = new JSONObject();
            json.put("version", 1);
            json.put("duration_minutes", prefs.getInt("duration_minutes", 20));
            json.put("active", prefs.getBoolean("active", false));
            json.put("wake_up_goal_enabled", prefs.getBoolean("wake_up_goal_enabled", false));
            json.put("wake_up_goal_hour", prefs.getInt("wake_up_goal_hour", 6));
            json.put("wake_up_goal_minute", prefs.getInt("wake_up_goal_minute", 30));
            json.put("min_sleep_duration_minutes", prefs.getInt("min_sleep_duration_minutes", 450));

            String jsonString = json.toString();

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, jsonString);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.link_export)));

            EventLogger.log(this, EventLogger.LEVEL_LOW, "Exported settings via system share sheet");
        } catch (JSONException e) {
            EventLogger.log(this, EventLogger.LEVEL_LOW, "Failed to export settings: " + e.getMessage());
        }
    }

    private void showImportDialog() {
        EditText input = new EditText(this);
        input.setHint("{\"version\":1,...}");

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip()) {
            ClipData clip = clipboard.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence text = clip.getItemAt(0).getText();
                if (text != null) {
                    String clipString = text.toString().trim();
                    if (clipString.startsWith("{") && clipString.endsWith("}")) {
                        try {
                            new JSONObject(clipString);
                            input.setText(clipString);
                        } catch (JSONException ignored) {
                        }
                    }
                }
            }
        }

        FrameLayout container = new FrameLayout(this);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding / 2, padding, 0);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle(R.string.import_settings_title)
                .setMessage(R.string.import_settings_instruction)
                .setView(container)
                .setPositiveButton(R.string.action_import, (dialog, which) -> {
                    String jsonString = input.getText().toString().trim();
                    processImportString(jsonString);
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void processImportString(String jsonString) {
        if (jsonString.isEmpty()) {
            Toast.makeText(this, R.string.import_settings_toast_invalid, Toast.LENGTH_SHORT).show();
            EventLogger.log(this, EventLogger.LEVEL_LOW, "Failed to import settings: empty input");
            return;
        }

        try {
            JSONObject json = new JSONObject(jsonString);
            int version = json.optInt("version", -1);
            if (version != 1) {
                Toast.makeText(this, R.string.import_settings_toast_invalid, Toast.LENGTH_SHORT).show();
                EventLogger.log(this, EventLogger.LEVEL_LOW, "Failed to import settings: unsupported version " + version);
                return;
            }

            int durationMinutes = json.optInt("duration_minutes", 20);
            if (durationMinutes < 1 || durationMinutes > 1440) {
                Toast.makeText(this, R.string.import_settings_toast_invalid, Toast.LENGTH_SHORT).show();
                EventLogger.log(this, EventLogger.LEVEL_LOW, "Failed to import settings: duration_minutes out of range");
                return;
            }

            int wakeUpGoalHour = json.optInt("wake_up_goal_hour", 6);
            if (wakeUpGoalHour < 0 || wakeUpGoalHour > 23) {
                Toast.makeText(this, R.string.import_settings_toast_invalid, Toast.LENGTH_SHORT).show();
                EventLogger.log(this, EventLogger.LEVEL_LOW, "Failed to import settings: wake_up_goal_hour out of range");
                return;
            }

            int wakeUpGoalMinute = json.optInt("wake_up_goal_minute", 30);
            if (wakeUpGoalMinute < 0 || wakeUpGoalMinute > 59) {
                Toast.makeText(this, R.string.import_settings_toast_invalid, Toast.LENGTH_SHORT).show();
                EventLogger.log(this, EventLogger.LEVEL_LOW, "Failed to import settings: wake_up_goal_minute out of range");
                return;
            }

            int minSleepDurationMinutes = json.optInt("min_sleep_duration_minutes", 450);
            if (minSleepDurationMinutes < 1 || minSleepDurationMinutes > 1440) {
                Toast.makeText(this, R.string.import_settings_toast_invalid, Toast.LENGTH_SHORT).show();
                EventLogger.log(this, EventLogger.LEVEL_LOW, "Failed to import settings: min_sleep_duration_minutes out of range");
                return;
            }

            boolean active = json.optBoolean("active", false);
            boolean wakeUpGoalEnabled = json.optBoolean("wake_up_goal_enabled", false);

            SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
            prefs.edit()
                    .putInt("duration_minutes", durationMinutes)
                    .putBoolean("active", active)
                    .putBoolean("wake_up_goal_enabled", wakeUpGoalEnabled)
                    .putInt("wake_up_goal_hour", wakeUpGoalHour)
                    .putInt("wake_up_goal_minute", wakeUpGoalMinute)
                    .putInt("min_sleep_duration_minutes", minSleepDurationMinutes)
                    .apply();

            Toast.makeText(this, R.string.import_settings_toast_success, Toast.LENGTH_SHORT).show();
            EventLogger.log(this, EventLogger.LEVEL_LOW, "Imported settings from string");

            redrawNotification();
        } catch (JSONException e) {
            Toast.makeText(this, R.string.import_settings_toast_invalid, Toast.LENGTH_SHORT).show();
            EventLogger.log(this, EventLogger.LEVEL_LOW, "Failed to import settings: invalid JSON format");
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
        startOrRequestNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        EventLogger.setListener(this);
        refreshEventLog();
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

    @Override
    protected void onPause() {
        super.onPause();
        EventLogger.setListener(null);
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
        if (scrollView != null) {
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
        }
    }
}
