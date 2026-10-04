package com.bas080.autosleepdroid

import android.widget.Toast

internal object MainActivityControlsHandler {

    private const val MAX_TIMER_HOURS = 12
    private const val TIMER_STEP_MINUTES = 5
    private const val MAX_MIN_SLEEP_HOURS = 16
    private const val MIN_SLEEP_STEP_MINUTES = 15
    private const val MAX_HC_HOURS = 2
    private const val HC_STEP_MINUTES = 5

    fun setupConfigControls(activity: MainActivity) {
        setupTimerControls(activity)
        setupAutoTimerControls(activity)
        setupWakeGoalControls(activity)
        setupHealthConnectControls(activity)
    }

    private fun setupTimerControls(activity: MainActivity) {
        activity.rowEnableTimer?.setOnCheckedChangeListener { isChecked ->
            activity.preferenceManager?.edit()?.putBoolean(PreferenceKeys.KEY_ACTIVE, isChecked)?.apply()
            val pm = activity.preferenceManager
            val goalEnabled = pm?.getBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, false) ?: false
            val healthConnectEnabled = pm?.getBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, false) ?: false
            MainActivityViewHelpers.updateInputEnabledStates(activity, goalEnabled, healthConnectEnabled)
            val msg = if (isChecked) "Timer enabled from UI" else "Timer disabled from UI"
            EventLogger.log(activity, EventLogger.LEVEL_HIGH, msg)
        }

        activity.inputDuration?.setOnClickListener {
            val spec = DurationDialogSpec(
                R.string.label_duration,
                PreferenceKeys.KEY_DURATION_MINUTES,
                AppDefaults.DURATION_MINUTES,
                MainActivity.DurationPickerBounds(0, MAX_TIMER_HOURS, TIMER_STEP_MINUTES)
            )
            MainActivityDialogs.showDurationDialog(activity, spec) { minutes ->
                activity.textDurationValue?.text = DurationUtils.formatDurationString(minutes)
            }
        }
    }

    private fun setupAutoTimerControls(activity: MainActivity) {
        activity.rowAutoTimer?.setOnCheckedChangeListener { isChecked ->
            val pm = activity.preferenceManager ?: return@setOnCheckedChangeListener
            val editor = pm.edit()
            editor.putBoolean(PreferenceKeys.KEY_AUTO_TIMER_ENABLED, isChecked)
            if (isChecked) {
                val dndActive = MainActivityViewHelpers.isDndActive(activity)
                editor.putBoolean(PreferenceKeys.KEY_ACTIVE, dndActive)
                activity.rowEnableTimer?.isChecked = dndActive
            }
            editor.apply()
            val logMsg = if (isChecked) "Auto sleep timer (DND) enabled" else "Auto sleep timer (DND) disabled"
            EventLogger.log(activity, EventLogger.LEVEL_HIGH, logMsg)
        }
    }

    private fun setupWakeGoalControls(activity: MainActivity) {
        activity.rowEnableGoal?.setOnCheckedChangeListener { isChecked ->
            activity.preferenceManager?.sharedPreferences?.edit()
                ?.putBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, isChecked)
                ?.remove(PreferenceKeys.KEY_WAKEUP_LAST_SCHEDULED_MS)
                ?.apply()
            val hcEnabled = activity.preferenceManager
                ?.getBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, false) ?: false
            MainActivityViewHelpers.updateInputEnabledStates(activity, isChecked, hcEnabled)
            val msg = if (isChecked) "Wake-up goal enabled" else "Wake-up goal disabled"
            EventLogger.log(activity, EventLogger.LEVEL_HIGH, msg)
        }

        activity.btnTargetTime?.setOnClickListener { MainActivityDialogs.showTargetTimeDialog(activity) }
        activity.btnCurrentWakeTime?.setOnClickListener { MainActivityDialogs.showCurrentWakeTimeDialog(activity) }

        activity.inputMinSleep?.setOnClickListener {
            val spec = DurationDialogSpec(
                R.string.label_min_sleep,
                PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES,
                AppDefaults.MIN_SLEEP_DURATION_MINUTES,
                MainActivity.DurationPickerBounds(0, MAX_MIN_SLEEP_HOURS, MIN_SLEEP_STEP_MINUTES)
            )
            MainActivityDialogs.showDurationDialog(activity, spec) { minutes ->
                activity.preferenceManager?.edit()?.remove(PreferenceKeys.KEY_WAKEUP_LAST_SCHEDULED_MS)?.apply()
                activity.textMinSleepValue?.text = DurationUtils.formatDurationString(minutes)
            }
        }
    }

    private fun setupHealthConnectControls(activity: MainActivity) {
        activity.rowHealthConnect?.setOnCheckedChangeListener { isChecked ->
            handleHealthConnectToggle(activity, true, isChecked)
        }

        activity.inputHcMinDuration?.setOnClickListener {
            val spec = DurationDialogSpec(
                R.string.label_hc_min_duration,
                PreferenceKeys.KEY_HC_MIN_DURATION_MINUTES,
                AppDefaults.HC_MIN_DURATION_MINUTES,
                MainActivity.DurationPickerBounds(0, MAX_HC_HOURS, HC_STEP_MINUTES)
            )
            MainActivityDialogs.showDurationDialog(activity, spec) { minutes ->
                activity.textHcMinDurationValue?.text = DurationUtils.formatDurationString(minutes)
            }
        }
    }

    private fun handleHealthConnectToggle(activity: MainActivity, isUserInitiated: Boolean, isChecked: Boolean) {
        if (isChecked) {
            enableHealthConnect(activity, isUserInitiated)
        } else {
            disableHealthConnect(activity, isUserInitiated)
        }
        val goalEnabled = activity.preferenceManager
            ?.getBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, false) ?: false
        MainActivityViewHelpers.updateInputEnabledStates(activity, goalEnabled, isChecked)
    }

    private fun enableHealthConnect(activity: MainActivity, isUserInitiated: Boolean) {
        if (!HealthConnectManager.isHealthConnectAvailable(activity)) {
            activity.rowHealthConnect?.isChecked = false
            Toast.makeText(activity, R.string.toast_health_connect_not_available, Toast.LENGTH_SHORT).show()
            EventLogger.log(activity, EventLogger.LEVEL_HIGH, "Health Connect requested but SDK is unavailable")
            return
        }
        activity.preferenceManager?.edit()?.putBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, true)?.apply()
        if (isUserInitiated) {
            Toast.makeText(activity, R.string.toast_health_connect_enabled, Toast.LENGTH_SHORT).show()
            activity.isRequestingHealthConnectPermission = true
            requestHealthConnectPermission(activity)
        }
    }

    private fun requestHealthConnectPermission(activity: MainActivity) {
        HealthConnectManager.hasSleepWritePermission(activity) { hasPermission ->
            if (!hasPermission) {
                EventLogger.log(
                    activity,
                    EventLogger.LEVEL_HIGH,
                    "Health Connect sync enabled; opening permissions settings"
                )
                HealthConnectManager.openHealthConnectPermissions(activity)
            } else {
                activity.isRequestingHealthConnectPermission = false
                EventLogger.log(activity, EventLogger.LEVEL_HIGH, "Health Connect sync enabled")
            }
        }
    }

    private fun disableHealthConnect(activity: MainActivity, isUserInitiated: Boolean) {
        activity.preferenceManager?.edit()?.putBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, false)?.apply()
        if (isUserInitiated) {
            activity.isRequestingHealthConnectPermission = false
            EventLogger.log(activity, EventLogger.LEVEL_HIGH, "Health Connect sync disabled; revoking permissions")
            Toast.makeText(activity, R.string.toast_health_connect_disabled, Toast.LENGTH_SHORT).show()
            HealthConnectManager.revokeAllPermissions(activity)
        }
    }
}
