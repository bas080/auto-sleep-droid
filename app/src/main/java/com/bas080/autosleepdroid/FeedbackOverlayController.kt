package com.bas080.autosleepdroid

import android.content.Context
import android.view.View

internal object FeedbackOverlayController {

    fun checkAndPromptCrashReport(activity: MainActivity) {
        val prefs = activity.getSharedPreferences("crash_reports", Context.MODE_PRIVATE)
        val pendingReport = prefs.getString("pending_crash_report", null)
        if (pendingReport != null) {
            showFeedbackOverlay(activity, crashReport = pendingReport)
        }
    }

    fun updateReportCrashRowVisibility(activity: MainActivity) {
        val prefs = activity.getSharedPreferences("crash_reports", Context.MODE_PRIVATE)
        val pendingReport = prefs.getString("pending_crash_report", null)
        activity.btnReportCrash?.visibility = if (pendingReport != null) View.VISIBLE else View.GONE
    }

    fun showFeedbackOverlay(activity: MainActivity, crashReport: String? = null) {
        val isCrash = !crashReport.isNullOrEmpty()
        activity.feedbackTitleText?.setText(if (isCrash) R.string.dialog_crash_title else R.string.link_feedback)
        activity.feedbackPromptText?.setText(if (isCrash) R.string.prompt_crash_report else R.string.prompt_feedback)
        activity.btnDiscardCrash?.visibility = if (isCrash) View.VISIBLE else View.GONE

        activity.feedbackTextContent?.setText("")
        activity.chkIncludeLogs?.isChecked = true

        setupFeedbackActions(activity, crashReport, isCrash)

        activity.feedbackOverlayContainer?.visibility = View.VISIBLE
        activity.manualOverlayContainer?.visibility = View.GONE
        activity.logsOverlayContainer?.visibility = View.GONE
        activity.mainContentContainer?.visibility = View.GONE
    }

    private fun setupFeedbackActions(activity: MainActivity, crashReport: String?, isCrash: Boolean) {
        activity.btnCopyFeedback?.setOnClickListener {
            val userInput = activity.feedbackTextContent?.text?.toString() ?: ""
            val shouldIncludeLogs = activity.chkIncludeLogs?.isChecked ?: true
            val textToCopy = FeedbackEmailHelper.buildFeedbackPayload(
                activity,
                userInput,
                crashReport,
                shouldIncludeLogs
            )
            FeedbackEmailHelper.copyTextToClipboard(activity, textToCopy)
        }

        activity.btnSendFeedbackEmail?.setOnClickListener {
            val userInput = activity.feedbackTextContent?.text?.toString() ?: ""
            val shouldIncludeLogs = activity.chkIncludeLogs?.isChecked ?: true
            val editedText = FeedbackEmailHelper.buildFeedbackPayload(
                activity,
                userInput,
                crashReport,
                shouldIncludeLogs
            )
            val subject = if (isCrash) {
                "Auto Sleep Droid Crash Report (v${BuildConfig.VERSION_NAME})"
            } else {
                "Auto Sleep Droid Feedback (v${BuildConfig.VERSION_NAME})"
            }
            FeedbackEmailHelper.sendFeedbackEmailWithText(activity, subject, editedText)
        }

        activity.btnDiscardCrash?.setOnClickListener {
            activity.getSharedPreferences("crash_reports", Context.MODE_PRIVATE)
                .edit().remove("pending_crash_report").apply()
            updateReportCrashRowVisibility(activity)
            MainActivityHeaderAndLogsHandler.hideOverlays(activity)
        }
    }
}
