package com.bas080.autosleepdroid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (Intent.ACTION_BOOT_COMPLETED != action) {
            EventLogger.log(context, "BootReceiver received action ${action ?: "null"}")
            return
        }

        EventLogger.log(context, "Boot completed")

        val serviceIntent = Intent(context, MainService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    context.startForegroundService(serviceIntent)
                } catch (e: IllegalStateException) {
                    EventLogger.log(context, "startForegroundService failed on boot: ${e.message}")
                    context.startService(serviceIntent)
                } catch (e: SecurityException) {
                    EventLogger.log(context, "startForegroundService failed on boot: ${e.message}")
                    context.startService(serviceIntent)
                }
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: IllegalStateException) {
            EventLogger.log(context, "Failed to start service on boot: ${e.message}")
        } catch (e: SecurityException) {
            EventLogger.log(context, "Failed to start service on boot: ${e.message}")
        }
    }
}
