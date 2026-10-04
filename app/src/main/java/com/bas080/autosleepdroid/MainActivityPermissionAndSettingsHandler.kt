package com.bas080.autosleepdroid

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context.ALARM_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast

internal object MainActivityPermissionAndSettingsHandler {

    private const val BUILD_VERSION_S = 31
    private const val BUILD_VERSION_TIRAMISU = 33

    fun requestNotificationPermissionOnStartupIfNeeded(activity: MainActivity) {
        if (Build.VERSION.SDK_INT >= BUILD_VERSION_TIRAMISU) {
            val hasPerm = activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            if (hasPerm != PackageManager.PERMISSION_GRANTED) {
                EventLogger.log(activity, EventLogger.LEVEL_LOW, "Requesting notification permission on app startup")
                activity.notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun requestExactAlarmPermissionIfNeeded(activity: MainActivity) {
        if (Build.VERSION.SDK_INT >= BUILD_VERSION_S) {
            val alarmManager = activity.getSystemService(ALARM_SERVICE) as android.app.AlarmManager?
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                EventLogger.log(activity, EventLogger.LEVEL_LOW, "Opening exact alarm settings")
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${activity.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                activity.startActivity(intent)
            }
        }
    }

    fun openSettingsWithFallback(activity: MainActivity, primaryAction: String, fallbackAction: String) {
        try {
            val intent = Intent(primaryAction).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            handleSettingsFallback(activity, fallbackAction, e.message)
        } catch (e: SecurityException) {
            handleSettingsFallback(activity, fallbackAction, e.message)
        }
    }

    private fun handleSettingsFallback(activity: MainActivity, fallbackAction: String, primaryMsg: String?) {
        EventLogger.log(activity, EventLogger.LEVEL_LOW, "Primary DND setting failed: $primaryMsg")
        try {
            val intent = Intent(fallbackAction).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (ex: ActivityNotFoundException) {
            EventLogger.log(activity, EventLogger.LEVEL_LOW, "Fallback DND setting failed: ${ex.message}")
            Toast.makeText(activity, "Could not open DND settings", Toast.LENGTH_SHORT).show()
        } catch (ex: SecurityException) {
            EventLogger.log(activity, EventLogger.LEVEL_LOW, "Fallback DND setting failed: ${ex.message}")
            Toast.makeText(activity, "Could not open DND settings", Toast.LENGTH_SHORT).show()
        }
    }

    fun checkHealthConnectOnResume(activity: MainActivity) {
        if (activity.isRequestingHealthConnectPermission) {
            checkPendingPermissionOnResume(activity)
        } else {
            checkActiveSyncOnResume(activity)
        }
    }

    private fun checkPendingPermissionOnResume(activity: MainActivity) {
        HealthConnectManager.hasSleepWritePermission(activity) { hasPermission ->
            activity.isRequestingHealthConnectPermission = false
            if (hasPermission) {
                activity.preferenceManager?.edit()?.putBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, true)?.apply()
                activity.rowHealthConnect?.isChecked = true
                EventLogger.log(activity, EventLogger.LEVEL_HIGH, "Health Connect sync enabled and permission granted")
            } else {
                activity.preferenceManager?.edit()?.putBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, false)
                    ?.apply()
                activity.rowHealthConnect?.isChecked = false
                Toast.makeText(activity, R.string.toast_health_connect_disabled, Toast.LENGTH_SHORT).show()
                EventLogger.log(
                    activity,
                    EventLogger.LEVEL_HIGH,
                    "Health Connect permission not granted; disabling sync"
                )
            }
        }
    }

    private fun checkActiveSyncOnResume(activity: MainActivity) {
        val healthConnectEnabled = activity.preferenceManager
            ?.getBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, false) ?: false
        if (!healthConnectEnabled) return

        HealthConnectManager.hasSleepWritePermission(activity) { hasPermission ->
            if (!hasPermission) {
                activity.preferenceManager?.edit()?.putBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, false)
                    ?.apply()
                activity.rowHealthConnect?.isChecked = false
                EventLogger.log(activity, EventLogger.LEVEL_HIGH, "Health Connect permission revoked; disabling sync")
            }
        }
    }
}
