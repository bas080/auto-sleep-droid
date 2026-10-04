package com.bas080.autosleepdroid

import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.view.View
import android.view.ViewGroup
import android.widget.Switch

internal object MainActivityViewHelpers {

    private const val ALPHA_ENABLED = 1.0f
    private const val ALPHA_DISABLED = 0.38f

    fun updateInputEnabledStates(
        activity: MainActivity,
        goalEnabled: Boolean,
        healthConnectEnabled: Boolean
    ) {
        setRowEnabled(activity.headerDnd, true)
        setRowEnabled(activity.headerTimer, true)
        setRowEnabled(activity.headerAlarm, true)
        setRowEnabled(activity.headerHealthConnect, true)
        setRowEnabled(activity.headerBackup, true)
        setRowEnabled(activity.headerAbout, true)

        setRowEnabled(activity.rowEnableTimer, true)
        setRowEnabled(activity.inputDuration, true)
        setRowEnabled(activity.rowAutoTimer, true)
        setRowEnabled(activity.rowEnableGoal, true)

        setRowEnabled(activity.btnTargetTime, goalEnabled)
        setRowEnabled(activity.btnCurrentWakeTime, goalEnabled)
        setRowEnabled(activity.inputMinSleep, goalEnabled)
        setRowEnabled(activity.rowHealthConnect, true)
        setRowEnabled(activity.inputHcMinDuration, healthConnectEnabled)
        setRowEnabled(activity.btnExport, true)
        setRowEnabled(activity.btnImport, true)
        setRowEnabled(activity.btnVersion, true)
        setRowEnabled(activity.btnFeedback, true)

        activity.goalContainer?.visibility = View.VISIBLE
    }

    private fun setRowEnabled(view: View?, enabled: Boolean) {
        view ?: return
        view.isEnabled = enabled
        if (view !is SettingRowView) {
            view.isClickable = enabled
            view.isFocusable = enabled
            view.alpha = if (enabled) ALPHA_ENABLED else ALPHA_DISABLED
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    setChildViewsEnabled(view.getChildAt(i), enabled)
                }
            }
        }
    }

    private fun setChildViewsEnabled(view: View?, enabled: Boolean) {
        view ?: return
        view.isEnabled = enabled
        if (view is Switch) {
            view.isClickable = enabled
            view.isFocusable = enabled
        } else {
            view.isClickable = false
            view.isFocusable = false
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                setChildViewsEnabled(view.getChildAt(i), enabled)
            }
        }
    }

    fun isDndActive(context: Context): Boolean {
        val nm = context.getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager?
        if (nm != null) {
            return nm.currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL
        }
        return false
    }
}
