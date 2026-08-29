package com.bas080.autosleepdroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TimePicker;

import java.util.Calendar;

public class GoalSettingsDialogActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
        boolean goalEnabled = prefs.getBoolean("wake_up_goal_enabled", false);
        int defaultGoalHour = prefs.getInt("wake_up_goal_hour", 6);
        int defaultGoalMin = prefs.getInt("wake_up_goal_minute", 30);
        int minSleepMin = prefs.getInt("min_sleep_duration_minutes", 450);

        TextView titleView = new TextView(this);
        titleView.setText(R.string.dialog_set_goal_title);
        titleView.setTextSize(18.0f);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        titleView.setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(8));

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dpToPx(20), dpToPx(8), dpToPx(20), dpToPx(8));

        TextView labelMinSleep = new TextView(this);
        labelMinSleep.setText(R.string.dialog_min_sleep_title);
        labelMinSleep.setTextSize(14.0f);
        labelMinSleep.setGravity(Gravity.START);
        labelMinSleep.setPadding(0, dpToPx(4), 0, dpToPx(4));
        container.addView(labelMinSleep);

        final EditText inputMinSleep = new EditText(this);
        inputMinSleep.setInputType(InputType.TYPE_CLASS_TEXT);
        inputMinSleep.setSingleLine(true);
        inputMinSleep.setGravity(Gravity.START);
        inputMinSleep.setText(DurationUtils.formatDurationString(minSleepMin));
        container.addView(inputMinSleep);

        final TimePicker timePicker = new TimePicker(this);
        timePicker.setIs24HourView(false);
        timePicker.setPadding(0, dpToPx(12), 0, 0);
        if (Build.VERSION.SDK_INT >= 23) {
            timePicker.setHour(defaultGoalHour);
            timePicker.setMinute(defaultGoalMin);
        } else {
            timePicker.setCurrentHour(defaultGoalHour);
            timePicker.setCurrentMinute(defaultGoalMin);
        }
        container.addView(timePicker);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCustomTitle(titleView);
        builder.setView(container);

        builder.setPositiveButton(getString(R.string.dialog_ok), (dialog, which) -> {
            String text = inputMinSleep.getText().toString().trim();
            int minMinutes = DurationUtils.parseDurationMinutes(text, DurationUtils.DefaultUnit.HOURS);
            if (minMinutes <= 0) {
                minMinutes = 450;
            }
            int goalHour = Build.VERSION.SDK_INT >= 23 ? timePicker.getHour() : timePicker.getCurrentHour();
            int goalMin = Build.VERSION.SDK_INT >= 23 ? timePicker.getMinute() : timePicker.getCurrentMinute();

            saveWakeUpGoal(goalHour, goalMin, minMinutes);
            finish();
        });

        builder.setNegativeButton(getString(R.string.dialog_stop), (dialog, which) -> {
            clearWakeUpGoal();
            finish();
        });

        builder.setOnCancelListener(dialog -> finish());

        AlertDialog alertDialog = builder.create();
        alertDialog.show();

        if (!goalEnabled) {
            alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void saveWakeUpGoal(int goalHour, int goalMinute, int minSleepMinutes) {
        SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
        prefs.edit()
                .putBoolean("wake_up_goal_enabled", true)
                .putInt("wake_up_goal_hour", goalHour)
                .putInt("wake_up_goal_minute", goalMinute)
                .putInt("min_sleep_duration_minutes", minSleepMinutes)
                .remove(SleepTimerService.KEY_WAKEUP_LAST_SCHEDULED_MS)
                .apply();

        String formattedGoalTime = formatTime(goalHour, goalMinute);
        String formattedMinSleep = DurationUtils.formatDurationString(minSleepMinutes);
        EventLogger.log(this, "Smart Wake-Up Goal set to " + formattedGoalTime + " (min sleep: " + formattedMinSleep + ")");
        android.widget.Toast.makeText(this, getString(R.string.toast_goal_set, formattedGoalTime), android.widget.Toast.LENGTH_SHORT).show();

        Intent redrawIntent = new Intent(this, SleepTimerService.class);
        redrawIntent.setAction(SleepTimerService.ACTION_REDRAW_NOTIFICATION);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(redrawIntent);
            } else {
                startService(redrawIntent);
            }
        } catch (Exception e) {
            EventLogger.log(this, "Failed to redraw notification service: " + e.getMessage());
        }
    }

    private void clearWakeUpGoal() {
        SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
        prefs.edit()
                .putBoolean("wake_up_goal_enabled", false)
                .remove(SleepTimerService.KEY_WAKEUP_LAST_SCHEDULED_MS)
                .apply();

        Intent clearIntent = new Intent(this, SleepTimerService.class);
        clearIntent.setAction(SleepTimerService.ACTION_CLEAR_GOAL);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(clearIntent);
            } else {
                startService(clearIntent);
            }
        } catch (Exception e) {
            EventLogger.log(this, "Failed to clear goal service: " + e.getMessage());
        }

        EventLogger.log(this, "Smart Wake-Up Goal cleared");
        android.widget.Toast.makeText(this, R.string.toast_goal_stopped, android.widget.Toast.LENGTH_SHORT).show();
    }

    private String formatTime(int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        java.text.DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);
        return timeFormat.format(cal.getTime());
    }
}
