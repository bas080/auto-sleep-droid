package com.bas080.autosleepdroid;

import android.app.Application;
import android.util.Log;

public class AutoSleepApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                String logMsg = "CRASH on thread '" + thread.getName() + "': " + Log.getStackTraceString(throwable);
                EventLogger.log(this, EventLogger.LEVEL_HIGH, logMsg);
            } catch (Throwable ignored) {
            }
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });
    }
}
