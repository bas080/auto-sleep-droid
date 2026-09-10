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
        context.startForegroundService(serviceIntent)
    }
}
