package com.bas080.autosleepdroid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            EventLogger.log(context, "BootReceiver received action: " + (intent != null ? intent.getAction() : "null"));
            return;
        }

        EventLogger.log(context, "Boot completed event received");

        Intent serviceIntent = new Intent(context, SleepTimerService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}
