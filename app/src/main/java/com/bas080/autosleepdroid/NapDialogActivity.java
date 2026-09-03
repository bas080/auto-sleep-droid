package com.bas080.autosleepdroid;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class NapDialogActivity extends Activity {
    private EditText inputNapHours;
    private EditText inputNapMinutes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nap_dialog);

        inputNapHours = findViewById(R.id.input_nap_hours);
        inputNapMinutes = findViewById(R.id.input_nap_minutes);
        Button btnCancel = findViewById(R.id.btn_nap_cancel);
        Button btnStart = findViewById(R.id.btn_nap_start);

        SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
        int savedDuration = prefs.getInt(SleepTimerService.KEY_NAP_DURATION_MINUTES, 20);
        populateDurationInputs(savedDuration);

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> finish());
        }

        if (btnStart != null) {
            btnStart.setOnClickListener(v -> startNap());
        }
    }

    private void populateDurationInputs(int totalMinutes) {
        if (inputNapHours == null || inputNapMinutes == null) return;
        int h = totalMinutes / 60;
        int m = totalMinutes % 60;
        inputNapHours.setText(h > 0 ? String.valueOf(h) : "");
        inputNapMinutes.setText(m > 0 || h == 0 ? String.valueOf(m) : "");
    }

    private int parseTotalMinutes() {
        if (inputNapHours == null || inputNapMinutes == null) return -1;
        String hStr = inputNapHours.getText().toString().trim();
        String mStr = inputNapMinutes.getText().toString().trim();
        if (hStr.isEmpty() && mStr.isEmpty()) return -1;
        int h = 0;
        int m = 0;
        try {
            if (!hStr.isEmpty()) h = Integer.parseInt(hStr);
            if (!mStr.isEmpty()) m = Integer.parseInt(mStr);
        } catch (NumberFormatException e) {
            return -1;
        }
        if (h < 0 || m < 0) return -1;
        long total = h * 60L + m;
        if (total <= 0 || total > 1440) {
            return -1;
        }
        return (int) total;
    }

    private void startNap() {
        int minutes = parseTotalMinutes();
        if (minutes > 0) {
            SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
            prefs.edit().putInt(SleepTimerService.KEY_NAP_DURATION_MINUTES, minutes).apply();

            Intent serviceIntent = new Intent(this, SleepTimerService.class);
            serviceIntent.setAction(SleepTimerService.ACTION_START_NAP);
            serviceIntent.putExtra(SleepTimerService.EXTRA_NAP_DURATION_MINUTES, minutes);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            finish();
        } else {
            Toast.makeText(this, R.string.toast_duration_invalid, Toast.LENGTH_SHORT).show();
        }
    }
}