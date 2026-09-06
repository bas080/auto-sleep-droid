package com.bas080.autosleepdroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;

public class AwakeDialogActivity extends Activity {
    private AlertDialog alertDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        android.view.ContextThemeWrapper dialogContext = new android.view.ContextThemeWrapper(this, R.style.AppTheme);

        AlertDialog.Builder builder = new AlertDialog.Builder(dialogContext);
        builder.setTitle(R.string.dialog_awake_title);
        builder.setMessage(R.string.dialog_awake_message);
        builder.setPositiveButton(R.string.action_awake, (dialog, which) -> {
            Intent serviceIntent = new Intent(AwakeDialogActivity.this, SleepTimerService.class);
            serviceIntent.setAction(SleepTimerService.ACTION_AWAKE);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            finish();
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
