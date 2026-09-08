package com.bas080.autosleepdroid

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Html
import android.text.SpannableStringBuilder
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Calendar

class MainActivity : Activity(), EventLogger.Listener {

    private var mainContentContainer: View? = null
    private var manualOverlayContainer: View? = null
    private var manualTextContent: TextView? = null
    private var logsOverlayContainer: View? = null

    private var headerNap: View? = null
    private var headerTimer: View? = null
    private var headerAlarm: View? = null
    private var headerHealthConnect: View? = null
    private var headerAbout: View? = null

    private var rowNapDnd: View? = null
    private var switchNapDnd: Switch? = null
    private var rowEnableTimer: View? = null
    private var switchEnableTimer: Switch? = null
    private var inputDuration: View? = null
    private var textDurationValue: TextView? = null
    private var rowAutoTimer: View? = null
    private var switchAutoTimer: Switch? = null
    private var rowEnableGoal: View? = null
    private var switchEnableGoal: Switch? = null
    private var goalContainer: View? = null
    private var btnTargetTime: View? = null
    private var textTargetTimeValue: TextView? = null
    private var btnCurrentWakeTime: View? = null
    private var textCurrentWakeTimeValue: TextView? = null
    private var inputMinSleep: View? = null
    private var textMinSleepValue: TextView? = null
    private var rowHealthConnect: View? = null
    private var switchHealthConnect: Switch? = null
    private var inputHcMinDuration: View? = null
    private var textHcMinDurationValue: TextView? = null
    private var btnNap: View? = null
    private var textNapStatus: TextView? = null
    private var btnVersion: View? = null
    private var btnLinks: View? = null
    private var eventScrollView: ScrollView? = null
    private var eventLogText: TextView? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var uiEffectsHandle: PreferenceManager.EffectHandle? = null
    private var preferenceManager: PreferenceManager? = null
    private var isUserInitiatedAutoTimer = false
    private var isUserInitiatedHealthConnect = false
    private var isRequestingHealthConnectPermission = false

    private class BoolPrefSpec(val key: String, val defaultValue: Boolean)

    private class IntPrefSpec(val key: String, val defaultValue: Int, val min: Int, val max: Int)

    fun interface OnDurationSavedListener {
        fun onSaved(minutes: Int)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preferenceManager = PreferenceManager(getSharedPreferences(PreferenceKeys.PREFERENCES_NAME, MODE_PRIVATE))

        bindViews()
        setupHeaderAndLinks()
        setupConfigControls()

        requestNotificationPermissionOnStartupIfNeeded()
        startTimerService()
        requestExactAlarmPermissionIfNeeded()
        checkAndPromptCrashReport()
    }

