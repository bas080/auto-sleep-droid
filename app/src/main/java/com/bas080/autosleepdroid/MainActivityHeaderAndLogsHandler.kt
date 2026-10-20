package com.bas080.autosleepdroid

import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.SpannableStringBuilder
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView

private const val MS_PER_MINUTE = 60_000L

internal object MainActivityHeaderAndLogsHandler {

    fun setupHeaderAndLinks(activity: MainActivity) {
        val versionText = activity.findViewById<TextView>(R.id.app_version_text)
        versionText?.text = activity.getString(R.string.version_label, BuildConfig.VERSION_NAME)

        activity.findViewById<Button>(R.id.btn_manual_back)?.setOnClickListener { hideOverlays(activity) }
        activity.findViewById<Button>(R.id.btn_logs_back)?.setOnClickListener { hideOverlays(activity) }

        activity.findViewById<Button>(R.id.btn_clear_logs)?.setOnClickListener {
            EventLogger.clear(activity)
            activity.eventLogText?.text = ""
        }

        activity.btnExport?.setOnClickListener {
            SettingsImportExportHandler.exportSettings(activity, activity.preferenceManager)
        }
        activity.btnImport?.setOnClickListener {
            SettingsImportExportHandler.showImportDialog(activity, activity.preferenceManager)
        }
        activity.btnVersion?.setOnClickListener {
            val releasesUrl = "https://github.com/bas080/auto-sleep-droid/releases"
            MainActivityDialogs.openUrl(activity, releasesUrl)
        }
        activity.btnFeedback?.setOnClickListener {
            FeedbackOverlayController.showFeedbackOverlay(activity, crashReport = null)
        }

        activity.btnReportCrash?.setOnClickListener {
            val prefs = activity.getSharedPreferences("crash_reports", Context.MODE_PRIVATE)
            val pendingReport = prefs.getString("pending_crash_report", null)
            FeedbackOverlayController.showFeedbackOverlay(activity, crashReport = pendingReport)
        }

        activity.btnFeedbackBack?.setOnClickListener { hideOverlays(activity) }
        activity.btnLinks?.setOnClickListener { MainActivityDialogs.showLinksDialog(activity) }
    }

    fun hideOverlays(activity: MainActivity) {
        activity.manualOverlayContainer?.visibility = View.GONE
        activity.logsOverlayContainer?.visibility = View.GONE
        activity.feedbackOverlayContainer?.visibility = View.GONE
        activity.bedtimeOverlayContainer?.visibility = View.GONE
        activity.mainContentContainer?.visibility = View.VISIBLE
    }

    fun isAnyOverlayVisible(activity: MainActivity): Boolean {
        return activity.manualOverlayContainer?.visibility == View.VISIBLE ||
            activity.logsOverlayContainer?.visibility == View.VISIBLE ||
            activity.feedbackOverlayContainer?.visibility == View.VISIBLE ||
            activity.bedtimeOverlayContainer?.visibility == View.VISIBLE
    }

    fun refreshEventLog(activity: MainActivity) {
        val events = EventLogger.getEvents(activity)
        val ssb = SpannableStringBuilder()
        for (event in events) {
            ssb.append(EventLogger.formatColoredEvent(activity, event)).append("\n")
        }
        activity.eventLogText?.let {
            it.text = ssb
            scrollToBottom(activity)
        }
    }

    fun scrollToBottom(activity: MainActivity) {
        activity.eventScrollView?.post { activity.eventScrollView?.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    fun startTimerService(context: Context) {
        val serviceIntent = Intent(context, MainService::class.java)
        try {
            context.startService(serviceIntent)
        } catch (e: IllegalStateException) {
            EventLogger.log(context, "startService failed, attempting startForegroundService: ${e.message}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    context.startForegroundService(serviceIntent)
                } catch (ex: IllegalStateException) {
                    EventLogger.log(context, "startForegroundService failed: ${ex.message}")
                } catch (ex: SecurityException) {
                    EventLogger.log(context, "startForegroundService failed: ${ex.message}")
                }
            }
        } catch (e: SecurityException) {
            EventLogger.log(context, "startService failed: ${e.message}")
        }
    }

    fun updateTargetTimeButtonText(activity: MainActivity, hour: Int, minute: Int) {
        MainActivityUiStateUpdater.updateTargetTimeButtonText(activity, hour, minute)
    }

    fun updateCurrentWakeTimeButtonText(activity: MainActivity, hour: Int, minute: Int) {
        MainActivityUiStateUpdater.updateCurrentWakeTimeButtonText(activity, hour, minute)
    }

    fun checkAndShowBedtimeOverlayOnResume(activity: MainActivity) {
        val pm = activity.preferenceManager ?: return
        val goalEnabled = pm.getBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, false)
        val bedtimeEnabled = pm.getBoolean(PreferenceKeys.KEY_BEDTIME_REMINDER_ENABLED, false)
        if (!goalEnabled || !bedtimeEnabled) return

        val now = System.currentTimeMillis()
        val minSleepMin = pm.getInt(
            PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES,
            AppDefaults.MIN_SLEEP_DURATION_MINUTES
        )
        val minSleepMs = minSleepMin * MS_PER_MINUTE

        val goalHour = pm.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR, AppDefaults.WAKE_UP_GOAL_HOUR)
        val goalMin = pm.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE, AppDefaults.WAKE_UP_GOAL_MINUTE)
        val currentHour = pm.getInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, goalHour)
        val currentMin = pm.getInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, goalMin)

        val currentWakeTime = PreferenceComputations.calculateCurrentWakeTime(now, currentHour, currentMin)
        val bedtimeStart = currentWakeTime - minSleepMs

        val alreadyShownToday = pm.getLong("bedtime_overlay_last_shown_ms", 0L)
        val sameWindow = alreadyShownToday in bedtimeStart..currentWakeTime

        if (now in bedtimeStart..currentWakeTime && !sameWindow && !isAnyOverlayVisible(activity)) {
            pm.edit().putLong("bedtime_overlay_last_shown_ms", now).apply()
            MainActivityDialogs.showBedtimeOverlay(activity)
        }
    }
}
