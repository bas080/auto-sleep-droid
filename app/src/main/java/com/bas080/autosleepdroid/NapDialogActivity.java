package com.bas080.autosleepdroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

public class NapDialogActivity extends Activity {
    private AlertDialog alertDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("sleep_timer", MODE_PRIVATE);
        int savedDuration = prefs.getInt(SleepTimerService.KEY_NAP_DURATION_MINUTES, 20);

        android.view.ContextThemeWrapper dialogContext = new android.view.ContextThemeWrapper(this, R.style.AppTheme);

        final DurationInputView durationInputView = new DurationInputView(dialogContext);
        durationInputView.setPadding(48, 24, 48, 24);
        durationInputView.setTotalMinutes(savedDuration);

        AlertDialog.Builder builder = new AlertDialog.Builder(dialogContext);
        builder.setTitle(R.string.dialog_nap_title);
        builder.setView(durationInputView);
        builder.setPositiveButton(R.string.action_nap, (dialog, which) -> {
            int minutes = durationInputView.getTotalMinutes();
            if (minutes > 0) {
                prefs.edit().putInt(SleepTimerService.KEY_NAP_DURATION_MINUTES, minutes).apply();

                Intent serviceIntent = new Intent(NapDialogActivity.this, SleepTimerService.class);
                serviceIntent.setAction(SleepTimerService.ACTION_START_NAP);
                serviceIntent.putExtra(SleepTimerService.EXTRA_NAP_DURATION_MINUTES, minutes);
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
                finish();
            } else {
                Toast.makeText(NapDialogActivity.this, R.string.toast_duration_invalid, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
        builder.setNegativeButton(R.string.dialog_cancel, (dialog, which) -> finish());
        builder.setOnCancelListener(dialog -> finish());

        alertDialog = builder.create();
        alertDialog.setOnDismissListener(dialog -> {
            if (!isFinishing()) {
                finish();
            }
        });
        alertDialog.show();
    }

    public AlertDialog getAlertDialog() {
        return alertDialog;
    }
}
