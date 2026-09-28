@file:Suppress("TooManyFunctions", "MagicNumber")

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

    @Volatile
    private var testClient: HealthConnectClient? = null

    @Volatile
    private var testSdkAvailable: Boolean? = null

    fun setClientForTesting(client: HealthConnectClient?, isSdkAvailable: Boolean? = true) {
        this.testClient = client
        this.testSdkAvailable = isSdkAvailable
    }

    private fun getClient(context: Context): HealthConnectClient {
        return testClient ?: HealthConnectClient.getOrCreate(context)
    }

    fun openHealthConnectPermissions(activity: Activity) {
        if (!isHealthConnectAvailable(activity)) {
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

    @Suppress("TooGenericExceptionCaught")
    fun revokeAllPermissions(context: Context, callback: Callback? = null) {
        if (!isHealthConnectAvailable(context)) {
            callback?.onResult(false, "Health Connect SDK unavailable")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = getClient(context)
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

    fun hasSleepWritePermission(context: Context, callback: PermissionCallback) {
        if (!isHealthConnectAvailable(context)) {
            callback.onPermissionResult(false)
            return
        }
        if (testSdkAvailable != null || testClient != null) {
            callback.onPermissionResult(checkTestClientPermission())
            return
        }
        checkAsyncPermission(context, callback)
    }

    private fun checkTestClientPermission(): Boolean {
        return try {
            val client = testClient ?: return false
            runBlocking {
                val granted = client.permissionController.getGrantedPermissions()
                granted.containsAll(REQUIRED_PERMISSIONS)
            }
        } catch (ignored: Exception) {
            false
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun checkAsyncPermission(context: Context, callback: PermissionCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            val hasPermission = try {
                val client = getClient(context)
                val granted = client.permissionController.getGrantedPermissions()
                granted.containsAll(REQUIRED_PERMISSIONS)
            } catch (e: Exception) {
                EventLogger.log(context, EventLogger.LEVEL_LOW, "Failed async permission check: ${e.message}")
                false
            }
            withContext(Dispatchers.Main) {
                callback.onPermissionResult(hasPermission)
            }
        }
    }

    fun writeSleepSession(
        context: Context,
        startTimeMs: Long,
        endTimeMs: Long,
        callback: Callback? = null
    ) {
        val validationError = validateSessionTimestamps(startTimeMs, endTimeMs)
        if (validationError != null) {
            EventLogger.log(context, EventLogger.LEVEL_HIGH, "Health Connect: $validationError")
            callback?.onResult(false, validationError)
            return
        }

        if (!isHealthConnectAvailable(context)) {
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
        val durationMinutes = ((endTimeMs - startTimeMs) / 60_000L).toInt()
        return if (durationMinutes < 1 || durationMinutes > 1440) {
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
        callback: Callback?
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = getClient(context)
                val granted = client.permissionController.getGrantedPermissions()
                if (!granted.containsAll(REQUIRED_PERMISSIONS)) {
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
                val durationMinutes = ((endTimeMs - startTimeMs) / 60_000L).toInt()
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
