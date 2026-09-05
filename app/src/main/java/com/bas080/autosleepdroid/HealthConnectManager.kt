package com.bas080.autosleepdroid

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

object HealthConnectManager {

    fun interface Callback {
        fun onResult(success: Boolean, error: String?)
    }

    val REQUIRED_PERMISSIONS = setOf(
        HealthPermission.getWritePermission(SleepSessionRecord::class)
    )

    @JvmStatic
    fun isHealthConnectAvailable(context: Context): Boolean {
        return try {
            val status = HealthConnectClient.getSdkStatus(context)
            status == HealthConnectClient.SDK_AVAILABLE
        } catch (e: Exception) {
            false
        }
    }

    @JvmStatic
    fun hasSleepWritePermission(context: Context, callback: (Boolean) -> Unit) {
        if (!isHealthConnectAvailable(context)) {
            callback(false)
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            val hasPermission = try {
                val client = HealthConnectClient.getOrCreate(context)
                val granted = client.permissionController.getGrantedPermissions()
                granted.containsAll(REQUIRED_PERMISSIONS)
            } catch (e: Exception) {
                false
            }
            withContext(Dispatchers.Main) {
                callback(hasPermission)
            }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun writeSleepSession(
        context: Context,
        startTimeMs: Long,
        endTimeMs: Long,
        callback: Callback? = null
    ) {
        if (startTimeMs <= 0 || endTimeMs <= startTimeMs) {
            val errorMsg = "Invalid timestamps: startTime=$startTimeMs, endTime=$endTimeMs"
            EventLogger.log(context, EventLogger.LEVEL_HIGH, "Health Connect: $errorMsg")
            callback?.onResult(false, errorMsg)
            return
        }

        val durationMinutes = ((endTimeMs - startTimeMs) / 60_000L).toInt()
        if (durationMinutes < 1 || durationMinutes > 1440) {
            val errorMsg = "Invalid sleep duration: ${durationMinutes}m"
            EventLogger.log(context, EventLogger.LEVEL_HIGH, "Health Connect: $errorMsg")
            callback?.onResult(false, errorMsg)
            return
        }

        if (!isHealthConnectAvailable(context)) {
            val errorMsg = "Health Connect SDK unavailable"
            EventLogger.log(context, EventLogger.LEVEL_HIGH, "Health Connect: $errorMsg")
            callback?.onResult(false, errorMsg)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = HealthConnectClient.getOrCreate(context)
                val granted = client.permissionController.getGrantedPermissions()
                if (!granted.containsAll(REQUIRED_PERMISSIONS)) {
                    val errorMsg = "Write permission not granted"
                    EventLogger.log(context, EventLogger.LEVEL_HIGH, "Health Connect: $errorMsg")
                    withContext(Dispatchers.Main) {
                        callback?.onResult(false, errorMsg)
                    }
                    return@launch
                }

                val startInstant = Instant.ofEpochMilli(startTimeMs)
                val endInstant = Instant.ofEpochMilli(endTimeMs)
                val startOffset = ZoneId.systemDefault().rules.getOffset(startInstant)
                val endOffset = ZoneId.systemDefault().rules.getOffset(endInstant)

                val record = SleepSessionRecord(
                    startTime = startInstant,
                    startZoneOffset = startOffset,
                    endTime = endInstant,
                    endZoneOffset = endOffset,
                    title = "Sleep"
                )

                client.insertRecords(listOf(record))
                val formattedDuration = DurationUtils.formatDurationString(durationMinutes)
                val logMsg = "Successfully persisted sleep session ($formattedDuration)"
                EventLogger.log(context, EventLogger.LEVEL_HIGH, "Health Connect: $logMsg")

                withContext(Dispatchers.Main) {
                    callback?.onResult(true, null)
                }
            } catch (e: Exception) {
                val errorMsg = "Error writing sleep session: ${e.message}"
                EventLogger.log(context, EventLogger.LEVEL_HIGH, "Health Connect: $errorMsg")
                withContext(Dispatchers.Main) {
                    callback?.onResult(false, errorMsg)
                }
            }
        }
    }
}
