package com.bas080.autosleepdroid;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

public class MainActivity extends Activity implements EventLogger.Listener {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 100;
    private static final String PREF_SLEEP_TIMER = "sleep_timer";
    public static final String PREF_WAKEUP_ENABLED = "wakeup_alarm_enabled";
    public static final String PREF_WAKEUP_HOURS = "wakeup_alarm_hours";
    public static final String PREF_WAKEUP_MINUTES = "wakeup_alarm_minutes";
    public static final String PREF_WAKEUP_MIN_SLEEP_HOURS = "wakeup_min_sleep_hours";
    public static final int DEFAULT_WAKEUP_HOURS = 6;
    public static final int DEFAULT_WAKEUP_MINUTES = 30;
    public static final float DEFAULT_MIN_SLEEP_HOURS = 7.5f;

    private ScrollView scrollView;
    private TextView eventLogText;
    private TextView wakeupAlarmStatusText;
    private Button btnSetWakeupAlarm;
    private Button btnClearWakeupAlarm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        scrollView = findViewById(R.id.event_scroll_view);
        eventLogText = findViewById(R.id.event_log_text);
        wakeupAlarmStatusText = findViewById(R.id.wakeup_alarm_status_text);
        btnSetWakeupAlarm = findViewById(R.id.btn_set_wakeup_alarm);
        btnClearWakeupAlarm = findViewById(R.id.btn_clear_wakeup_alarm);

        btnSetWakeupAlarm.setOnClickListener(v -> showWakeupAlarmDialog());
        btnClearWakeupAlarm.setOnClickListener(v -> clearWakeupAlarm());

        EventLogger.log(this, "MainActivity created");

