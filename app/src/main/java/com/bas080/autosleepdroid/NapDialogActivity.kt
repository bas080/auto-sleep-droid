package com.bas080.autosleepdroid

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.widget.Toast

class NapDialogActivity : Activity() {
    var alertDialog: AlertDialog? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(PreferenceKeys.PREFERENCES_NAME, MODE_PRIVATE)
        val savedDuration = prefs.getInt(PreferenceKeys.KEY_NAP_DURATION_MINUTES, 20)

        val dialogContext = ContextThemeWrapper(this, R.style.AppTheme)

        val durationInputView = DurationInputView(dialogContext)
        durationInputView.configure(0, 3, 5)
        durationInputView.setPadding(48, 24, 48, 24)
        durationInputView.setTotalMinutes(savedDuration)

        val builder = AlertDialog.Builder(dialogContext)
        builder.setTitle(R.string.dialog_nap_title)
        builder.setView(durationInputView)
        builder.setPositiveButton(R.string.action_nap) { _, _ ->
            val minutes = durationInputView.getTotalMinutes()
            if (minutes > 0) {
                prefs.edit().putInt(MainService.KEY_NAP_DURATION_MINUTES, minutes).apply()

                val serviceIntent = Intent(this@NapDialogActivity, MainService::class.java).apply {
                    action = MainService.ACTION_START_NAP
                    putExtra(MainService.EXTRA_NAP_DURATION_MINUTES, minutes)
                }
                if (Build.VERSION.SDK_INT >= 26) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                finish()
            } else {
                Toast.makeText(this@NapDialogActivity, R.string.toast_duration_invalid, Toast.LENGTH_SHORT).show()
                finish()
            }
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
