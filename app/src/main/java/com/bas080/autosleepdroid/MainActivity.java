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
import android.widget.Button;
import android.widget.EditText;
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

        Button btnManual = findViewById(R.id.btn_manual);
        if (btnManual != null) {
            btnManual.setOnClickListener(v -> showManualDialog());
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

            redrawNotification();
        } catch (JSONException e) {
            Toast.makeText(this, R.string.toast_import_invalid, Toast.LENGTH_SHORT).show();
            EventLogger.log(this, "Failed to import settings: invalid format (" + e.getMessage() + ")");
        }
    }

    private void showManualDialog() {
        String markdownText = "";
        try (java.io.InputStream is = getAssets().open("manual.md");
             java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            markdownText = sb.toString();
        } catch (java.io.IOException e) {
            EventLogger.log(this, "Failed to load manual: " + e.getMessage());
            return;
        }

        CharSequence formattedText = formatMarkdown(markdownText);

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

    private CharSequence formatMarkdown(String md) {
        if (md == null || md.isEmpty()) {
            return "";
        }
        StringBuilder html = new StringBuilder();
        String[] lines = md.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ")) {
                html.append("<h2><b>").append(escapeHtml(trimmed.substring(2))).append("</b></h2>");
            } else if (trimmed.startsWith("## ")) {
                html.append("<h3><b>").append(escapeHtml(trimmed.substring(3))).append("</b></h3>");
            } else if (trimmed.startsWith("- ")) {
                html.append("&bull; ").append(processInlineMarkdown(trimmed.substring(2))).append("<br>");
            } else if (trimmed.isEmpty()) {
                html.append("<br>");
            } else {
                html.append(processInlineMarkdown(trimmed)).append("<br>");
            }
        }
        if (Build.VERSION.SDK_INT >= 24) {
            return android.text.Html.fromHtml(html.toString(), android.text.Html.FROM_HTML_MODE_LEGACY);
        } else {
            return android.text.Html.fromHtml(html.toString());
        }
    }

    private String processInlineMarkdown(String text) {
        String escaped = escapeHtml(text);
        return escaped.replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>");
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;");
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
