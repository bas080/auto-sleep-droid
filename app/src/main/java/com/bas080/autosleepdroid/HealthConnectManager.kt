package com.bas080.autosleepdroid

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

object HealthConnectManager {

    internal const val MS_PER_MINUTE = 60_000L
    internal const val MAX_SLEEP_DURATION_MINUTES = 1440

    @Volatile
    internal var testClient: HealthConnectClient? = null

    @Volatile
    internal var testSdkAvailable: Boolean? = null

    fun setClientForTesting(client: HealthConnectClient?, isSdkAvailable: Boolean? = true) {
        this.testClient = client
        this.testSdkAvailable = isSdkAvailable
    }

    internal fun getClient(context: Context): HealthConnectClient {
        return testClient ?: HealthConnectClient.getOrCreate(context)
    }

    fun openHealthConnectPermissions(activity: Activity) {
        HealthConnectPermissionHandler.openHealthConnectPermissions(activity)
    }

    fun interface Callback {
        fun onResult(success: Boolean, error: String?)
    }

    fun interface PermissionCallback {
        fun onPermissionResult(hasPermission: Boolean)
    }

    val REQUIRED_PERMISSIONS = setOf(
        HealthPermission.getWritePermission(SleepSessionRecord::class)
    )

    @Suppress("TooGenericExceptionCaught")
    fun isHealthConnectAvailable(context: Context): Boolean {
        testSdkAvailable?.let { return it }
        return try {
            val status = HealthConnectClient.getSdkStatus(context)
            status == HealthConnectClient.SDK_AVAILABLE
        } catch (e: Exception) {
            EventLogger.log(context, EventLogger.LEVEL_LOW, "Failed to query Health Connect SDK status: ${e.message}")
            false
        }
    }

    fun revokeAllPermissions(context: Context, callback: Callback? = null) {
        HealthConnectPermissionHandler.revokeAllPermissions(context, callback)
    }

    fun hasSleepWritePermission(context: Context, callback: PermissionCallback) {
        HealthConnectPermissionHandler.hasSleepWritePermission(context, callback)
    }

    fun writeSleepSession(
        context: Context,
        startTimeMs: Long,
        endTimeMs: Long,
        callback: Callback? = null
    ) {
        HealthConnectSessionWriter.writeSleepSession(context, startTimeMs, endTimeMs, callback)
    }
}

