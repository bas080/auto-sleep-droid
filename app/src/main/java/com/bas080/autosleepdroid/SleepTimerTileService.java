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
        boolean isEnabled = getSharedPreferences("sleep_timer", MODE_PRIVATE)
                .getBoolean("active", true);

        Intent intent = new Intent(this, SleepTimerService.class);
        if (isEnabled) {
            intent.setAction(SleepTimerService.ACTION_TURN_OFF);
        } else {
            intent.setAction(SleepTimerService.ACTION_TURN_ON);
        }

        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        updateTile();
    }

    public void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }

        boolean isEnabled = getSharedPreferences("sleep_timer", MODE_PRIVATE)
                .getBoolean("active", true);

        tile.setState(isEnabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel(getString(R.string.app_name));
        tile.updateTile();
    }
}
