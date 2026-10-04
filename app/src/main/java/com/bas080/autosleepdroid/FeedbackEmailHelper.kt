package com.bas080.autosleepdroid

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast

internal object FeedbackEmailHelper {

    private const val MAX_FEEDBACK_LOG_ENTRIES = 50
    private const val BYTES_PER_KB = 1024L

    fun buildFeedbackPayload(
        context: Context,
        userMessage: String,
        crashReport: String?,
        includeLogs: Boolean
    ): String {
        val bodyBuilder = StringBuilder()

        val trimmedMessage = userMessage.trim()
        if (trimmedMessage.isNotEmpty()) {
            bodyBuilder.append("User Feedback / Details:\n").append(trimmedMessage).append("\n\n")
        }

        if (!crashReport.isNullOrEmpty()) {
            bodyBuilder.append("Crash Report:\n").append(crashReport).append("\n\n")
        }

        if (includeLogs) {
            val events = EventLogger.getEvents(context)
            if (events.isNotEmpty()) {
                val lastEvents = events.takeLast(MAX_FEEDBACK_LOG_ENTRIES)
                bodyBuilder.append("Logs:\n")
                for (event in lastEvents) {
                    bodyBuilder.append(EventLogger.formatColoredEvent(context, event).toString()).append("\n")
                }
                bodyBuilder.append("\n")
            }
        }

        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        val availMemMb = memInfo.availMem / (BYTES_PER_KB * BYTES_PER_KB)

        val stat = android.os.StatFs(context.filesDir.absolutePath)
        val availStorageMb = stat.availableBytes / (BYTES_PER_KB * BYTES_PER_KB)

        bodyBuilder.append("---\nApp Version: ").append(BuildConfig.VERSION_NAME)
            .append(" (Code ").append(BuildConfig.VERSION_CODE).append(")")
            .append("\nAndroid Version: ").append(Build.VERSION.RELEASE)
            .append(" (API ").append(Build.VERSION.SDK_INT).append(")")
            .append("\nDevice: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL)
            .append("\nFree Memory: ").append(availMemMb).append(" MB")
            .append("\nAvailable Storage: ").append(availStorageMb).append(" MB")

        return bodyBuilder.toString()
    }

    fun copyTextToClipboard(context: Context, textToCopy: String): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard != null) {
            try {
                val clip = ClipData.newPlainText("Crash / Feedback Report", textToCopy)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, R.string.toast_report_copied, Toast.LENGTH_SHORT).show()
                return true
            } catch (e: SecurityException) {
                EventLogger.log(context, "Failed to copy report to clipboard: " + e.message)
            } catch (e: IllegalStateException) {
                EventLogger.log(context, "Failed to copy report to clipboard: " + e.message)
            }
        }
        Toast.makeText(context, "Could not copy report to clipboard", Toast.LENGTH_SHORT).show()
        return false
    }

    fun sendFeedbackEmailWithText(context: Context, subject: String, body: String) {
        val mailtoUriStr = "mailto:bas080@hotmail.com?subject=" + Uri.encode(subject) + "&body=" + Uri.encode(body)
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse(mailtoUriStr)
            putExtra(Intent.EXTRA_EMAIL, arrayOf("bas080@hotmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }

        try {
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.link_feedback)))
        } catch (e: ActivityNotFoundException) {
            EventLogger.log(context, EventLogger.LEVEL_LOW, "Email chooser failed: ${e.message}")
            launchEmailFallbackOrCopy(context, subject, body)
        } catch (e: SecurityException) {
            EventLogger.log(context, EventLogger.LEVEL_LOW, "Email chooser failed: ${e.message}")
            launchEmailFallbackOrCopy(context, subject, body)
        }
    }

    private fun launchEmailFallbackOrCopy(context: Context, subject: String, body: String) {
        val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf("bas080@hotmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        try {
            context.startActivity(Intent.createChooser(fallbackIntent, context.getString(R.string.link_feedback)))
        } catch (ex: ActivityNotFoundException) {
            EventLogger.log(context, "Failed to launch email client: " + ex.message)
            copyReportOrShowError(context, body)
        } catch (ex: SecurityException) {
            EventLogger.log(context, "Failed to launch email client: " + ex.message)
            copyReportOrShowError(context, body)
        }
    }

    private fun copyReportOrShowError(context: Context, body: String) {
        val copied = copyTextToClipboard(context, body)
        if (copied) {
            Toast.makeText(context, "No email app found. Report copied to clipboard.", Toast.LENGTH_LONG).show()
        } else {
            showNoEmailAppDialog(context)
        }
    }

    private fun showNoEmailAppDialog(context: Context) {
        val dialog = AlertDialog.Builder(context)
            .setTitle("Could Not Send Report")
            .setMessage("No email app or clipboard handler was found on this device.")
            .setPositiveButton(R.string.dialog_ok, null)
            .show()
        dialog.findViewById<TextView>(android.R.id.title)?.gravity = Gravity.CENTER
    }
}
