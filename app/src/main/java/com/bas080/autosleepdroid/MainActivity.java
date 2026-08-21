package com.bas080.autosleepdroid;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.content.ComponentName;
import android.os.Build;
import android.os.Bundle;

public class MainActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 100;
    private boolean accessSettingsOpened;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startOrRequestNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (accessSettingsOpened) {
            accessSettingsOpened = false;
            startOrRequestNotificationPermission();
        }
    }

    private void startOrRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST);
            return;
        }
        if (hasNotificationAccess()) {
            startTimerService();
        } else if (!accessSettingsOpened) {
            accessSettingsOpened = true;
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
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
        finish();
    }

    private boolean hasNotificationAccess() {
        android.app.NotificationManager manager =
                (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        ComponentName component = new ComponentName(this, MediaSessionAccessService.class);
        return manager != null && manager.isNotificationListenerAccessGranted(component);
    }
}
