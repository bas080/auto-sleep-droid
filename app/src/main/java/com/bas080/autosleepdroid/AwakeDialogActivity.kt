package com.bas080.autosleepdroid

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ContextThemeWrapper

class AwakeDialogActivity : Activity() {
    var alertDialog: AlertDialog? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dialogContext = ContextThemeWrapper(this, R.style.AppTheme)

        val builder = AlertDialog.Builder(dialogContext)
        builder.setTitle(R.string.dialog_awake_title)
        builder.setMessage(R.string.dialog_awake_message)
        builder.setPositiveButton(R.string.action_awake) { _, _ ->
            val serviceIntent = Intent(this@AwakeDialogActivity, MainService::class.java).apply {
                action = MainService.ACTION_AWAKE
            }
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            finish()
        }
        builder.setNegativeButton(R.string.dialog_cancel) { _, _ -> finish() }
        builder.setOnCancelListener { finish() }

        val dialog = builder.create()
        dialog.setOnDismissListener {
            if (!isFinishing) {
                finish()
            }
        }
        alertDialog = dialog
        dialog.show()
    }
}
