package com.bas080.autosleepdroid

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity(), EventLogger.Listener {

    internal var mainContentContainer: View? = null
    internal var manualOverlayContainer: View? = null
    internal var manualTextContent: TextView? = null
    internal var logsOverlayContainer: View? = null
    internal var feedbackOverlayContainer: View? = null
    internal var feedbackTitleText: TextView? = null
    internal var feedbackPromptText: TextView? = null
    internal var feedbackTextContent: EditText? = null
    internal var chkIncludeLogs: CheckBox? = null
    internal var btnDiscardCrash: Button? = null
    internal var btnCopyFeedback: Button? = null
    internal var btnSendFeedbackEmail: Button? = null
    internal var btnFeedbackBack: Button? = null
    internal var btnReportCrash: View? = null

    internal var headerDnd: View? = null
    internal var headerTimer: View? = null
    internal var headerAlarm: View? = null
    internal var headerHealthConnect: View? = null
    internal var headerBackup: View? = null
    internal var headerAbout: View? = null

    internal var rowEnableTimer: SettingRowView? = null
    internal var switchEnableTimer: Switch? = null
    internal var inputDuration: View? = null
    internal var textDurationValue: TextView? = null
    internal var rowAutoTimer: SettingRowView? = null
    internal var switchAutoTimer: Switch? = null
    internal var rowEnableGoal: SettingRowView? = null
    internal var switchEnableGoal: Switch? = null
    internal var goalContainer: View? = null
    internal var btnTargetTime: View? = null
    internal var textTargetTimeValue: TextView? = null
    internal var btnCurrentWakeTime: View? = null
    internal var textCurrentWakeTimeValue: TextView? = null
    internal var inputMinSleep: View? = null
    internal var textMinSleepValue: TextView? = null
    internal var rowHealthConnect: SettingRowView? = null
    internal var switchHealthConnect: Switch? = null
    internal var inputHcMinDuration: View? = null
    internal var textHcMinDurationValue: TextView? = null
    internal var btnExport: View? = null
    internal var btnImport: View? = null
    internal var btnVersion: View? = null
    internal var btnFeedback: View? = null
    internal var btnLinks: View? = null
    internal var eventScrollView: ScrollView? = null
    internal var eventLogText: TextView? = null

    internal var uiEffectsHandle: PreferenceManager.EffectHandle? = null
    internal var preferenceManager: PreferenceManager? = null
    internal var isRequestingHealthConnectPermission = false

    internal val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        EventLogger.log(this, EventLogger.LEVEL_LOW, "Notification permission granted: $isGranted")
        if (isGranted) {
            MainActivityHeaderAndLogsHandler.startTimerService(this)
        }
    }

    class DurationPickerBounds(
        val minHours: Int = 0,
        val maxHours: Int = 24,
        val minuteStep: Int = 1
    )

    fun interface OnDurationSavedListener {
        fun onSaved(minutes: Int)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()

        FeedbackOverlayController.checkAndPromptCrashReport(this)

        preferenceManager = PreferenceManager(getSharedPreferences(PreferenceKeys.PREFERENCES_NAME, MODE_PRIVATE))

        MainActivityHeaderAndLogsHandler.setupHeaderAndLinks(this)
        MainActivityControlsHandler.setupConfigControls(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (MainActivityHeaderAndLogsHandler.isAnyOverlayVisible(this@MainActivity)) {
                    MainActivityHeaderAndLogsHandler.hideOverlays(this@MainActivity)
                } else {
                    finish()
                }
            }
        })
        FeedbackOverlayController.updateReportCrashRowVisibility(this)

        MainActivityPermissionAndSettingsHandler.requestNotificationPermissionOnStartupIfNeeded(this)
        MainActivityPermissionAndSettingsHandler.requestExactAlarmPermissionIfNeeded(this)
    }

    private fun bindViews() {
        mainContentContainer = findViewById(R.id.main_content_container)
        manualOverlayContainer = findViewById(R.id.manual_overlay_container)
        manualTextContent = findViewById(R.id.manual_text_content)
        logsOverlayContainer = findViewById(R.id.logs_overlay_container)
        feedbackOverlayContainer = findViewById(R.id.feedback_overlay_container)
        feedbackTitleText = findViewById(R.id.feedback_title_text)
        feedbackPromptText = findViewById(R.id.feedback_prompt_text)
        feedbackTextContent = findViewById(R.id.feedback_text_content)
        chkIncludeLogs = findViewById(R.id.chk_include_logs)
        btnDiscardCrash = findViewById(R.id.btn_discard_crash)
        btnCopyFeedback = findViewById(R.id.btn_copy_feedback)
        btnSendFeedbackEmail = findViewById(R.id.btn_send_feedback_email)
        btnFeedbackBack = findViewById(R.id.btn_feedback_back)
        btnReportCrash = findViewById(R.id.btn_report_crash)

        headerDnd = findViewById(R.id.header_dnd)
        headerTimer = findViewById(R.id.header_timer)
        headerAlarm = findViewById(R.id.header_alarm)
        headerHealthConnect = findViewById(R.id.header_health_connect)
        headerBackup = findViewById(R.id.header_backup)
        headerAbout = findViewById(R.id.header_about)

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
        btnExport = findViewById(R.id.btn_export)
        btnImport = findViewById(R.id.btn_import)
        btnVersion = findViewById(R.id.btn_version)
        btnFeedback = findViewById(R.id.btn_feedback)
        btnLinks = findViewById(R.id.btn_links)
        eventScrollView = findViewById(R.id.event_scroll_view)
        eventLogText = findViewById(R.id.event_log_text)
    }

    private fun importSettings(jsonStr: String?) {
        SettingsImportExportHandler.importSettings(this, preferenceManager, jsonStr)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        EventLogger.log(this, EventLogger.LEVEL_LOW, "MainActivity new intent")
        MainActivityHeaderAndLogsHandler.startTimerService(this)
    }

    override fun onResume() {
        super.onResume()
        FeedbackOverlayController.updateReportCrashRowVisibility(this)
        EventLogger.setListener(this)
        MainActivityHeaderAndLogsHandler.startTimerService(this)
        MainActivityPermissionAndSettingsHandler.checkHealthConnectOnResume(this)
        MainActivityUiStateUpdater.registerPreferenceListeners(this)
    }

    override fun onPause() {
        super.onPause()
        EventLogger.setListener(null)
        MainActivityUiStateUpdater.unregisterPreferenceListeners(this)
    }

    override fun onStop() {
        super.onStop()
        MainActivityUiStateUpdater.unregisterPreferenceListeners(this)
    }

    override fun onDestroy() {
        preferenceManager?.shutdown()
        preferenceManager = null
        super.onDestroy()
    }

    override fun onEventLogged(event: String) {
        if (logsOverlayContainer?.visibility == View.VISIBLE) {
            eventLogText?.let {
                it.append(EventLogger.formatColoredEvent(this, event))
                it.append("\n")
                MainActivityHeaderAndLogsHandler.scrollToBottom(this)
            }
        }
    }
}