internal object HealthConnectPermissionHandler {
    fun openHealthConnectPermissions(activity: Activity) {
        if (!HealthConnectManager.isHealthConnectAvailable(activity)) {
            val toastRes = R.string.toast_health_connect_not_available
            android.widget.Toast.makeText(activity, toastRes, android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val intents = listOf(
            Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS").apply {
                putExtra(Intent.EXTRA_PACKAGE_NAME, activity.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent("androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE").apply {
                setPackage("com.google.android.apps.healthdata")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS).apply {
                putExtra(Intent.EXTRA_PACKAGE_NAME, activity.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${activity.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )

        for (intent in intents) {
            try {
                activity.startActivity(intent)
                return
            } catch (ignored: Exception) {
                // Try next fallback intent
            }
        }

        val fallbackToastRes = R.string.toast_health_connect_not_available
        android.widget.Toast.makeText(activity, fallbackToastRes, android.widget.Toast.LENGTH_SHORT).show()
    }

    @Suppress("TooGenericExceptionCaught")
    fun revokeAllPermissions(context: Context, callback: HealthConnectManager.Callback?) {
        if (!HealthConnectManager.isHealthConnectAvailable(context)) {
            callback?.onResult(false, "Health Connect SDK unavailable")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = HealthConnectManager.getClient(context)
                client.permissionController.revokeAllPermissions()
                EventLogger.log(context, EventLogger.LEVEL_HIGH, "Health Connect: Revoked all granted permissions")
                withContext(Dispatchers.Main) {
                    callback?.onResult(true, null)
                }
            } catch (e: Exception) {
                val errorMsg = "Error revoking permissions: ${e.message}"
                EventLogger.log(context, EventLogger.LEVEL_HIGH, "Health Connect: $errorMsg")
                withContext(Dispatchers.Main) {
                    callback?.onResult(false, errorMsg)
                }
            }
        }
    }

    fun hasSleepWritePermission(context: Context, callback: HealthConnectManager.PermissionCallback) {
        if (!HealthConnectManager.isHealthConnectAvailable(context)) {
            callback.onPermissionResult(false)
            return
        }
        if (HealthConnectManager.testSdkAvailable != null || HealthConnectManager.testClient != null) {
            callback.onPermissionResult(checkTestClientPermission())
            return
        }
        checkAsyncPermission(context, callback)
    }

    private fun checkTestClientPermission(): Boolean {
        return try {
            val client = HealthConnectManager.testClient ?: return false
            runBlocking {
                val granted = client.permissionController.getGrantedPermissions()
                granted.containsAll(HealthConnectManager.REQUIRED_PERMISSIONS)
            }
        } catch (ignored: Exception) {
            false
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun checkAsyncPermission(context: Context, callback: HealthConnectManager.PermissionCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            val hasPermission = try {
                val client = HealthConnectManager.getClient(context)
                val granted = client.permissionController.getGrantedPermissions()
                granted.containsAll(HealthConnectManager.REQUIRED_PERMISSIONS)
            } catch (e: Exception) {
                EventLogger.log(context, EventLogger.LEVEL_LOW, "Failed async permission check: ${e.message}")
                false
            }
            withContext(Dispatchers.Main) {
                callback.onPermissionResult(hasPermission)
            }
        }
    }
}

internal object HealthConnectSessionWriter {
    fun writeSleepSession(
        context: Context,
        startTimeMs: Long,
        endTimeMs: Long,
        callback: HealthConnectManager.Callback?
    ) {
        val validationError = validateSessionTimestamps(startTimeMs, endTimeMs)
        if (validationError != null) {
            EventLogger.log(context, EventLogger.LEVEL_HIGH, "Health Connect: $validationError")
            callback?.onResult(false, validationError)
            return
        }

        if (!HealthConnectManager.isHealthConnectAvailable(context)) {
            val errorMsg = "Health Connect SDK unavailable"
            EventLogger.log(context, EventLogger.LEVEL_HIGH, "Health Connect: $errorMsg")
            callback?.onResult(false, errorMsg)
            return
        }

        executeSleepSessionWrite(context, startTimeMs, endTimeMs, callback)
    }

    private fun validateSessionTimestamps(startTimeMs: Long, endTimeMs: Long): String? {
        if (startTimeMs <= 0 || endTimeMs <= startTimeMs) {
            return "Invalid timestamps: startTime=$startTimeMs, endTime=$endTimeMs"
        }
        val durationMinutes = ((endTimeMs - startTimeMs) / HealthConnectManager.MS_PER_MINUTE).toInt()
        return if (durationMinutes < 1 || durationMinutes > HealthConnectManager.MAX_SLEEP_DURATION_MINUTES) {
            "Invalid sleep duration: ${durationMinutes}m"
        } else {
            null
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun executeSleepSessionWrite(
        context: Context,
        startTimeMs: Long,
        endTimeMs: Long,
        callback: HealthConnectManager.Callback?
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = HealthConnectManager.getClient(context)
                val granted = client.permissionController.getGrantedPermissions()
                if (!granted.containsAll(HealthConnectManager.REQUIRED_PERMISSIONS)) {
                    val errorMsg = "Write permission not granted"
                    EventLogger.log(context, EventLogger.LEVEL_HIGH, "Health Connect: $errorMsg")
                    withContext(Dispatchers.Main) { callback?.onResult(false, errorMsg) }
                    return@launch
                }

                val startInstant = Instant.ofEpochMilli(startTimeMs)
                val endInstant = Instant.ofEpochMilli(endTimeMs)
                val record = SleepSessionRecord(
                    startTime = startInstant,
                    startZoneOffset = ZoneId.systemDefault().rules.getOffset(startInstant),
                    endTime = endInstant,
                    endZoneOffset = ZoneId.systemDefault().rules.getOffset(endInstant),
                    title = "Sleep"
                )

                client.insertRecords(listOf(record))
                val durationMinutes = ((endTimeMs - startTimeMs) / HealthConnectManager.MS_PER_MINUTE).toInt()
                val formatted = DurationUtils.formatDurationString(durationMinutes)
                val logMsg = "Successfully persisted sleep session ($formatted)"
                EventLogger.log(context, EventLogger.LEVEL_HIGH, "Health Connect: $logMsg")

                withContext(Dispatchers.Main) { callback?.onResult(true, null) }
            } catch (e: Exception) {
                val errorMsg = "Error writing sleep session: ${e.message}"
                EventLogger.log(context, EventLogger.LEVEL_HIGH, "Health Connect: $errorMsg")
                withContext(Dispatchers.Main) { callback?.onResult(false, errorMsg) }
            }
        }
    }
}
