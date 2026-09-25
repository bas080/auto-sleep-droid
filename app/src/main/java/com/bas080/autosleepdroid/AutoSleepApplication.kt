package com.bas080.autosleepdroid

import android.app.Application
import android.util.Log

class AutoSleepApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val timeStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                val logMsg = "CRASH at $timeStr on thread '${thread.name}':\n${Log.getStackTraceString(throwable)}"
                EventLogger.log(this, EventLogger.LEVEL_HIGH, logMsg)

                val prefs = getSharedPreferences("crash_reports", MODE_PRIVATE)
                prefs.edit().putString("pending_crash_report", logMsg).commit()
            } catch (ignored: Throwable) {
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