    private fun checkAndPromptCrashReport() {
        val prefs = getSharedPreferences("crash_reports", MODE_PRIVATE)
        val pendingReport = prefs.getString("pending_crash_report", null)
        if (pendingReport != null) {
            prefs.edit().remove("pending_crash_report").apply()

            val builder = AlertDialog.Builder(this)
            builder.setTitle(R.string.dialog_crash_title)
            builder.setMessage(R.string.dialog_crash_message)
            builder.setPositiveButton(R.string.btn_send_report) { _, _ -> sendFeedbackEmail(pendingReport) }
            builder.setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.dismiss() }
            builder.show()
        }
    }

    private fun requestNotificationPermissionOnStartupIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                EventLogger.log(this, EventLogger.LEVEL_LOW, "Requesting notification permission on app startup")
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST
                )
            }
        }
    }

    private fun bindViews() {
        mainContentContainer = findViewById(R.id.main_content_container)
        manualOverlayContainer = findViewById(R.id.manual_overlay_container)
        manualTextContent = findViewById(R.id.manual_text_content)
        logsOverlayContainer = findViewById(R.id.logs_overlay_container)

        headerNap = findViewById(R.id.header_nap)
        headerTimer = findViewById(R.id.header_timer)
        headerAlarm = findViewById(R.id.header_alarm)
        headerHealthConnect = findViewById(R.id.header_health_connect)
        headerAbout = findViewById(R.id.header_about)

        rowNapDnd = findViewById(R.id.row_nap_dnd)
        switchNapDnd = findViewById(R.id.switch_nap_dnd)
        rowEnableTimer = findViewById(R.id.row_enable_timer)
        switchEnableTimer = findViewById(R.id.switch_enable_timer)
        inputDuration = findViewById(R.id.input_duration)
        textDurationValue = findViewById(R.id.text_duration_value)
        rowAutoTimer = findViewById(R.id.row_auto_timer)
        switchAutoTimer = findViewById(R.id.switch_auto_timer)
        rowEnableGoal = findViewById(R.id.row_enable_goal)
        switchEnableGoal = findViewById(R.id.switch_enable_goal)
        goalContainer = findViewById(R.id.goal_container)
        btnTargetTime = findViewById(R.id.btn_target_time)
        textTargetTimeValue = findViewById(R.id.text_target_time_value)
        btnCurrentWakeTime = findViewById(R.id.btn_current_wake_time)
        textCurrentWakeTimeValue = findViewById(R.id.text_current_wake_time_value)
        inputMinSleep = findViewById(R.id.input_min_sleep)
        textMinSleepValue = findViewById(R.id.text_min_sleep_value)
        rowHealthConnect = findViewById(R.id.row_health_connect)
        switchHealthConnect = findViewById(R.id.switch_health_connect)
        inputHcMinDuration = findViewById(R.id.input_hc_min_duration)
        textHcMinDurationValue = findViewById(R.id.text_hc_min_duration_value)
        btnNap = findViewById(R.id.btn_nap)
        textNapStatus = findViewById(R.id.text_nap_status)
        btnVersion = findViewById(R.id.btn_version)
        btnLinks = findViewById(R.id.btn_links)
        eventScrollView = findViewById(R.id.event_scroll_view)
        eventLogText = findViewById(R.id.event_log_text)
    }

    private fun setupHeaderAndLinks() {
        val versionText = findViewById<TextView>(R.id.app_version_text)
        versionText?.text = getString(R.string.version_label, BuildConfig.VERSION_NAME)

        val btnManualBack = findViewById<Button>(R.id.btn_manual_back)
        btnManualBack?.setOnClickListener { hideOverlays() }

        val btnLogsBack = findViewById<Button>(R.id.btn_logs_back)
        btnLogsBack?.setOnClickListener { hideOverlays() }

        btnVersion?.setOnClickListener { openUrl("https://github.com/bas080/auto-sleep-droid/releases") }

        btnLinks?.setOnClickListener { showLinksDialog() }
    }

    private fun showLinksDialog() {
        val options = arrayOf<CharSequence>(
            getString(R.string.link_manual),
            getString(R.string.link_logs),
            getString(R.string.link_feedback),
            getString(R.string.link_donate),
            getString(R.string.link_export),
            getString(R.string.link_import)
        )

        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.label_links)
        builder.setItems(options) { dialog, which ->
            when (which) {
                0 -> showManualScreen()
                1 -> showLogsScreen()
                2 -> sendFeedbackEmail()
                3 -> openUrl("https://liberapay.com/bas080")
                4 -> exportSettings()
                5 -> showImportDialog()
            }
        }
        builder.setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    fun sendFeedbackEmail(crashReport: String? = null) {
        val subject = "Auto Sleep Droid Feedback (v${BuildConfig.VERSION_NAME})"
        val bodyBuilder = StringBuilder()
        if (!crashReport.isNullOrEmpty()) {
            bodyBuilder.append("Crash Report:\n").append(crashReport).append("\n\n")
            val events = EventLogger.getEvents(this)
            if (events.isNotEmpty()) {
                bodyBuilder.append("Logs:\n")
                for (event in events) {
                    bodyBuilder.append(EventLogger.formatColoredEvent(this, event).toString()).append("\n")
                }
                bodyBuilder.append("\n")
            }
        }
        bodyBuilder.append("---\nApp Version: ").append(BuildConfig.VERSION_NAME)
            .append("\nAndroid Version: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")")
            .append("\nDevice: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL)

        val bodyTemplate = bodyBuilder.toString()

        val mailtoUri = Uri.parse(
            "mailto:bas080@hotmail.com" +
                    "?subject=" + Uri.encode(subject) +
                    "&body=" + Uri.encode(bodyTemplate)
        )

        val intent = Intent(Intent.ACTION_SENDTO, mailtoUri)
        intent.putExtra(Intent.EXTRA_SUBJECT, subject)
        intent.putExtra(Intent.EXTRA_TEXT, bodyTemplate)

        try {
            startActivity(Intent.createChooser(intent, getString(R.string.link_feedback)))
        } catch (e: Exception) {
            EventLogger.log(this, "Failed to launch email client: " + e.message)
            Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showManualScreen() {
        loadManualTextIfNeeded()
        manualOverlayContainer?.visibility = View.VISIBLE
        logsOverlayContainer?.visibility = View.GONE
        mainContentContainer?.visibility = View.GONE
    }

    private fun showLogsScreen() {
        refreshEventLog()
        logsOverlayContainer?.visibility = View.VISIBLE
        manualOverlayContainer?.visibility = View.GONE
        mainContentContainer?.visibility = View.GONE
    }

    private fun hideOverlays() {
        manualOverlayContainer?.visibility = View.GONE
        logsOverlayContainer?.visibility = View.GONE
        mainContentContainer?.visibility = View.VISIBLE
    }

    private fun loadManualTextIfNeeded() {
        val textContent = manualTextContent ?: return
        if (textContent.text.isNotEmpty()) {
            return
        }
        val htmlText = try {
            assets.open("manual.html").bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } catch (e: IOException) {
            EventLogger.log(this, "Failed to load manual: " + e.message)
            return
        }

        val formattedText: CharSequence = if (Build.VERSION.SDK_INT >= 24) {
            Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(htmlText)
        }

        textContent.text = formattedText
    }

    override fun onBackPressed() {
        if ((manualOverlayContainer != null && manualOverlayContainer!!.visibility == View.VISIBLE)
            || (logsOverlayContainer != null && logsOverlayContainer!!.visibility == View.VISIBLE)
        ) {
            hideOverlays()
            return
        }
        super.onBackPressed()
    }

    fun showDurationDialog(
        titleResId: Int,
        prefKey: String,
        defaultMinutes: Int,
        minHours: Int = 0,
        maxHours: Int = 24,
        minuteStep: Int = 1,
        listener: OnDurationSavedListener? = null
    ) {
        val currentMinutes = preferenceManager?.getInt(prefKey, defaultMinutes) ?: defaultMinutes

        val durationInputView = DurationInputView(this)
        durationInputView.configure(minHours, maxHours, minuteStep)
        durationInputView.setPadding(48, 24, 48, 24)
        durationInputView.setTotalMinutes(currentMinutes)

        val builder = AlertDialog.Builder(this)
        builder.setTitle(titleResId)
        builder.setView(durationInputView)
        builder.setPositiveButton(R.string.dialog_ok) { _, _ ->
            val minutes = durationInputView.getTotalMinutes()
            if (minutes > 0) {
                preferenceManager?.edit()?.putInt(prefKey, minutes)?.apply()
                listener?.onSaved(minutes)
            } else {
                Toast.makeText(this@MainActivity, R.string.toast_duration_invalid, Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    private fun setupConfigControls() {
        rowNapDnd?.setOnClickListener {
            switchNapDnd?.let { sw ->
                sw.isPressed = true
                sw.toggle()
                sw.isPressed = false
            }
        }

        switchNapDnd?.setOnCheckedChangeListener { buttonView, isChecked ->
            preferenceManager?.edit()?.putBoolean(PreferenceKeys.KEY_NAP_DND_ENABLED, isChecked)?.apply()
            val isUserInitiated = buttonView.isPressed
            if (isChecked && isUserInitiated && !isDndPermissionGranted()) {
                EventLogger.log(this, EventLogger.LEVEL_HIGH, "Nap DND enabled; DND policy permission missing, opening settings")
                openDndPermissionSettings()
            } else {
                EventLogger.log(this, EventLogger.LEVEL_HIGH, if (isChecked) "Nap DND enabled" else "Nap DND disabled")
            }
        }

        rowEnableTimer?.setOnClickListener {
            switchEnableTimer?.let { sw ->
                sw.isPressed = true
                sw.toggle()
                sw.isPressed = false
            }
        }

        switchEnableTimer?.setOnCheckedChangeListener { _, isChecked ->
            preferenceManager?.edit()?.putBoolean(PreferenceKeys.KEY_ACTIVE, isChecked)?.apply()
            val goalEnabled = preferenceManager?.getBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, false) ?: false
            updateInputEnabledStates(isChecked, goalEnabled)
            EventLogger.log(this, EventLogger.LEVEL_HIGH, if (isChecked) "Timer enabled from UI" else "Timer disabled from UI")
        }

        inputDuration?.setOnClickListener {
            showDurationDialog(
                R.string.label_duration,
                PreferenceKeys.KEY_DURATION_MINUTES,
                AppDefaults.DURATION_MINUTES,
                0, 12, 5
            ) { minutes ->
                textDurationValue?.text = DurationUtils.formatDurationString(minutes)
            }
        }

        rowAutoTimer?.setOnClickListener {
            isUserInitiatedAutoTimer = true
            switchAutoTimer?.let { sw ->
                sw.isPressed = true
                sw.toggle()
                sw.isPressed = false
            }
        }

        switchAutoTimer?.setOnCheckedChangeListener { buttonView, isChecked ->
            val pm = preferenceManager ?: return@setOnCheckedChangeListener
            val editor = pm.edit()
            editor.putBoolean(PreferenceKeys.KEY_AUTO_TIMER_ENABLED, isChecked)
            val isUserInitiated = buttonView.isPressed || isUserInitiatedAutoTimer
            isUserInitiatedAutoTimer = false
            if (isChecked && isUserInitiated) {
                val dndActive = isDndActive()
                editor.putBoolean(PreferenceKeys.KEY_ACTIVE, dndActive)
                switchEnableTimer?.isChecked = dndActive
                openDndSettings()
            }
            editor.apply()
            if (isChecked) {
                EventLogger.log(this, EventLogger.LEVEL_HIGH, if (isUserInitiated) "Auto sleep timer (DND) enabled; opening DND settings" else "Auto sleep timer (DND) enabled")
            } else {
                EventLogger.log(this, EventLogger.LEVEL_HIGH, "Auto sleep timer (DND) disabled")
            }
        }

        rowEnableGoal?.setOnClickListener {
            switchEnableGoal?.let { sw ->
                sw.isPressed = true
                sw.toggle()
                sw.isPressed = false
            }
        }

        switchEnableGoal?.setOnCheckedChangeListener { _, isChecked ->
            preferenceManager?.sharedPreferences?.edit()
                ?.putBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, isChecked)
                ?.remove(PreferenceKeys.KEY_WAKEUP_LAST_SCHEDULED_MS)
                ?.apply()
            val timerActive = preferenceManager?.getBoolean(PreferenceKeys.KEY_ACTIVE, true) ?: true
            updateInputEnabledStates(timerActive, isChecked)
            EventLogger.log(this, EventLogger.LEVEL_HIGH, if (isChecked) "Wake-up goal enabled" else "Wake-up goal disabled")
        }

        btnTargetTime?.setOnClickListener { showTargetTimeDialog() }

        btnCurrentWakeTime?.setOnClickListener { showCurrentWakeTimeDialog() }

        inputMinSleep?.setOnClickListener {
            showDurationDialog(
                R.string.label_min_sleep,
                PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES,
                AppDefaults.MIN_SLEEP_DURATION_MINUTES,
                0, 16, 15
            ) { minutes ->
                preferenceManager?.edit()?.remove(PreferenceKeys.KEY_WAKEUP_LAST_SCHEDULED_MS)?.apply()
                textMinSleepValue?.text = DurationUtils.formatDurationString(minutes)
            }
        }

        rowHealthConnect?.setOnClickListener {
            isUserInitiatedHealthConnect = true
            switchHealthConnect?.let { sw ->
                sw.isPressed = true
                sw.toggle()
                sw.isPressed = false
            }
        }

        switchHealthConnect?.setOnCheckedChangeListener { buttonView, isChecked ->
            val isUserInitiated = buttonView.isPressed || isUserInitiatedHealthConnect
            isUserInitiatedHealthConnect = false
            if (isChecked) {
                if (!HealthConnectManager.isHealthConnectAvailable(this)) {
                    switchHealthConnect?.isChecked = false
                    Toast.makeText(this, R.string.toast_health_connect_not_available, Toast.LENGTH_SHORT).show()
                    EventLogger.log(this, EventLogger.LEVEL_HIGH, "Health Connect requested but SDK is unavailable")
                    return@setOnCheckedChangeListener
                }
                preferenceManager?.edit()?.putBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, true)?.apply()
                if (isUserInitiated) {
                    Toast.makeText(this, R.string.toast_health_connect_enabled, Toast.LENGTH_SHORT).show()
                    isRequestingHealthConnectPermission = true
                    HealthConnectManager.hasSleepWritePermission(this) { hasPermission ->
                        if (!hasPermission) {
                            EventLogger.log(this, EventLogger.LEVEL_HIGH, "Health Connect sync enabled; opening permissions settings")
                            HealthConnectManager.openHealthConnectPermissions(this)
                        } else {
                            isRequestingHealthConnectPermission = false
                            EventLogger.log(this, EventLogger.LEVEL_HIGH, "Health Connect sync enabled")
                        }
                    }
                } else {
                    EventLogger.log(this, EventLogger.LEVEL_HIGH, "Health Connect sync enabled")
                }
            } else {
                preferenceManager?.edit()?.putBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, false)?.apply()
                if (isUserInitiated) {
                    isRequestingHealthConnectPermission = false
                    EventLogger.log(this, EventLogger.LEVEL_HIGH, "Health Connect sync disabled; revoking permissions")
                    Toast.makeText(this, R.string.toast_health_connect_disabled, Toast.LENGTH_SHORT).show()
                    HealthConnectManager.revokeAllPermissions(this)
                }
            }
            val active = preferenceManager?.getBoolean(PreferenceKeys.KEY_ACTIVE, true) ?: true
            val goalEnabled = preferenceManager?.getBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, false) ?: false
            updateInputEnabledStates(active, goalEnabled, isChecked)
        }

        inputHcMinDuration?.setOnClickListener {
            showDurationDialog(
                R.string.label_hc_min_duration,
                PreferenceKeys.KEY_HC_MIN_DURATION_MINUTES,
                AppDefaults.HC_MIN_DURATION_MINUTES,
                0, 2, 5
            ) { minutes ->
                textHcMinDurationValue?.text = DurationUtils.formatDurationString(minutes)
            }
        }
    }

    private fun updateInputEnabledStates(active: Boolean, goalEnabled: Boolean) {
        val healthConnectEnabled = preferenceManager?.getBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, false) ?: false
        updateInputEnabledStates(active, goalEnabled, healthConnectEnabled)
    }

    private fun updateInputEnabledStates(active: Boolean, goalEnabled: Boolean, healthConnectEnabled: Boolean) {
        setRowEnabled(headerNap, true)
        setRowEnabled(headerTimer, true)
        setRowEnabled(headerAlarm, true)
        setRowEnabled(headerHealthConnect, true)
        setRowEnabled(headerAbout, true)

        setRowEnabled(rowNapDnd, true)
        setRowEnabled(rowEnableTimer, true)
        setRowEnabled(inputDuration, true)
        setRowEnabled(rowAutoTimer, true)
        setRowEnabled(rowEnableGoal, true)

        setRowEnabled(btnTargetTime, goalEnabled)
        setRowEnabled(btnCurrentWakeTime, goalEnabled)
        setRowEnabled(inputMinSleep, goalEnabled)
        setRowEnabled(rowHealthConnect, true)
        setRowEnabled(inputHcMinDuration, healthConnectEnabled)
        setRowEnabled(btnVersion, true)

        goalContainer?.visibility = View.VISIBLE
    }

    private fun isDndPermissionGranted(): Boolean {
        if (Build.VERSION.SDK_INT >= 23) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager?
            return nm != null && nm.isNotificationPolicyAccessGranted
        }
        return true
    }

    private fun isDndActive(): Boolean {
        if (Build.VERSION.SDK_INT >= 23) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager?
            if (nm != null) {
                return nm.currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL
            }
        }
        return false
    }

    private fun openSettingsWithFallback(primaryAction: String, fallbackAction: String) {
        try {
            startActivity(Intent(primaryAction))
        } catch (e: Exception) {
            try {
                startActivity(Intent(fallbackAction))
            } catch (ex: Exception) {
                Toast.makeText(this, "Could not open DND settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openDndPermissionSettings() {
        openSettingsWithFallback(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS)
    }

    private fun openDndSettings() {
        openSettingsWithFallback(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    }

    private fun setRowEnabled(view: View?, enabled: Boolean) {
        view ?: return
        view.isEnabled = enabled
        if (view !is SettingRowView) {
            view.isClickable = enabled
            view.isFocusable = enabled
            view.alpha = if (enabled) 1.0f else 0.38f
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    setChildViewsEnabled(view.getChildAt(i), enabled)
                }
            }
        }
    }

    private fun setChildViewsEnabled(view: View?, enabled: Boolean) {
        view ?: return
        view.isEnabled = enabled
        if (view is Switch) {
            view.isClickable = enabled
            view.isFocusable = enabled
        } else {
            view.isClickable = false
            view.isFocusable = false
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                setChildViewsEnabled(view.getChildAt(i), enabled)
            }
        }
    }

    private fun showTimePickerDialog(initialHour: Int, initialMin: Int, listener: TimePickerDialog.OnTimeSetListener) {
        val is24Hour = android.text.format.DateFormat.is24HourFormat(this)
        TimePickerDialog(this, listener, initialHour, initialMin, is24Hour).show()
    }

    private fun showTargetTimeDialog() {
        val pm = preferenceManager ?: return
        val goalHour = pm.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR, AppDefaults.WAKE_UP_GOAL_HOUR)
        val goalMin = pm.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE, AppDefaults.WAKE_UP_GOAL_MINUTE)

        showTimePickerDialog(goalHour, goalMin) { _, hourOfDay, minute ->
            val editor = pm.sharedPreferences.edit()
            editor.putInt(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR, hourOfDay)
            editor.putInt(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE, minute)
            if (!pm.contains(PreferenceKeys.KEY_CURRENT_WAKE_HOUR)) {
                editor.putInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, hourOfDay)
                editor.putInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, minute)
            }
            editor.remove(PreferenceKeys.KEY_WAKEUP_LAST_SCHEDULED_MS)
            editor.apply()
            updateTargetTimeButtonText(hourOfDay, minute)
            updateCurrentWakeTimeButtonText(pm.getInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, hourOfDay), pm.getInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, minute))
            redrawNotification()
        }
    }

    private fun showCurrentWakeTimeDialog() {
        val pm = preferenceManager ?: return
        val goalHour = pm.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR, AppDefaults.WAKE_UP_GOAL_HOUR)
        val goalMin = pm.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE, AppDefaults.WAKE_UP_GOAL_MINUTE)
        val currentHour = pm.getInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, goalHour)
        val currentMin = pm.getInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, goalMin)

        showTimePickerDialog(currentHour, currentMin) { _, hourOfDay, minute ->
            pm.sharedPreferences.edit()
                .putInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, hourOfDay)
                .putInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, minute)
                .remove(PreferenceKeys.KEY_WAKEUP_LAST_SCHEDULED_MS)
                .apply()
            updateCurrentWakeTimeButtonText(hourOfDay, minute)
            redrawNotification()
        }
    }

    private fun updateTargetTimeButtonText(hour: Int, minute: Int) {
        textTargetTimeValue?.text = formatTime(hour, minute)
    }

    private fun updateCurrentWakeTimeButtonText(hour: Int, minute: Int) {
        textCurrentWakeTimeValue?.text = formatTime(hour, minute)
    }

    private fun formatTime(hour: Int, minute: Int): String {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        val timeFormat = android.text.format.DateFormat.getTimeFormat(this)
        return timeFormat.format(cal.time)
    }

    private fun getComputedDurationString(pm: PreferenceManager?, key: String, defaultMinutes: Int): String {
        pm ?: return DurationUtils.formatDurationString(defaultMinutes)
        return pm.getComputed(key, PreferenceComputations.formatDuration(key, defaultMinutes)) ?: DurationUtils.formatDurationString(defaultMinutes)
    }

    private fun updateNapUi(getter: PreferenceGetter) {
        val napDndEnabled = getter.getBoolean(PreferenceKeys.KEY_NAP_DND_ENABLED, false)
        val napDurationMinutes = getter.getInt(PreferenceKeys.KEY_NAP_DURATION_MINUTES, AppDefaults.NAP_DURATION_MINUTES)
        val napEndsAt = getter.getLong(PreferenceKeys.KEY_NAP_ALARM_ENDS_AT, 0L)

        val isNapActive = if (preferenceManager != null)
            true == preferenceManager?.getComputed(PreferenceComputations.IS_NAP_ACTIVE)
        else
            napEndsAt > System.currentTimeMillis()

        switchNapDnd?.isChecked = napDndEnabled
        if (btnNap != null && textNapStatus != null) {
            if (isNapActive) {
                textNapStatus?.setText(R.string.action_cancel_nap)
                btnNap?.setOnClickListener { cancelNap() }
            } else {
                textNapStatus?.text = getComputedDurationString(preferenceManager, PreferenceKeys.KEY_NAP_DURATION_MINUTES, napDurationMinutes)
                btnNap?.setOnClickListener { openNapDialog() }
            }
        }
    }

    private fun updateTimerUi(getter: PreferenceGetter) {
        val active = getter.getBoolean(PreferenceKeys.KEY_ACTIVE, true)
        val durationMinutes = getter.getInt(PreferenceKeys.KEY_DURATION_MINUTES, AppDefaults.DURATION_MINUTES)
        val autoTimer = getter.getBoolean(PreferenceKeys.KEY_AUTO_TIMER_ENABLED, false)

        switchEnableTimer?.isChecked = active
        textDurationValue?.text = getComputedDurationString(preferenceManager, PreferenceKeys.KEY_DURATION_MINUTES, durationMinutes)
        switchAutoTimer?.isChecked = autoTimer
    }

    private fun updateGoalUi(getter: PreferenceGetter) {
        val active = getter.getBoolean(PreferenceKeys.KEY_ACTIVE, true)
        val goalEnabled = getter.getBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, false)
        val healthConnectEnabled = getter.getBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, false)
        val goalHour = getter.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR, AppDefaults.WAKE_UP_GOAL_HOUR)
        val goalMin = getter.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE, AppDefaults.WAKE_UP_GOAL_MINUTE)
        val currentHour = getter.getInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, goalHour)
        val currentMin = getter.getInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, goalMin)
        val minSleepMin = getter.getInt(PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES, AppDefaults.MIN_SLEEP_DURATION_MINUTES)

        switchEnableGoal?.isChecked = goalEnabled
        updateTargetTimeButtonText(goalHour, goalMin)
        updateCurrentWakeTimeButtonText(currentHour, currentMin)
        textMinSleepValue?.text = getComputedDurationString(preferenceManager, PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES, minSleepMin)
        updateInputEnabledStates(active, goalEnabled, healthConnectEnabled)
    }

    private fun updateHealthConnectUi(getter: PreferenceGetter) {
        val healthConnectEnabled = getter.getBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, false)
        val hcMinDurationMin = getter.getInt(PreferenceKeys.KEY_HC_MIN_DURATION_MINUTES, AppDefaults.HC_MIN_DURATION_MINUTES)

        switchHealthConnect?.isChecked = healthConnectEnabled
        textHcMinDurationValue?.text = getComputedDurationString(preferenceManager, PreferenceKeys.KEY_HC_MIN_DURATION_MINUTES, hcMinDurationMin)
        if (healthConnectEnabled) {
            if (!isRequestingHealthConnectPermission) {
                HealthConnectManager.hasSleepWritePermission(this) { hasPermission ->
                    if (!hasPermission) {
                        preferenceManager?.edit()?.putBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, false)?.apply()
                        switchHealthConnect?.isChecked = false
                        EventLogger.log(this, EventLogger.LEVEL_HIGH, "Health Connect permission revoked; disabling sync")
                    }
                }
            }
        }
    }

    private fun openNapDialog() {
        val intent = Intent(this, NapDialogActivity::class.java)
        startActivity(intent)
    }

    private fun cancelNap() {
        preferenceManager?.edit()?.remove(PreferenceKeys.KEY_NAP_ALARM_ENDS_AT)?.apply()

        val serviceIntent = Intent(this, MainService::class.java).apply {
            action = MainService.ACTION_CANCEL_NAP
        }
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun exportSettings() {
        val pm = preferenceManager ?: return
        try {
            val json = JSONObject()
            json.put("version", 1)
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
            startActivity(Intent.createChooser(sendIntent, getString(R.string.link_export)))

            EventLogger.log(this, EventLogger.LEVEL_HIGH, "Exported settings via system share sheet")
        } catch (e: JSONException) {
            EventLogger.log(this, "Failed to export settings: " + e.message)
        }
    }

    private fun showImportDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.dialog_import_title)
        builder.setMessage(R.string.dialog_import_message)

        val input = EditText(this)
        input.isSingleLine = false
        input.setLines(4)

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        if (clipboard != null && clipboard.hasPrimaryClip()) {
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val text = clipData.getItemAt(0).text
                if (text != null) {
                    val str = text.toString().trim()
                    if (str.startsWith("{") && str.endsWith("}")) {
                        input.setText(str)
                    }
                }
            }
        }

        builder.setView(input)

        builder.setPositiveButton(R.string.dialog_import_action) { _, _ ->
            val importStr = input.text.toString().trim()
            importSettings(importStr)
        }
        builder.setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.dismiss() }

        builder.show()
    }

    private fun importSettings(jsonStr: String?) {
        val pm = preferenceManager ?: return
        if (jsonStr.isNullOrEmpty()) {
            Toast.makeText(this, R.string.toast_import_invalid, Toast.LENGTH_SHORT).show()
            EventLogger.log(this, "Failed to import settings: empty input")
            return
        }

        try {
            val json = JSONObject(jsonStr)
            if (!json.has("version") || json.getInt("version") != 1) {
                throw JSONException("Unsupported schema version")
            }

            val editor = pm.sharedPreferences.edit()
            for (spec in EXPORTED_BOOL_PREFS) {
                editor.putBoolean(spec.key, json.optBoolean(spec.key, spec.defaultValue))
            }

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

            editor.remove(PreferenceKeys.KEY_WAKEUP_LAST_SCHEDULED_MS).apply()

            Toast.makeText(this, R.string.toast_import_success, Toast.LENGTH_SHORT).show()
            EventLogger.log(this, EventLogger.LEVEL_HIGH, "Imported settings from string")

            redrawNotification()
        } catch (e: JSONException) {
            Toast.makeText(this, R.string.toast_import_invalid, Toast.LENGTH_SHORT).show()
            EventLogger.log(this, "Failed to import settings: invalid format (${e.message})")
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    private fun requestExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 31) {
            val alarmManager = getSystemService(ALARM_SERVICE) as android.app.AlarmManager?
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                EventLogger.log(this, EventLogger.LEVEL_LOW, "Opening exact alarm settings")
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        EventLogger.log(this, EventLogger.LEVEL_LOW, "MainActivity new intent")
        startTimerService()
    }

    override fun onResume() {
        super.onResume()
        EventLogger.setListener(this)
        refreshEventLog()
        if (isRequestingHealthConnectPermission) {
            HealthConnectManager.hasSleepWritePermission(this) { hasPermission ->
                isRequestingHealthConnectPermission = false
                if (hasPermission) {
                    preferenceManager?.edit()?.putBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, true)?.apply()
                    switchHealthConnect?.isChecked = true
                    EventLogger.log(this, EventLogger.LEVEL_HIGH, "Health Connect sync enabled and permission granted")
                } else {
                    preferenceManager?.edit()?.putBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, false)?.apply()
                    switchHealthConnect?.isChecked = false
                    Toast.makeText(this, R.string.toast_health_connect_disabled, Toast.LENGTH_SHORT).show()
                    EventLogger.log(this, EventLogger.LEVEL_HIGH, "Health Connect permission not granted; disabling sync")
                }
            }
        }
        redrawNotification()
        registerPreferenceListeners()
    }

    private fun registerPreferenceListeners() {
        val pm = preferenceManager ?: return

        if (uiEffectsHandle == null) {
            uiEffectsHandle = pm.watchEffects(
                PreferenceManager.PreferenceEffect { getter -> updateNapUi(getter) },
                PreferenceManager.PreferenceEffect { getter -> updateTimerUi(getter) },
                PreferenceManager.PreferenceEffect { getter -> updateGoalUi(getter) },
                PreferenceManager.PreferenceEffect { getter -> updateHealthConnectUi(getter) }
            )
        }
    }

    private fun unregisterPreferenceListeners() {
        uiEffectsHandle?.dispose()
        uiEffectsHandle = null
    }

    override fun onPause() {
        super.onPause()
        EventLogger.setListener(null)
        unregisterPreferenceListeners()
    }

    override fun onStop() {
        super.onStop()
        unregisterPreferenceListeners()
    }

    override fun onDestroy() {
        preferenceManager?.shutdown()
        preferenceManager = null
        super.onDestroy()
    }

    private fun refreshEventLog() {
        val events = EventLogger.getEvents(this)
        val ssb = SpannableStringBuilder()
        for (event in events) {
            ssb.append(EventLogger.formatColoredEvent(this, event)).append("\n")
        }
        eventLogText?.let {
            it.text = ssb
            scrollToBottom()
        }
    }

    override fun onEventLogged(event: String) {
        eventLogText?.let {
            it.append(EventLogger.formatColoredEvent(this, event))
            it.append("\n")
            scrollToBottom()
        }
    }

    private fun scrollToBottom() {
        eventScrollView?.post { eventScrollView?.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun redrawNotification() {
        val serviceIntent = Intent(this, MainService::class.java).apply {
            action = MainService.ACTION_REDRAW_NOTIFICATION
        }
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            EventLogger.log(this, EventLogger.LEVEL_LOW, "Notification permission granted: $granted")
            if (granted) {
                startTimerService()
            }
            redrawNotification()
        }
    }

    private fun startTimerService() {
        val serviceIntent = Intent(this, MainService::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST = 100

        private val EXPORTED_BOOL_PREFS = arrayOf(
            BoolPrefSpec(PreferenceKeys.KEY_NAP_DND_ENABLED, false),
            BoolPrefSpec(PreferenceKeys.KEY_ACTIVE, true),
            BoolPrefSpec(PreferenceKeys.KEY_AUTO_TIMER_ENABLED, false),
            BoolPrefSpec(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, false),
            BoolPrefSpec(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, false)
        )

        private val EXPORTED_INT_PREFS = arrayOf(
            IntPrefSpec(PreferenceKeys.KEY_DURATION_MINUTES, AppDefaults.DURATION_MINUTES, AppDefaults.MINUTES_MIN, AppDefaults.MINUTES_MAX),
            IntPrefSpec(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR, AppDefaults.WAKE_UP_GOAL_HOUR, 0, 23),
            IntPrefSpec(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE, AppDefaults.WAKE_UP_GOAL_MINUTE, 0, 59),
            IntPrefSpec(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, AppDefaults.WAKE_UP_GOAL_HOUR, 0, 23),
            IntPrefSpec(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, AppDefaults.WAKE_UP_GOAL_MINUTE, 0, 59),
            IntPrefSpec(PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES, AppDefaults.MIN_SLEEP_DURATION_MINUTES, AppDefaults.MINUTES_MIN, AppDefaults.MINUTES_MAX),
            IntPrefSpec(PreferenceKeys.KEY_HC_MIN_DURATION_MINUTES, AppDefaults.HC_MIN_DURATION_MINUTES, 0, AppDefaults.MINUTES_MAX)
        )
    }
}
