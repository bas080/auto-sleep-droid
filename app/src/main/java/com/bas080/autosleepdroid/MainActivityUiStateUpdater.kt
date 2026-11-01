package com.bas080.autosleepdroid

import java.util.Calendar

internal object MainActivityUiStateUpdater {

    fun registerPreferenceListeners(activity: MainActivity) {
        val pm = activity.preferenceManager ?: return

        if (activity.uiEffectsHandle == null) {
            activity.uiEffectsHandle = pm.watchEffects(
                PreferenceManager.PreferenceEffect { getter ->
                    updateTimerUi(activity, getter)
                },
                PreferenceManager.PreferenceEffect { getter ->
                    updateGoalUi(activity, getter)
                },
                PreferenceManager.PreferenceEffect { getter ->
                    updateHealthConnectUi(activity, getter)
                }
            )
        }
    }

    fun unregisterPreferenceListeners(activity: MainActivity) {
        activity.uiEffectsHandle?.dispose()
        activity.uiEffectsHandle = null
    }

    fun updateTimerUi(activity: MainActivity, getter: PreferenceGetter) {
        val active = getter.getBoolean(PreferenceKeys.KEY_ACTIVE, true)
        val durationMinutes = getter.getInt(PreferenceKeys.KEY_DURATION_MINUTES, AppDefaults.DURATION_MINUTES)
        val autoTimer = getter.getBoolean(PreferenceKeys.KEY_AUTO_TIMER_ENABLED, false)

        activity.rowEnableTimer?.isChecked = active
        activity.textDurationValue?.text = getComputedDurationString(
            activity.preferenceManager,
            PreferenceKeys.KEY_DURATION_MINUTES,
            durationMinutes
        )
        activity.rowAutoTimer?.isChecked = autoTimer
    }

    fun updateGoalUi(activity: MainActivity, getter: PreferenceGetter) {
        val goalEnabled = getter.getBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, false)
        val healthConnectEnabled = getter.getBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, false)
        val goalHour = getter.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR, AppDefaults.WAKE_UP_GOAL_HOUR)
        val goalMin = getter.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE, AppDefaults.WAKE_UP_GOAL_MINUTE)
        val currentHour = getter.getInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, goalHour)
        val currentMin = getter.getInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, goalMin)
        val minSleepMin = getter.getInt(
            PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES,
            AppDefaults.MIN_SLEEP_DURATION_MINUTES
        )

        activity.rowEnableGoal?.isChecked = goalEnabled
        updateTargetTimeButtonText(activity, goalHour, goalMin)
        updateCurrentWakeTimeButtonText(activity, currentHour, currentMin)
        activity.textMinSleepValue?.text = getComputedDurationString(
            activity.preferenceManager,
            PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES,
            minSleepMin
        )
        MainActivityViewHelpers.updateInputEnabledStates(activity, goalEnabled, healthConnectEnabled)
    }

    fun updateHealthConnectUi(activity: MainActivity, getter: PreferenceGetter) {
        val healthConnectEnabled = getter.getBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, false)
        val hcMinDurationMin = getter.getInt(
            PreferenceKeys.KEY_HC_MIN_DURATION_MINUTES,
            AppDefaults.HC_MIN_DURATION_MINUTES
        )

        activity.rowHealthConnect?.isChecked = healthConnectEnabled
        activity.textHcMinDurationValue?.text = getComputedDurationString(
            activity.preferenceManager,
            PreferenceKeys.KEY_HC_MIN_DURATION_MINUTES,
            hcMinDurationMin
        )
    }

    private fun getComputedDurationString(pm: PreferenceManager?, key: String, defaultMinutes: Int): String {
        pm ?: return DurationUtils.formatDurationString(defaultMinutes)
        val computed = PreferenceComputations.formatDuration(key, defaultMinutes)
        return pm.getComputed(key, computed) ?: DurationUtils.formatDurationString(defaultMinutes)
    }

    fun updateTargetTimeButtonText(activity: MainActivity, hour: Int, minute: Int) {
        activity.textTargetTimeValue?.text = formatTime(activity, hour, minute)
    }

    fun updateCurrentWakeTimeButtonText(activity: MainActivity, hour: Int, minute: Int) {
        activity.textCurrentWakeTimeValue?.text = formatTime(activity, hour, minute)
    }

    private fun formatTime(activity: MainActivity, hour: Int, minute: Int): String {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        val timeFormat = android.text.format.DateFormat.getTimeFormat(activity)
        return timeFormat.format(cal.time)
    }
}