internal fun MainActivity.showFeedbackDialog(crashReport: String? = null) {
    FeedbackOverlayController.showFeedbackOverlay(this, crashReport)
}

internal fun MainActivity.showFeedbackOverlay(crashReport: String? = null) {
    FeedbackOverlayController.showFeedbackOverlay(this, crashReport)
}

internal fun MainActivity.sendFeedbackEmail(crashReport: String? = null, includeLogs: Boolean = true) {
    val isCrash = !crashReport.isNullOrEmpty()
    val subject = if (isCrash) {
        "Auto Sleep Droid Crash Report (v${BuildConfig.VERSION_NAME})"
    } else {
        "Auto Sleep Droid Feedback (v${BuildConfig.VERSION_NAME})"
    }

    val body = FeedbackEmailHelper.buildFeedbackPayload(this, "", crashReport, includeLogs)
    FeedbackEmailHelper.sendFeedbackEmailWithText(this, subject, body)
}

internal fun MainActivity.buildFeedbackPayload(
    context: Context,
    userMessage: String,
    crashReport: String?,
    includeLogs: Boolean
): String {
    return FeedbackEmailHelper.buildFeedbackPayload(context, userMessage, crashReport, includeLogs)
}

internal fun MainActivity.showDurationDialog(
    titleResId: Int,
    prefKey: String,
    defaultMinutes: Int,
    bounds: MainActivity.DurationPickerBounds = MainActivity.DurationPickerBounds(),
    listener: MainActivity.OnDurationSavedListener? = null
) {
    val spec = DurationDialogSpec(titleResId, prefKey, defaultMinutes, bounds)
    MainActivityDialogs.showDurationDialog(this, spec, listener)
}

internal fun MainActivity.openSettingsWithFallback(primaryAction: String, fallbackAction: String) {
    MainActivityPermissionAndSettingsHandler.openSettingsWithFallback(this, primaryAction, fallbackAction)
}

internal fun MainActivity.requestExactAlarmPermissionIfNeeded() {
    MainActivityPermissionAndSettingsHandler.requestExactAlarmPermissionIfNeeded(this)
}
