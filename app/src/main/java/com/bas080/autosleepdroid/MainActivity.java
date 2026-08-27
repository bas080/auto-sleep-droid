package com.bas080.autosleepdroid;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements EventLogger.Listener {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 100;
    private ScrollView scrollView;
    private TextView eventLogText;
    private TextView tvWakeUpGoalStatus;
    private Button btnSetWakeUpGoal;
    private Button btnClearGoal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        scrollView = findViewById(R.id.event_scroll_view);
        eventLogText = findViewById(R.id.event_log_text);
        tvWakeUpGoalStatus = findViewById(R.id.tv_wake_up_goal_status);
        btnSetWakeUpGoal = findViewById(R.id.btn_set_wake_up_goal);
        btnClearGoal = findViewById(R.id.btn_clear_goal);

        if (btnSetWakeUpGoal != null) {
            btnSetWakeUpGoal.setOnClickListener(v -> showSetGoalDialog());
        }
        if (btnClearGoal != null) {
            btnClearGoal.setOnClickListener(v -> clearWakeUpGoal());
        }

        EventLogger.log(this, "MainActivity created");

        startOrRequestNotificationPermission();
        requestExactAlarmPermissionIfNeeded();
        updateGoalStatusView();
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
        updateGoalStatusView();
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

    private void showSetGoalDialog() {
        SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
        int defaultHour = prefs.getInt("wake_up_goal_hour", 6);
        int defaultMinute = prefs.getInt("wake_up_goal_minute", 30);

        TimePickerDialog timePickerDialog = new TimePickerDialog(this,
                (view, hourOfDay, minute) -> showMinSleepDurationDialog(hourOfDay, minute),
                defaultHour, defaultMinute, false);
        timePickerDialog.setTitle(getString(R.string.dialog_set_goal_title));
        timePickerDialog.show();
    }

    private void showMinSleepDurationDialog(int goalHour, int goalMinute) {
        SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
        int minSleepMin = prefs.getInt("min_sleep_duration_minutes", 450);
        double defaultHours = minSleepMin / 60.0;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.dialog_min_sleep_title));

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format(Locale.US, "%.1f", defaultHours));
        builder.setView(input);

        builder.setPositiveButton(getString(R.string.dialog_ok), (dialog, which) -> {
            String text = input.getText().toString().trim();
            double hours = 7.5;
            if (!text.isEmpty()) {
                try {
                    hours = Double.parseDouble(text);
                } catch (NumberFormatException ignored) {
                }
            }
            int minMinutes = (int) Math.round(hours * 60.0);
            saveWakeUpGoal(goalHour, goalMinute, minMinutes);
        });
        builder.setNegativeButton(getString(R.string.dialog_cancel), (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void saveWakeUpGoal(int goalHour, int goalMinute, int minSleepMinutes) {
        SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
        prefs.edit()
                .putBoolean("wake_up_goal_enabled", true)
                .putInt("wake_up_goal_hour", goalHour)
                .putInt("wake_up_goal_minute", goalMinute)
                .putInt("min_sleep_duration_minutes", minSleepMinutes)
                .apply();

        String formattedGoalTime = formatTime(goalHour, goalMinute);
        double hours = minSleepMinutes / 60.0;
        EventLogger.log(this, "Smart Wake-Up Goal set to " + formattedGoalTime + " (min sleep: " + String.format(Locale.US, "%.1fh", hours) + ")");

        updateGoalStatusView();
        redrawNotification();
    }

    private void clearWakeUpGoal() {
        SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
        prefs.edit()
                .putBoolean("wake_up_goal_enabled", false)
                .remove("last_scheduled_alarm_hour")
                .remove("last_scheduled_alarm_minute")
                .apply();

        try {
            Intent dismissIntent = new Intent(AlarmClock.ACTION_DISMISS_ALARM);
            dismissIntent.putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_LABEL);
            dismissIntent.putExtra(AlarmClock.EXTRA_MESSAGE, "Auto Sleep");
            dismissIntent.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
            dismissIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(dismissIntent);
        } catch (Exception e) {
            EventLogger.log(this, "Unable to dismiss Auto Sleep alarm: " + e.getMessage());
        }

        EventLogger.log(this, "Smart Wake-Up Goal cleared");
        updateGoalStatusView();
        redrawNotification();
    }

    private void updateGoalStatusView() {
        SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
        boolean enabled = prefs.getBoolean("wake_up_goal_enabled", false);
        if (!enabled) {
            if (tvWakeUpGoalStatus != null) {
                tvWakeUpGoalStatus.setText(getString(R.string.wake_up_goal_disabled));
            }
            return;
        }

        int goalHour = prefs.getInt("wake_up_goal_hour", 6);
        int goalMin = prefs.getInt("wake_up_goal_minute", 30);
        int alarmHour = prefs.getInt("last_scheduled_alarm_hour", -1);
        int alarmMin = prefs.getInt("last_scheduled_alarm_minute", -1);

        String formattedGoalTime = formatTime(goalHour, goalMin);

        if (alarmHour != -1 && alarmMin != -1) {
            String formattedAlarmTime = formatTime(alarmHour, alarmMin);
            tvWakeUpGoalStatus.setText("Goal: " + formattedGoalTime + " • Tonight's Alarm: " + formattedAlarmTime);
        } else {
            int minSleepMin = prefs.getInt("min_sleep_duration_minutes", 450);
            double minSleepHours = minSleepMin / 60.0;
            tvWakeUpGoalStatus.setText("Goal: " + formattedGoalTime + " • Min Sleep: " + String.format(Locale.US, "%.1fh", minSleepHours));
        }
    }

    private String formatTime(int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        java.text.DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);
        return timeFormat.format(cal.getTime());
    }
}
