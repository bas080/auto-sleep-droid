package com.bas080.autosleepdroid;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

public class NapDialogActivity extends Activity {
    private DurationInputView inputNapDuration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nap_dialog);

        inputNapDuration = findViewById(R.id.input_nap_duration);
        Button btnCancel = findViewById(R.id.btn_nap_cancel);
        Button btnStart = findViewById(R.id.btn_nap_start);

        SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
        int savedDuration = prefs.getInt(SleepTimerService.KEY_NAP_DURATION_MINUTES, 20);
        if (inputNapDuration != null) {
            inputNapDuration.setTotalMinutes(savedDuration);
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> finish());
        }

        if (btnStart != null) {
            btnStart.setOnClickListener(v -> startNap());
        }
    }

    private void startNap() {
        int minutes = inputNapDuration != null ? inputNapDuration.getTotalMinutes() : -1;
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