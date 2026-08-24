package com.bas080.autosleepdroid;

import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class SleepTimerTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        boolean currentlyEnabled = getSharedPreferences("sleep_timer", MODE_PRIVATE)
                .getBoolean("active", true);
        boolean newEnabledState = !currentlyEnabled;

        getSharedPreferences("sleep_timer", MODE_PRIVATE)
                .edit()
                .putBoolean("active", newEnabledState)
                .apply();

        updateTile(newEnabledState);

        Intent intent = new Intent(this, SleepTimerService.class);
        if (newEnabledState) {
            intent.setAction(SleepTimerService.ACTION_TURN_ON);
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } else {
            intent.setAction(SleepTimerService.ACTION_TURN_OFF);
            startService(intent);
        }
    }

    public void updateTile() {
        boolean isEnabled = getSharedPreferences("sleep_timer", MODE_PRIVATE)
                .getBoolean("active", true);
        updateTile(isEnabled);
    }

    private void updateTile(boolean isEnabled) {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }

        tile.setState(isEnabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel(getString(R.string.app_name));
        if (Build.VERSION.SDK_INT >= 23) {
            tile.setIcon(Icon.createWithResource(this, R.drawable.ic_zzz));
        }
        tile.updateTile();
    }
}
