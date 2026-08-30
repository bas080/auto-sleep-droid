package com.bas080.autosleepdroid;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34})
public class SleepTileServiceTest {

    private Context context;
    private SharedPreferences preferences;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        preferences = context.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
    }

    @Test
    public void testTileStateWhenActive() {
        preferences.edit().putBoolean("active", true).commit();

        ServiceController<SleepTileService> controller = Robolectric.buildService(SleepTileService.class);
        SleepTileService service = controller.create().get();

        service.onStartListening();

        Tile tile = service.getQsTile();
        assertNotNull(tile);
        assertEquals(Tile.STATE_ACTIVE, tile.getState());
    }

    @Test
    public void testTileStateWhenInactive() {
        preferences.edit().putBoolean("active", false).commit();

        ServiceController<SleepTileService> controller = Robolectric.buildService(SleepTileService.class);
        SleepTileService service = controller.create().get();

        service.onStartListening();

        Tile tile = service.getQsTile();
        assertNotNull(tile);
        assertEquals(Tile.STATE_INACTIVE, tile.getState());
    }

    @Test
    public void testClickTogglesTurnOffWhenActive() {
        preferences.edit().putBoolean("active", true).commit();

        ServiceController<SleepTileService> controller = Robolectric.buildService(SleepTileService.class);
        SleepTileService service = controller.create().get();

        service.onClick();

        Intent nextService = ShadowApplication.getInstance().getNextStartedService();
        assertNotNull(nextService);
        assertEquals(SleepTimerService.ACTION_TURN_OFF, nextService.getAction());
    }

    @Test
    public void testClickTogglesTurnOnWhenInactive() {
        preferences.edit().putBoolean("active", false).commit();

        ServiceController<SleepTileService> controller = Robolectric.buildService(SleepTileService.class);
        SleepTileService service = controller.create().get();

        service.onClick();

        Intent nextService = ShadowApplication.getInstance().getNextStartedService();
        assertNotNull(nextService);
        assertEquals(SleepTimerService.ACTION_TURN_ON, nextService.getAction());
    }
}
