package com.bas080.autosleepdroid;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class SleepTileService extends TileService {
    private static final String PREFERENCES = "sleep_timer";
    private static final String KEY_ENABLED = "active";

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    @Override
    public void onClick() {
        super.onClick();
        SharedPreferences preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        boolean enabled = preferences.getBoolean(KEY_ENABLED, true);

        Intent serviceIntent = new Intent(this, SleepTimerService.class);
        if (enabled) {
            serviceIntent.setAction(SleepTimerService.ACTION_TURN_OFF);
        } else {
            serviceIntent.setAction(SleepTimerService.ACTION_TURN_ON);
        }
        startForegroundService(serviceIntent);

        updateTileState();
    }

    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }

        SharedPreferences preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        boolean enabled = preferences.getBoolean(KEY_ENABLED, true);

        if (enabled) {
            tile.setState(Tile.STATE_ACTIVE);
            tile.setSubtitle(getString(R.string.tile_subtitle_on));
        } else {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setSubtitle(getString(R.string.tile_subtitle_off));
        }
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_zzz));
        tile.updateTile();
    }
}
