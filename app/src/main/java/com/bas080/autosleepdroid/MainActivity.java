package com.bas080.autosleepdroid;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

public class MainActivity extends Activity implements EventLogger.Listener {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 100;
    private boolean accessSettingsOpened;
    private ScrollView scrollView;
    private TextView eventLogText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        scrollView = findViewById(R.id.event_scroll_view);
        eventLogText = findViewById(R.id.event_log_text);

        EventLogger.log(this, "MainActivity created");

        startOrRequestNotificationPermission();
        requestExactAlarmPermissionIfNeeded();
    }

    private void requestExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 31) {
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                EventLogger.log(this, "Opening exact alarm permission settings");
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
            EventLogger.log(this, "Requesting POST_NOTIFICATIONS permission");
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
            EventLogger.log(this, "Permission result: POST_NOTIFICATIONS granted = " + granted);
            startTimerService();
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
