package com.bas080.autosleepdroid

import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.SpannableStringBuilder
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView

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
        activity.mainContentContainer?.visibility = View.VISIBLE
    }

    fun isAnyOverlayVisible(activity: MainActivity): Boolean {
        return activity.manualOverlayContainer?.visibility == View.VISIBLE ||
            activity.logsOverlayContainer?.visibility == View.VISIBLE ||
            activity.feedbackOverlayContainer?.visibility == View.VISIBLE
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

    fun startTimerService(activity: MainActivity) {
        val serviceIntent = Intent(activity, MainService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    activity.startForegroundService(serviceIntent)
                    return
                } catch (e: IllegalStateException) {
                    EventLogger.log(activity, "startForegroundService failed: ${e.message}")
                } catch (e: SecurityException) {
                    EventLogger.log(activity, "startForegroundService failed: ${e.message}")
                }
            }
            activity.startService(serviceIntent)
        } catch (e: IllegalStateException) {
            EventLogger.log(activity, "Failed to start service: ${e.message}")
        } catch (e: SecurityException) {
            EventLogger.log(activity, "Failed to start service: ${e.message}")
        }
    }

    fun updateTargetTimeButtonText(activity: MainActivity, hour: Int, minute: Int) {
        MainActivityUiStateUpdater.updateTargetTimeButtonText(activity, hour, minute)
    }

    fun updateCurrentWakeTimeButtonText(activity: MainActivity, hour: Int, minute: Int) {
        MainActivityUiStateUpdater.updateCurrentWakeTimeButtonText(activity, hour, minute)
    }
}
