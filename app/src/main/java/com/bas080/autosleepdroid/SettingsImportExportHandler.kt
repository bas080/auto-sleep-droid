package com.bas080.autosleepdroid

import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.EditText
import android.widget.Toast
import org.json.JSONException
import org.json.JSONObject

internal class BoolPrefSpec(val key: String, val defaultValue: Boolean)

internal class IntPrefSpec(val key: String, val defaultValue: Int, val min: Int, val max: Int)

internal object SettingsImportExportHandler {

    private const val EXPORT_IMPORT_SCHEMA_VERSION = 1
    private const val IMPORT_INPUT_LINES = 4
    private const val HOUR_MIN = 0
    private const val HOUR_MAX = 23
    private const val MINUTE_MIN = 0
    private const val MINUTE_MAX = 59

    private val EXPORTED_BOOL_PREFS = arrayOf(
        BoolPrefSpec(PreferenceKeys.KEY_ACTIVE, true),
        BoolPrefSpec(PreferenceKeys.KEY_AUTO_TIMER_ENABLED, false),
        BoolPrefSpec(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, false),
        BoolPrefSpec(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, false),
        BoolPrefSpec(PreferenceKeys.KEY_DONATE_DIALOG_HIDDEN, false)
    )

    private val EXPORTED_INT_PREFS = arrayOf(
        IntPrefSpec(
            PreferenceKeys.KEY_DURATION_MINUTES,
            AppDefaults.DURATION_MINUTES,
            AppDefaults.MINUTES_MIN,
            AppDefaults.MINUTES_MAX
        ),
        IntPrefSpec(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR, AppDefaults.WAKE_UP_GOAL_HOUR, HOUR_MIN, HOUR_MAX),
        IntPrefSpec(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE, AppDefaults.WAKE_UP_GOAL_MINUTE, MINUTE_MIN, MINUTE_MAX),
        IntPrefSpec(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, AppDefaults.WAKE_UP_GOAL_HOUR, HOUR_MIN, HOUR_MAX),
        IntPrefSpec(
            PreferenceKeys.KEY_CURRENT_WAKE_MINUTE,
            AppDefaults.WAKE_UP_GOAL_MINUTE,
            MINUTE_MIN,
            MINUTE_MAX
        ),
        IntPrefSpec(
            PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES,
            AppDefaults.MIN_SLEEP_DURATION_MINUTES,
            AppDefaults.MINUTES_MIN,
            AppDefaults.MINUTES_MAX
        ),
        IntPrefSpec(
            PreferenceKeys.KEY_HC_MIN_DURATION_MINUTES,
            AppDefaults.HC_MIN_DURATION_MINUTES,
            0,
            AppDefaults.MINUTES_MAX
        )
    )

    fun exportSettings(activity: MainActivity, pm: PreferenceManager?) {
        pm ?: return
        try {
            val json = JSONObject()
            json.put("version", EXPORT_IMPORT_SCHEMA_VERSION)
            for (spec in EXPORTED_BOOL_PREFS) {
                json.put(spec.key, pm.getBoolean(spec.key, spec.defaultValue))
            }
            val goalHour = pm.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR, AppDefaults.WAKE_UP_GOAL_HOUR)
            val goalMin = pm.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE, AppDefaults.WAKE_UP_GOAL_MINUTE)
            for (spec in EXPORTED_INT_PREFS) {
                var def = spec.defaultValue
                if (PreferenceKeys.KEY_CURRENT_WAKE_HOUR == spec.key) def = goalHour
                else if (PreferenceKeys.KEY_CURRENT_WAKE_MINUTE == spec.key) def = goalMin
                json.put(spec.key, pm.getInt(spec.key, def))
            }

            val exportStr = json.toString()
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, exportStr)
                type = "text/plain"
            }
            activity.startActivity(Intent.createChooser(sendIntent, activity.getString(R.string.link_export)))

            EventLogger.log(activity, EventLogger.LEVEL_HIGH, "Exported settings via system share sheet")
        } catch (e: JSONException) {
            EventLogger.log(activity, "Failed to export settings: " + e.message)
        }
    }

    fun showImportDialog(activity: MainActivity, pm: PreferenceManager?) {
        val builder = AlertDialog.Builder(activity)
        builder.setTitle(R.string.dialog_import_title)
        builder.setMessage(R.string.dialog_import_message)

        val input = EditText(activity).apply {
            isSingleLine = false
            setLines(IMPORT_INPUT_LINES)
        }

        getClipboardJsonText(activity)?.let { input.setText(it) }

        builder.setView(input)

        builder.setPositiveButton(R.string.dialog_import_action) { _, _ ->
            val importStr = input.text.toString().trim()
            importSettings(activity, pm, importStr)
        }
        builder.setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.dismiss() }

        val dialog = builder.show()
        MainActivityDialogs.centerDialogTitle(dialog)
    }

    private fun getClipboardJsonText(activity: MainActivity): String? {
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipData = clipboard?.primaryClip
        val hasClip = clipData != null && clipData.itemCount > 0
        val text = if (hasClip) clipData!!.getItemAt(0).text?.toString()?.trim() else null
        return if (text != null && text.startsWith("{") && text.endsWith("}")) text else null
    }

    fun importSettings(activity: MainActivity, pm: PreferenceManager?, jsonStr: String?) {
        pm ?: return
        if (jsonStr.isNullOrEmpty()) {
            Toast.makeText(activity, R.string.toast_import_invalid, Toast.LENGTH_SHORT).show()
            EventLogger.log(activity, "Failed to import settings: empty input")
            return
        }

        try {
            val json = JSONObject(jsonStr)
            if (!json.has("version") || json.getInt("version") != EXPORT_IMPORT_SCHEMA_VERSION) {
                throw JSONException("Unsupported schema version")
            }

            val editor = pm.sharedPreferences.edit()
            importBooleanSettings(json, editor)
            importIntSettings(json, editor)

            editor.remove(PreferenceKeys.KEY_WAKEUP_LAST_SCHEDULED_MS).apply()

            Toast.makeText(activity, R.string.toast_import_success, Toast.LENGTH_SHORT).show()
            EventLogger.log(activity, EventLogger.LEVEL_HIGH, "Imported settings from string")
        } catch (e: JSONException) {
            Toast.makeText(activity, R.string.toast_import_invalid, Toast.LENGTH_SHORT).show()
            EventLogger.log(activity, "Failed to import settings: invalid format (${e.message})")
        }
    }

    private fun importBooleanSettings(json: JSONObject, editor: SharedPreferences.Editor) {
        for (spec in EXPORTED_BOOL_PREFS) {
            editor.putBoolean(spec.key, json.optBoolean(spec.key, spec.defaultValue))
        }
    }

    private fun importIntSettings(json: JSONObject, editor: SharedPreferences.Editor) {
        val importedGoalHour = json.optInt(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR, AppDefaults.WAKE_UP_GOAL_HOUR)
        val importedGoalMin = json.optInt(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE, AppDefaults.WAKE_UP_GOAL_MINUTE)

        for (spec in EXPORTED_INT_PREFS) {
            var def = spec.defaultValue
            if (PreferenceKeys.KEY_CURRENT_WAKE_HOUR == spec.key) def = importedGoalHour
            else if (PreferenceKeys.KEY_CURRENT_WAKE_MINUTE == spec.key) def = importedGoalMin

            val valNum = json.optInt(spec.key, def)
            if (valNum < spec.min || valNum > spec.max) {
                throw JSONException("${spec.key} out of range")
            }
            editor.putInt(spec.key, valNum)
        }
    }
}
