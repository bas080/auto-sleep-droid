package com.bas080.autosleepdroid

import android.app.Application
import android.util.Log

class AutoSleepApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val logMsg = "CRASH on thread '${thread.name}': ${Log.getStackTraceString(throwable)}"
                EventLogger.log(this, EventLogger.LEVEL_HIGH, logMsg)

                val prefs = getSharedPreferences("crash_reports", MODE_PRIVATE)
                prefs.edit().putString("pending_crash_report", logMsg).commit()
            } catch (ignored: Throwable) {
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
