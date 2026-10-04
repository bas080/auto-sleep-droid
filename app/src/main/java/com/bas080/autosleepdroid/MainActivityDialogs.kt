package com.bas080.autosleepdroid

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.text.Html
import android.view.Gravity
import android.view.View
import android.widget.TextView
import android.widget.Toast
import java.io.IOException
import java.nio.charset.StandardCharsets

internal class DurationDialogSpec(
    val titleResId: Int,
    val prefKey: String,
    val defaultMinutes: Int,
    val bounds: MainActivity.DurationPickerBounds = MainActivity.DurationPickerBounds()
)

internal object MainActivityDialogs {

    private const val PADDING_DIALOG_HORIZONTAL_DP = 24
    private const val PADDING_DIALOG_VERTICAL_DP = 12

    fun centerDialogTitle(dialog: AlertDialog) {
        dialog.findViewById<TextView>(android.R.id.title)?.gravity = Gravity.CENTER
    }

    fun openUrl(activity: MainActivity, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        activity.startActivity(intent)
    }

    fun showDurationDialog(
        activity: MainActivity,
        spec: DurationDialogSpec,
        listener: MainActivity.OnDurationSavedListener? = null
    ) {
        val pm = activity.preferenceManager
        val currentMinutes = pm?.getInt(spec.prefKey, spec.defaultMinutes) ?: spec.defaultMinutes

        val durationInputView = DurationInputView(activity)
        durationInputView.configure(spec.bounds.minHours, spec.bounds.maxHours, spec.bounds.minuteStep)
        val density = activity.resources.displayMetrics.density
        val paddingHorizontalPx = (PADDING_DIALOG_HORIZONTAL_DP * density).toInt()
        val paddingVerticalPx = (PADDING_DIALOG_VERTICAL_DP * density).toInt()
        durationInputView.setPadding(paddingHorizontalPx, paddingVerticalPx, paddingHorizontalPx, paddingVerticalPx)
        durationInputView.setTotalMinutes(currentMinutes)

        val builder = AlertDialog.Builder(activity)
        builder.setTitle(spec.titleResId)
        builder.setView(durationInputView)
        builder.setPositiveButton(R.string.dialog_ok) { _, _ ->
            val minutes = durationInputView.getTotalMinutes()
            if (minutes > 0) {
                pm?.edit()?.putInt(spec.prefKey, minutes)?.apply()
                listener?.onSaved(minutes)
            } else {
                Toast.makeText(activity, R.string.toast_duration_invalid, Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.dismiss() }
        val dialog = builder.show()
        centerDialogTitle(dialog)
    }

    fun showLinksDialog(activity: MainActivity) {
        val options = arrayOf<CharSequence>(
            activity.getString(R.string.link_manual),
            activity.getString(R.string.link_logs),
            activity.getString(R.string.link_donate)
        )

        val builder = AlertDialog.Builder(activity)
        builder.setTitle(R.string.label_links)
        builder.setItems(options) { _, which ->
            when (which) {
                0 -> showManualScreen(activity)
                1 -> showLogsScreen(activity)
                2 -> openUrl(activity, "https://liberapay.com/bas080")
            }
        }
        builder.setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.dismiss() }
        val dialog = builder.show()
        centerDialogTitle(dialog)
    }

    fun showManualScreen(activity: MainActivity) {
        loadManualTextIfNeeded(activity)
        activity.manualOverlayContainer?.visibility = View.VISIBLE
        activity.logsOverlayContainer?.visibility = View.GONE
        activity.mainContentContainer?.visibility = View.GONE
    }

    fun showLogsScreen(activity: MainActivity) {
        MainActivityHeaderAndLogsHandler.refreshEventLog(activity)
        activity.logsOverlayContainer?.visibility = View.VISIBLE
        activity.manualOverlayContainer?.visibility = View.GONE
        activity.mainContentContainer?.visibility = View.GONE
    }

    private fun loadManualTextIfNeeded(activity: MainActivity) {
        val textContent = activity.manualTextContent ?: return
        if (textContent.text.isNotEmpty()) return

        try {
            val htmlText = activity.assets.open("manual.html")
                .bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val formattedText: CharSequence = Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY)
            textContent.text = formattedText
        } catch (e: IOException) {
            EventLogger.log(activity, "Failed to load manual: " + e.message)
        }
    }

    fun showTargetTimeDialog(activity: MainActivity) {
        val pm = activity.preferenceManager ?: return
        val goalHour = pm.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR, AppDefaults.WAKE_UP_GOAL_HOUR)
        val goalMin = pm.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE, AppDefaults.WAKE_UP_GOAL_MINUTE)

        showTimePickerDialog(activity, goalHour, goalMin) { _, hourOfDay, minute ->
            val editor = pm.sharedPreferences.edit()
            editor.putInt(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR, hourOfDay)
            editor.putInt(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE, minute)
            if (!pm.contains(PreferenceKeys.KEY_CURRENT_WAKE_HOUR)) {
                editor.putInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, hourOfDay)
                editor.putInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, minute)
            }
            editor.remove(PreferenceKeys.KEY_WAKEUP_LAST_SCHEDULED_MS)
            editor.apply()
            MainActivityHeaderAndLogsHandler.updateTargetTimeButtonText(activity, hourOfDay, minute)
            val currHour = pm.getInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, hourOfDay)
            val currMin = pm.getInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, minute)
            MainActivityHeaderAndLogsHandler.updateCurrentWakeTimeButtonText(activity, currHour, currMin)
        }
    }

    fun showCurrentWakeTimeDialog(activity: MainActivity) {
        val pm = activity.preferenceManager ?: return
        val goalHour = pm.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR, AppDefaults.WAKE_UP_GOAL_HOUR)
        val goalMin = pm.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE, AppDefaults.WAKE_UP_GOAL_MINUTE)
        val currentHour = pm.getInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, goalHour)
        val currentMin = pm.getInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, goalMin)

        showTimePickerDialog(activity, currentHour, currentMin) { _, hourOfDay, minute ->
            pm.sharedPreferences.edit()
                .putInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, hourOfDay)
                .putInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, minute)
                .remove(PreferenceKeys.KEY_WAKEUP_LAST_SCHEDULED_MS)
                .apply()
            MainActivityHeaderAndLogsHandler.updateCurrentWakeTimeButtonText(activity, hourOfDay, minute)
        }
    }

    private fun showTimePickerDialog(
        activity: MainActivity,
        initialHour: Int,
        initialMin: Int,
        listener: TimePickerDialog.OnTimeSetListener
    ) {
        val is24Hour = android.text.format.DateFormat.is24HourFormat(activity)
        TimePickerDialog(activity, listener, initialHour, initialMin, is24Hour).show()
    }
}
