package com.bas080.autosleepdroid;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.text.InputType;

import java.util.List;

public class MainActivity extends Activity implements EventLogger.Listener {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 100;
    private boolean accessSettingsOpened;
    private ScrollView scrollView;
    private TextView eventLogText;
    private Button btnSetPostFadeout;
    private Button btnClearPostFadeout;
    private TextView postFadeoutStatusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        scrollView = findViewById(R.id.event_scroll_view);
        eventLogText = findViewById(R.id.event_log_text);
        btnSetPostFadeout = findViewById(R.id.btn_set_post_fadeout);
        btnClearPostFadeout = findViewById(R.id.btn_clear_post_fadeout);
        postFadeoutStatusText = findViewById(R.id.post_fadeout_status_text);

        if (btnSetPostFadeout != null) {
            btnSetPostFadeout.setOnClickListener(v -> showPostFadeoutInputDialog());
        }
        if (btnClearPostFadeout != null) {
            btnClearPostFadeout.setOnClickListener(v -> clearPostFadeoutResumption());
        }

        EventLogger.log(this, "MainActivity created");

        startOrRequestNotificationPermission();
        requestExactAlarmPermissionIfNeeded();
    }

    private void showPostFadeoutInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.action_set_post_fadeout);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(R.string.post_fadeout_input_prompt);
        builder.setView(input);

        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            String text = input.getText().toString().trim();
            try {
                int hours = Integer.parseInt(text);
                if (hours >= 1 && hours <= 12) {
                    setPostFadeoutResumption(hours);
                } else if (hours == 0) {
                    clearPostFadeoutResumption();
                }
            } catch (NumberFormatException ignored) {
            }
        });

        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void setPostFadeoutResumption(int hours) {
        Intent serviceIntent = new Intent(this, SleepTimerService.class);
        serviceIntent.setAction(SleepTimerService.ACTION_SET_POST_FADEOUT_RESUMPTION);
        serviceIntent.putExtra(SleepTimerService.EXTRA_POST_FADEOUT_HOURS, hours);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        updatePostFadeoutStatusText();
    }

    private void clearPostFadeoutResumption() {
        Intent serviceIntent = new Intent(this, SleepTimerService.class);
        serviceIntent.setAction(SleepTimerService.ACTION_CLEAR_POST_FADEOUT_RESUMPTION);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        updatePostFadeoutStatusText();
    }

    private void updatePostFadeoutStatusText() {
        if (postFadeoutStatusText == null) {
            return;
        }
        SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(SleepTimerService.KEY_POST_FADEOUT_ENABLED, false);
        int hours = prefs.getInt(SleepTimerService.KEY_POST_FADEOUT_HOURS, 8);

        if (enabled && hours >= 1 && hours <= 12) {
            postFadeoutStatusText.setText(getString(R.string.post_fadeout_status_on, hours));
        } else {
            postFadeoutStatusText.setText(getString(R.string.post_fadeout_status_off));
        }
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
        updatePostFadeoutStatusText();
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