        updateWakeupAlarmStatusUI();
        startOrRequestNotificationPermission();
        requestExactAlarmPermissionIfNeeded();
    }

    private void updateWakeupAlarmStatusUI() {
        SharedPreferences prefs = getSharedPreferences(PREF_SLEEP_TIMER, Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(PREF_WAKEUP_ENABLED, false);
        if (enabled) {
            int hours = prefs.getInt(PREF_WAKEUP_HOURS, DEFAULT_WAKEUP_HOURS);
            int minutes = prefs.getInt(PREF_WAKEUP_MINUTES, DEFAULT_WAKEUP_MINUTES);
            float minSleepHours = prefs.getFloat(PREF_WAKEUP_MIN_SLEEP_HOURS, DEFAULT_MIN_SLEEP_HOURS);
            wakeupAlarmStatusText.setText(getString(R.string.wakeup_alarm_status_enabled, hours, minutes, minSleepHours));
            btnClearWakeupAlarm.setEnabled(true);
        } else {
            wakeupAlarmStatusText.setText(getString(R.string.wakeup_alarm_status_disabled));
            btnClearWakeupAlarm.setEnabled(false);
        }
    }

    private void showWakeupAlarmDialog() {
        SharedPreferences prefs = getSharedPreferences(PREF_SLEEP_TIMER, Context.MODE_PRIVATE);
        int currHours = prefs.getInt(PREF_WAKEUP_HOURS, DEFAULT_WAKEUP_HOURS);
        int currMinutes = prefs.getInt(PREF_WAKEUP_MINUTES, DEFAULT_WAKEUP_MINUTES);
        float currMinSleep = prefs.getFloat(PREF_WAKEUP_MIN_SLEEP_HOURS, DEFAULT_MIN_SLEEP_HOURS);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        final android.widget.TimePicker timePicker = new android.widget.TimePicker(this);
        timePicker.setIs24HourView(android.text.format.DateFormat.is24HourFormat(this));
        if (Build.VERSION.SDK_INT >= 23) {
            timePicker.setHour(currHours);
            timePicker.setMinute(currMinutes);
        } else {
            timePicker.setCurrentHour(currHours);
            timePicker.setCurrentMinute(currMinutes);
        }
        layout.addView(timePicker);

        final EditText minSleepInput = new EditText(this);
        minSleepInput.setHint(R.string.dialog_min_sleep_hint);
        minSleepInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        minSleepInput.setText(String.format(java.util.Locale.US, "%.1f", currMinSleep));
        layout.addView(minSleepInput);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_wakeup_alarm_title)
                .setView(layout)
                .setPositiveButton(R.string.dialog_btn_save, (dialog, which) -> {
                    int selectedHour;
                    int selectedMinute;
                    if (Build.VERSION.SDK_INT >= 23) {
                        selectedHour = timePicker.getHour();
                        selectedMinute = timePicker.getMinute();
                    } else {
                        selectedHour = timePicker.getCurrentHour();
                        selectedMinute = timePicker.getCurrentMinute();
                    }

                    float minSleep = parseFloatOrDefault(minSleepInput.getText().toString(), currMinSleep, 6.0f, 9.0f);

                    prefs.edit()
                            .putBoolean(PREF_WAKEUP_ENABLED, true)
                            .putInt(PREF_WAKEUP_HOURS, selectedHour)
                            .putInt(PREF_WAKEUP_MINUTES, selectedMinute)
                            .putFloat(PREF_WAKEUP_MIN_SLEEP_HOURS, minSleep)
                            .apply();

                    EventLogger.log(this, String.format(java.util.Locale.US, "Wake-Up Goal set to %02d:%02d (Min sleep: %.1fh)", selectedHour, selectedMinute, minSleep));
                    updateWakeupAlarmStatusUI();
                    redrawNotification();
                })
                .setNegativeButton(R.string.dialog_btn_cancel, null)
                .show();
    }

    private int[] parseTimeHHMM(String raw, int defaultHour, int defaultMinute) {
        if (raw == null || raw.trim().isEmpty()) {
            return new int[]{defaultHour, defaultMinute};
        }
        String[] parts = raw.trim().split(":");
        if (parts.length == 2) {
            try {
                int h = Integer.parseInt(parts[0].trim());
                int m = Integer.parseInt(parts[1].trim());
                if (h >= 0 && h <= 23 && m >= 0 && m <= 59) {
                    return new int[]{h, m};
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return new int[]{defaultHour, defaultMinute};
    }

    private float parseFloatOrDefault(String raw, float defaultValue, float min, float max) {
        if (raw == null || raw.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            float val = Float.parseFloat(raw.trim());
            if (val < min || val > max) {
                return defaultValue;
            }
            return val;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void clearWakeupAlarm() {
        SharedPreferences prefs = getSharedPreferences(PREF_SLEEP_TIMER, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_WAKEUP_ENABLED, false).apply();
        EventLogger.log(this, "Wake-Up Alarm disabled");
        updateWakeupAlarmStatusUI();
        redrawNotification();
    }

    private int parseInputOrDefault(String raw, int defaultValue, int min, int max) {
        if (raw == null || raw.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            int val = Integer.parseInt(raw.trim());
            if (val < min || val > max) {
                return defaultValue;
            }
            return val;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void requestExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 31) {
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                EventLogger.log(this, "Opening exact alarm settings");
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
        EventLogger.log(this, "MainActivity new intent");
        startOrRequestNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        EventLogger.log(this, "MainActivity resumed");
        EventLogger.setListener(this);
        refreshEventLog();
        updateWakeupAlarmStatusUI();
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
        EventLogger.log(this, "MainActivity paused");
        EventLogger.setListener(null);
    }

    private void startOrRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            EventLogger.log(this, "Requesting notification permission");
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
            EventLogger.log(this, "Notification permission granted: " + granted);
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
        StringBuilder sb = new StringBuilder();
        for (String event : events) {
            sb.append(event).append("\n");
        }
        if (eventLogText != null) {
            eventLogText.setText(sb.toString());
            scrollToBottom();
        }
    }

    @Override
    public void onEventLogged(String event) {
        if (eventLogText != null) {
            eventLogText.append(event + "\n");
            scrollToBottom();
        }
    }

    private void scrollToBottom() {
        if (scrollView != null) {
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
        }
    }
}
