package com.bas080.autosleepdroid

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

class MainService : Service(), SensorEventListener {

    enum class State {
        OFF,
        WAITING,
        ACTIVE,
        FADING
    }

    private val handler = Handler(Looper.getMainLooper())
    private var audioManager: AudioManager? = null
    private var alarmManager: AlarmManager? = null
    private var preferences: SharedPreferences? = null
    private var expiryRunnable: Runnable? = null
    private var fadeRunnable: Runnable? = null
    private var restoreVolumeRunnable: Runnable? = null
    private var audioPlaybackCallback: AudioManager.AudioPlaybackCallback? = null
    private var volumeReceiver: android.content.BroadcastReceiver? = null
    private var dndReceiver: android.content.BroadcastReceiver? = null

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var lastOrientation = ORIENTATION_UNKNOWN
    private var lastSensorEventTimeMs = 0L
    private var sensorListenerRegistered = false
    private var sensorThread: HandlerThread? = null
    private var sensorHandler: Handler? = null
    private var vibrator: Vibrator? = null
    private var lastScheduledWakeupAlarmTimeMs = 0L
    private var currentAlarmRingtone: Ringtone? = null
    private var alarmCrescendoRunnable: Runnable? = null
    private var alarmCrescendoStartTimeMs = 0L
    private var isWakeUpAlarmRinging = false
    private var isWakeUpAlarmSnoozed = false
    private var isForeground = false
    private var napAlarmEndsAt = 0L
    private var lastTimerEndsAt = 0L
    private var isNapAlarmRinging = false
    private var isNapDndChanging = false

    var state = State.OFF
        private set

    var configuredDurationMinutes = AppDefaults.DURATION_MINUTES
        private set

    var timerEndsAt = 0L
        private set

    private var volumeBeforeFade = 0
    private var fadeStep = 0
    private var lastFadeVolume = 0
    var lastObservedVolume = 0
        private set
    private var lastObservedMediaActive = false
    private var suppressVolumeReset = false

    private var preferenceManager: PreferenceManager? = null

    val isEnabled: Boolean
        get() = state != State.OFF

    val isActive: Boolean
        get() = state == State.ACTIVE || state == State.FADING

    val isFading: Boolean
        get() = state == State.FADING

    private fun transitionTo(newState: State) {
        this.state = newState
        onStateChanged(newState)
    }

    fun initializeTimerState(
        savedEnabled: Boolean,
        savedDurationMinutes: Int,
        savedEndsAt: Long,
        initialVolume: Int,
        musicActive: Boolean,
        now: Long
    ) {
        this.configuredDurationMinutes = if (isValidDuration(savedDurationMinutes)) savedDurationMinutes else AppDefaults.DURATION_MINUTES
        this.lastObservedVolume = initialVolume
        this.lastObservedMediaActive = false

        if (savedEnabled && savedEndsAt > now) {
            startTimer(configuredDurationMinutes, savedEndsAt, now, false)
        } else if (savedEnabled && savedEndsAt > 0L && savedEndsAt <= now) {
            beginFadeOut(initialVolume)
        } else if (savedEnabled && musicActive) {
            startTimer(configuredDurationMinutes, now + configuredDurationMinutes * 60_000L, now, true)
        } else if (savedEnabled) {
            transitionTo(State.WAITING)
        } else {
            transitionTo(State.OFF)
        }
    }

    fun reloadTimerSettings(savedEnabled: Boolean, savedDurationMinutes: Int, musicActive: Boolean, now: Long) {
        val newDuration = if (isValidDuration(savedDurationMinutes)) savedDurationMinutes else AppDefaults.DURATION_MINUTES

        if (!savedEnabled) {
            configuredDurationMinutes = newDuration
            if (state != State.OFF) {
                handleTurnOff(false)
            }
            return
        }

        if (state == State.OFF) {
            configuredDurationMinutes = newDuration
            if (musicActive) {
                startTimer(configuredDurationMinutes, now + configuredDurationMinutes * 60_000L, now, true)
            } else {
                onPersistState(true, configuredDurationMinutes, 0L)
                transitionTo(State.WAITING)
            }
        } else if (state == State.WAITING) {
            configuredDurationMinutes = newDuration
            if (musicActive) {
                startTimer(configuredDurationMinutes, now + configuredDurationMinutes * 60_000L, now, true)
            } else {
                updateNotification()
            }
        } else if (state == State.ACTIVE) {
            if (newDuration != configuredDurationMinutes) {
                startTimer(newDuration, now + newDuration * 60_000L, now, true)
            }
        } else if (state == State.FADING) {
            configuredDurationMinutes = newDuration
        }
    }

    fun handleTurnOff(triggerVibration: Boolean) {
        if (triggerVibration) {
            onTriggerVibration()
        }
        timerEndsAt = 0L
        onCancelAlarm()
        onPersistState(false, configuredDurationMinutes, 0L)
        transitionTo(State.OFF)
    }

    fun handleTurnOn(musicActive: Boolean, now: Long, triggerVibration: Boolean) {
        if (triggerVibration) {
            onTriggerVibration()
        }
        onPersistState(true, configuredDurationMinutes, timerEndsAt)
        if (musicActive) {
            startTimer(configuredDurationMinutes, now + configuredDurationMinutes * 60_000L, now, true)
        } else {
            transitionTo(State.WAITING)
        }
    }

    fun handleDurationReplyState(duration: Int, musicActive: Boolean, now: Long, triggerVibration: Boolean) {
        if (triggerVibration) {
            onTriggerVibration()
        }

        if (isValidDuration(duration)) {
            configuredDurationMinutes = duration
        } else if (!isValidDuration(configuredDurationMinutes)) {
            configuredDurationMinutes = AppDefaults.DURATION_MINUTES
        }

        if (musicActive) {
            startTimer(configuredDurationMinutes, now + configuredDurationMinutes * 60_000L, now, true)
        } else {
            onPersistState(true, configuredDurationMinutes, 0L)
            transitionTo(State.WAITING)
        }
    }

    fun startTimer(durationMinutes: Int, endsAt: Long, now: Long, persist: Boolean) {
        onCancelAlarm()
        val wasActive = state == State.ACTIVE
        configuredDurationMinutes = if (isValidDuration(durationMinutes)) durationMinutes else AppDefaults.DURATION_MINUTES
        timerEndsAt = endsAt
        if (persist) {
            onPersistState(true, configuredDurationMinutes, timerEndsAt)
        }
        onScheduleAlarm(timerEndsAt)
        if (wasActive) {
            onTimerRescheduled()
            updateNotification()
        } else {
            transitionTo(State.ACTIVE)
        }
    }

    @JvmOverloads
    fun handleAlarmExpiryState(currentVolume: Int, now: Long = System.currentTimeMillis()) {
        if (isEnabled && state == State.ACTIVE) {
            if (timerEndsAt > 0L && now < timerEndsAt - 1000L) {
                return
            }
            beginFadeOut(currentVolume)
        }
    }

    fun beginFadeOut(currentVolume: Int) {
        if (!isEnabled || state == State.FADING) {
            return
        }
        volumeBeforeFade = currentVolume
        lastFadeVolume = currentVolume
        lastObservedVolume = currentVolume
        fadeStep = 0
        transitionTo(State.FADING)
    }

    fun runFadeStep(currentVolume: Int, flipDetected: Boolean): Boolean {
        if (state != State.FADING) {
            return false
        }

        if (flipDetected) {
            cancelFadeForFlip()
            return false
        }

        if (currentVolume != lastFadeVolume) {
            cancelFadeForVolumeChange(currentVolume)
            return false
        }

        fadeStep++
        val targetVolume = 0
        val progress = fadeStep.toFloat() / AppDefaults.TOTAL_FADE_STEPS
        val fraction = 1.0f - (1.0f - progress) * (1.0f - progress)
        val nextVolume = Math.round(volumeBeforeFade - (volumeBeforeFade - targetVolume) * fraction)

        lastFadeVolume = nextVolume
        lastObservedVolume = nextVolume

        suppressVolumeReset = true
        onSetStreamVolume(nextVolume)
        suppressVolumeReset = false

        if (fadeStep >= AppDefaults.TOTAL_FADE_STEPS) {
            finishExpiry()
            return false
        }
        return true
    }

    fun finishExpiry() {
        onPauseMedia()
    }

    fun restoreVolumeAfterPause() {
        suppressVolumeReset = true
        onSetStreamVolume(volumeBeforeFade)
        suppressVolumeReset = false
        EventLogger.log("Restored pre-fade volume to $volumeBeforeFade")
        lastObservedVolume = volumeBeforeFade
        onPersistState(true, configuredDurationMinutes, 0L)
        transitionTo(State.WAITING)
    }

    fun cancelFadeForVolumeChange(currentVolume: Int) {
        cancelFadeForFlip()
    }

    fun cancelFadeForFlip() {
        onTriggerVibration()
        onCancelAlarm()
        suppressVolumeReset = true
        onSetStreamVolume(volumeBeforeFade)
        suppressVolumeReset = false
        EventLogger.log("Restored pre-fade volume to $volumeBeforeFade")
        lastObservedVolume = volumeBeforeFade
        if (isValidDuration(configuredDurationMinutes)) {
            startTimer(configuredDurationMinutes, System.currentTimeMillis() + configuredDurationMinutes * 60_000L, System.currentTimeMillis(), true)
        } else {
            transitionTo(State.WAITING)
        }
    }

    fun onPlaybackStateChanged(musicActive: Boolean, now: Long) {
        val playbackStarted = musicActive && !lastObservedMediaActive
        val playbackStopped = !musicActive && lastObservedMediaActive
        lastObservedMediaActive = musicActive

        if (playbackStarted) {
            EventLogger.log("Music playback started")
        } else if (playbackStopped) {
            EventLogger.log("Music playback stopped")
        }

        if (isEnabled) {
            if (state == State.WAITING && musicActive) {
                startTimer(configuredDurationMinutes, now + configuredDurationMinutes * 60_000L, now, true)
            }
        }
    }

    fun onVolumeChanged(currentVolume: Int, now: Long) {
        if (suppressVolumeReset || !isActive) {
            lastObservedVolume = currentVolume
            return
        }

        val expectedVolume = if (state == State.FADING) lastFadeVolume else lastObservedVolume
        val volumeChanged = currentVolume != expectedVolume
        lastObservedVolume = currentVolume

        if (volumeChanged) {
            EventLogger.log("Volume changed to $currentVolume")
            if (state == State.FADING) {
                cancelFadeForVolumeChange(currentVolume)
            } else if (state == State.ACTIVE) {
                resetTimerForVolumeChange(now)
            }
        }
    }

    fun onPhoneFlipped(now: Long) {
        if (!isActive) {
            return
        }

        EventLogger.log("Phone flip gesture detected")

        if (state == State.FADING) {
            cancelFadeForFlip()
        } else if (state == State.ACTIVE) {
            resetTimerForVolumeChange(now)
        }
    }

    private fun resetTimerForVolumeChange(now: Long) {
        if (state != State.FADING && isValidDuration(configuredDurationMinutes)) {
            onTriggerVibration()
            startTimer(configuredDurationMinutes, now + configuredDurationMinutes * 60_000L, now, true)
        }
    }

    override fun onCreate() {
        super.onCreate()
        EventLogger.log(this, EventLogger.LEVEL_LOW, "MainService created")
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager?
        alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager?
        preferenceManager = PreferenceManager(getSharedPreferences(PREFERENCES, MODE_PRIVATE))
        preferences = preferenceManager?.sharedPreferences
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator?
        createNotificationChannel()

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager?
        if (sensorManager != null) {
            accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        }

        setupPreferenceListeners()
        initializeStateAndNotification()
    }

    private fun setupPreferenceListeners() {
        preferenceManager?.watchEffect { getter ->
            val enabled = getter.getBoolean(PreferenceKeys.KEY_ACTIVE, true)
            val duration = getter.getInt(PreferenceKeys.KEY_DURATION_MINUTES, AppDefaults.DURATION_MINUTES)
            onTimerConfigChanged(enabled, duration)
        }

        preferenceManager?.watchEffect { getter ->
            val goalEnabled = getter.getBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, false)
            getter.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR, AppDefaults.WAKE_UP_GOAL_HOUR)
            getter.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE, AppDefaults.WAKE_UP_GOAL_MINUTE)
            getter.getInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, AppDefaults.WAKE_UP_GOAL_HOUR)
            getter.getInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, AppDefaults.WAKE_UP_GOAL_MINUTE)
            getter.getInt(PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES, AppDefaults.MIN_SLEEP_DURATION_MINUTES)
            onWakeGoalConfigChanged(goalEnabled)
        }

        preferenceManager?.watchEffect { getter ->
            getter.getBoolean(PreferenceKeys.KEY_NAP_DND_ENABLED, false)
            getter.getLong(PreferenceKeys.KEY_NAP_ALARM_ENDS_AT, 0L)
            updateNotification()
        }

        preferenceManager?.watchEffect { getter ->
            if (getter.getBoolean(PreferenceKeys.KEY_AUTO_TIMER_ENABLED, false)) {
                checkAndApplyDndAutoTimer()
            }
        }

        preferenceManager?.watchEffect { getter ->
            getter.getBoolean(PreferenceKeys.KEY_HEALTH_CONNECT_ENABLED, false)
            getter.getInt(PreferenceKeys.KEY_HC_MIN_DURATION_MINUTES, 15)
            updateNotification()
        }
    }

    private fun onTimerConfigChanged(enabled: Boolean, durationMinutes: Int) {
        val musicActive = audioManager != null && audioManager!!.isMusicActive
        reloadTimerSettings(enabled, durationMinutes, musicActive, System.currentTimeMillis())
        if (isWakeAlarmEnabled()) {
            checkAndScheduleSmartWakeUpAlarm(timerEndsAt)
        }
        updateNotification()
    }

    private fun onWakeGoalConfigChanged(goalEnabled: Boolean) {
        if (goalEnabled) {
            checkAndScheduleSmartWakeUpAlarm(timerEndsAt)
        } else {
            dismissAutoSleepAlarm()
        }
        updateNotification()
    }

    private fun initializeStateAndNotification() {
        val savedEnabled = preferences?.getBoolean(KEY_ENABLED, true) ?: true
        val savedDuration = preferences?.getInt(KEY_DURATION_MINUTES, AppDefaults.DURATION_MINUTES) ?: AppDefaults.DURATION_MINUTES
        val savedEndsAt = preferences?.getLong(KEY_TIMER_ENDS_AT, 0L) ?: 0L
        napAlarmEndsAt = preferences?.getLong(KEY_NAP_ALARM_ENDS_AT, 0L) ?: 0L
        isNapAlarmRinging = preferences?.getBoolean(KEY_NAP_ALARM_RINGING, false) ?: false
        val currentVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        val musicActive = audioManager != null && audioManager!!.isMusicActive

        EventLogger.log(this, "MainService state initialized (enabled: $savedEnabled, duration: ${savedDuration}m)")

        initializeTimerState(savedEnabled, savedDuration, savedEndsAt, currentVolume, musicActive, System.currentTimeMillis())

        registerDndReceiver()
        checkAndApplyDndAutoTimer()

        showOrHideNotification()

        val goalEnabled = isWakeAlarmEnabled()
        if (goalEnabled) {
            checkAndScheduleSmartWakeUpAlarm(savedEndsAt)
        }
    }

    private fun registerAudioPlaybackCallback() {
        if (audioManager != null && Build.VERSION.SDK_INT >= 26) {
            audioPlaybackCallback = object : AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: MutableList<android.media.AudioPlaybackConfiguration>?) {
                    super.onPlaybackConfigChanged(configs)
                    val musicActive = audioManager?.isMusicActive == true
                    onPlaybackStateChanged(musicActive, System.currentTimeMillis())
                }
            }
            audioManager?.registerAudioPlaybackCallback(audioPlaybackCallback!!, handler)
        }
    }

    private fun unregisterAudioPlaybackCallback() {
        if (audioManager != null && audioPlaybackCallback != null && Build.VERSION.SDK_INT >= 26) {
            audioManager?.unregisterAudioPlaybackCallback(audioPlaybackCallback!!)
            audioPlaybackCallback = null
        }
    }

    private fun registerDndReceiver() {
        if (dndReceiver == null && Build.VERSION.SDK_INT >= 23) {
            dndReceiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED == intent?.action) {
                        EventLogger.log(context, EventLogger.LEVEL_HIGH, "DND state changed")
                        checkAndApplyDndAutoTimer()
                    }
                }
            }
            val filter = android.content.IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            registerReceiver(dndReceiver, filter)
        }
    }

    private fun unregisterDndReceiver() {
        dndReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (ignored: IllegalArgumentException) {
            }
            dndReceiver = null
        }
    }

    private fun checkAndApplyDndAutoTimer() {
        if (isNapDndChanging || preferenceManager == null) return
        val autoTimerEnabled = true == preferenceManager?.getComputed(PreferenceComputations.IS_AUTO_TIMER_ENABLED)
        if (!autoTimerEnabled) return

        if (Build.VERSION.SDK_INT >= 23) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager?
            if (nm != null) {
                val filter = nm.currentInterruptionFilter
                val dndActive = filter != NotificationManager.INTERRUPTION_FILTER_ALL
                val musicActive = audioManager != null && audioManager!!.isMusicActive
                val now = System.currentTimeMillis()

                if (dndActive && !isEnabled) {
                    EventLogger.log(this, EventLogger.LEVEL_HIGH, "DND active: turning ON sleep timer")
                    handleTurnOn(musicActive, now, true)
                    updateNotification()
                } else if (!dndActive && isEnabled) {
                    EventLogger.log(this, EventLogger.LEVEL_HIGH, "DND inactive: turning OFF sleep timer")
                    handleTurnOff(true)
                    updateNotification()
                }
            }
        }
    }

    private fun registerVolumeObserver() {
        if (volumeReceiver == null) {
            volumeReceiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if ("android.media.VOLUME_CHANGED_ACTION" == intent?.action) {
                        if (isWakeUpAlarmRinging || isWakeUpAlarmSnoozed) {
                            dismissWakeUpAlarmViaVolumeKey()
                        } else {
                            val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                            if (streamType == AudioManager.STREAM_MUSIC || streamType == -1) {
                                audioManager?.let {
                                    val currentVol = it.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    onVolumeChanged(currentVol, System.currentTimeMillis())
                                }
                            }
                        }
                    }
                }
            }
            val filter = android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION")
            registerReceiver(volumeReceiver, filter)
        }
    }

    private fun unregisterVolumeObserver() {
        volumeReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (ignored: IllegalArgumentException) {
            }
            volumeReceiver = null
        }
    }

    private fun registerSensorListener() {
        if (sensorManager != null && accelerometer != null && !sensorListenerRegistered) {
            if (sensorThread == null) {
                sensorThread = HandlerThread("SensorThread").apply { start() }
                sensorHandler = Handler(sensorThread!!.looper)
            }
            sensorListenerRegistered = true
            sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL, sensorHandler)
        }
    }

    private fun unregisterSensorListener() {
        if (sensorListenerRegistered && sensorManager != null) {
            sensorListenerRegistered = false
            sensorManager?.unregisterListener(this)
        }
        sensorThread?.let {
            it.quitSafely()
            sensorThread = null
            sensorHandler = null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val action = intent.action
            if (ACTION_TURN_OFF == action) {
                EventLogger.log(this, EventLogger.LEVEL_HIGH, "Timer turned off")
                handleTurnOff(true)
                Toast.makeText(this, R.string.toast_timer_turned_off, Toast.LENGTH_SHORT).show()
            } else if (ACTION_TURN_ON == action) {
                EventLogger.log(this, EventLogger.LEVEL_HIGH, "Timer turned on")
                val musicActive = audioManager != null && audioManager!!.isMusicActive
                handleTurnOn(musicActive, System.currentTimeMillis(), true)
                Toast.makeText(this, R.string.toast_timer_turned_on, Toast.LENGTH_SHORT).show()
            } else if (ACTION_SET_DURATION == action) {
                handleDurationReply(intent)
            } else if (ACTION_ALARM_EXPIRY == action) {
                EventLogger.log(this, EventLogger.LEVEL_HIGH, "AlarmManager trigger received")
                val currentVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                handleAlarmExpiryState(currentVol)
            } else if (ACTION_WAKEUP_ALARM_EXPIRY == action) {
                EventLogger.log(this, EventLogger.LEVEL_HIGH, "Auto Sleep wake-up alarm triggered")
                if (!isNapAlarmRinging) {
                    updateNextWakeUpTimeOnDismissOrExpiry()
                }
                if (isWakeAlarmEnabled()) {
                    isWakeUpAlarmRinging = true
                    isWakeUpAlarmSnoozed = false
                    updateListenersRegistration()
                    playWakeUpAlarmSound()
                } else {
                    EventLogger.log(this, "Wake alarm disabled; skipping alarm tone")
                }
                updateNotification()
                checkAndScheduleSmartWakeUpAlarm(timerEndsAt)
            } else if (ACTION_DISMISS_WAKEUP_ALARM == action) {
                EventLogger.log(this, EventLogger.LEVEL_HIGH, "Wake-Up Goal alarm dismissed")
                processSleepSessionOnAlarmDismissal()
                if (!isNapAlarmRinging) {
                    updateNextWakeUpTimeOnDismissOrExpiry()
                }
                stopWakeUpAlarmSound()
                cancelSnoozeAlarm()
                cancelNapAlarm(false)
                setNapAlarmRinging(false)
                isWakeUpAlarmRinging = false
                isWakeUpAlarmSnoozed = false
                updateListenersRegistration()
                checkAndScheduleSmartWakeUpAlarm(timerEndsAt)
                updateNotification()
                Toast.makeText(this, R.string.toast_alarm_dismissed, Toast.LENGTH_SHORT).show()
            } else if (ACTION_SNOOZE_WAKEUP_ALARM == action) {
                EventLogger.log(this, EventLogger.LEVEL_HIGH, "Wake-Up Goal alarm snoozed for 9m")
                stopWakeUpAlarmSound()
                snoozeWakeUpAlarm()
                isWakeUpAlarmRinging = false
                isWakeUpAlarmSnoozed = true
                updateListenersRegistration()
                updateNotification()
                Toast.makeText(this, R.string.toast_alarm_snoozed, Toast.LENGTH_SHORT).show()
            } else if (ACTION_AWAKE == action) {
                handleAwakeAction()
            } else if (ACTION_CLEAR_GOAL == action) {
                preferences?.edit()
                    ?.putBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, false)
                    ?.remove(KEY_WAKEUP_LAST_SCHEDULED_MS)
                    ?.apply()
                cancelSnoozeAlarm()
                dismissAutoSleepAlarm()
                EventLogger.log(this, EventLogger.LEVEL_HIGH, "Smart Wake-Up Goal cleared")
                Toast.makeText(this, R.string.toast_goal_stopped, Toast.LENGTH_SHORT).show()
                updateNotification()
            } else if (ACTION_START_NAP == action) {
                val duration = intent.getIntExtra(EXTRA_NAP_DURATION_MINUTES, preferences?.getInt(KEY_NAP_DURATION_MINUTES, 20) ?: 20)
                startNapAlarm(duration)
            } else if (ACTION_CANCEL_NAP == action) {
                cancelNapAlarm(true)
            } else if (ACTION_NAP_EXPIRY == action) {
                EventLogger.log(this, EventLogger.LEVEL_HIGH, "Nap alarm triggered")
                cancelNapAlarm(false)
                setNapAlarmRinging(true)
                isWakeUpAlarmRinging = true
                isWakeUpAlarmSnoozed = false
                updateListenersRegistration()
                playWakeUpAlarmSound()
                updateNotification()
            } else if (ACTION_REDRAW_NOTIFICATION == action) {
                reloadSettingsAndUpdate()
            }
        }
        return START_STICKY
    }

    private fun handleDurationReply(intent: Intent) {
        val reply = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(REMOTE_INPUT_KEY)
        val duration = parseDurationMinutes(reply?.toString())

        if (duration > 0) {
            val musicActive = audioManager != null && audioManager!!.isMusicActive
            handleDurationReplyState(duration, musicActive, System.currentTimeMillis(), true)
            EventLogger.log(this, EventLogger.LEVEL_HIGH, "Duration set to ${configuredDurationMinutes}m (input: '$reply')")
            val formattedStr = formatDurationString(configuredDurationMinutes)
            Toast.makeText(this, getString(R.string.toast_duration_set, formattedStr), Toast.LENGTH_SHORT).show()
        } else {
            EventLogger.log(this, EventLogger.LEVEL_HIGH, "Invalid duration input: '$reply'")
            Toast.makeText(this, R.string.toast_duration_invalid, Toast.LENGTH_SHORT).show()
        }
    }

    private fun startFadeRunnable() {
        EventLogger.log(this, EventLogger.LEVEL_HIGH, "Fade-out started")
        fadeRunnable = Runnable { runFadeStep() }
        handler.post(fadeRunnable!!)
    }

    private fun runFadeStep() {
        if (audioManager == null) {
            finishExpiry()
            return
        }

        val currentVolume = audioManager!!.getStreamVolume(AudioManager.STREAM_MUSIC)

        val continues = runFadeStep(currentVolume, false)
        if (continues) {
            fadeRunnable?.let { handler.postDelayed(it, AppDefaults.FADE_STEP_INTERVAL_MS) }
        }
    }

    private fun scheduleExpiry() {
        expiryRunnable?.let { handler.removeCallbacks(it) }
        val delay = Math.max(0L, timerEndsAt - System.currentTimeMillis())
        expiryRunnable = Runnable {
            val vol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
            beginFadeOut(vol)
        }
        handler.postDelayed(expiryRunnable!!, delay)
    }

    private fun cancelTimerCallbacks() {
        expiryRunnable?.let { handler.removeCallbacks(it) }
        fadeRunnable?.let { handler.removeCallbacks(it) }
        restoreVolumeRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun updateListenersRegistration() {
        val needSensor = isActive || isWakeUpAlarmRinging
        if (needSensor) {
            registerSensorListener()
        } else {
            unregisterSensorListener()
        }

        val needVolume = isActive || isWakeUpAlarmRinging || isWakeUpAlarmSnoozed
        if (needVolume) {
            registerVolumeObserver()
        } else {
            unregisterVolumeObserver()
        }
    }

    fun onStateChanged(newState: State) {
        cancelTimerCallbacks()
        if (newState == State.OFF) {
            unregisterAudioPlaybackCallback()
            stopWakeUpAlarmSound()
            cancelSnoozeAlarm()
            isWakeUpAlarmRinging = false
            isWakeUpAlarmSnoozed = false
            onCancelAlarm()
            updateListenersRegistration()
            showOrHideNotification()
        } else if (newState == State.WAITING) {
            registerAudioPlaybackCallback()
            onCancelAlarm()
            updateListenersRegistration()
            showOrHideNotification()
        } else if (newState == State.FADING) {
            unregisterAudioPlaybackCallback()
            updateListenersRegistration()
            showOrHideNotification()
            startFadeRunnable()
        } else if (newState == State.ACTIVE) {
            preferences?.edit()?.putLong(PreferenceKeys.KEY_TIMER_START_TIME_MS, System.currentTimeMillis())?.apply()
            unregisterAudioPlaybackCallback()
            updateListenersRegistration()
            checkAndScheduleSmartWakeUpAlarm(timerEndsAt)
            showOrHideNotification()
            scheduleExpiry()
        }
    }

    fun onSetStreamVolume(volume: Int) {
        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
    }

    fun onScheduleAlarm(triggerAtMillis: Long) {
        val am = alarmManager ?: return
        val intent = Intent(this, MainService::class.java).setAction(ACTION_ALARM_EXPIRY)
        val pendingIntent = PendingIntent.getService(
            this, 100, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= 31) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    EventLogger.log(this, "Exact alarm permission missing, using fallback alarm")
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            EventLogger.log(this, "SecurityException scheduling alarm, using fallback alarm")
        }
    }

    fun onCancelAlarm() {
        val am = alarmManager ?: return
        val intent = Intent(this, MainService::class.java).setAction(ACTION_ALARM_EXPIRY)
        val pendingIntent = PendingIntent.getService(
            this, 100, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            am.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun onPauseMedia() {
        EventLogger.log(this, EventLogger.LEVEL_HIGH, "Timer expired: pausing media")
        val now = System.currentTimeMillis()
        preferences?.edit()?.putLong(PreferenceKeys.KEY_SLEEP_START_TIME_MS, now)?.apply()
        pauseMediaViaAudioFocus()

        restoreVolumeRunnable = Runnable { restoreVolumeAfterPause() }
        handler.postDelayed(restoreVolumeRunnable!!, PAUSE_RESET_DELAY_MS)
    }

    private fun processSleepSession() {
        val prefs = preferences ?: return
        val healthConnectEnabled = true == preferenceManager?.getComputed(PreferenceComputations.IS_HEALTH_CONNECT_ENABLED)
        val hcMinDurationMinutes = prefs.getInt(PreferenceKeys.KEY_HC_MIN_DURATION_MINUTES, AppDefaults.HC_MIN_DURATION_MINUTES)

        val sleepStartTime = prefs.getLong(PreferenceKeys.KEY_SLEEP_START_TIME_MS, 0L)
        val timerStartTime = prefs.getLong(PreferenceKeys.KEY_TIMER_START_TIME_MS, 0L)
        val napStartTime = prefs.getLong(PreferenceKeys.KEY_NAP_START_TIME_MS, 0L)
        val wakeTime = System.currentTimeMillis()

        if (napStartTime > 0L && wakeTime > napStartTime) {
            val durationMinutes = (wakeTime - napStartTime) / 60_000L
            if (healthConnectEnabled && (wakeTime - napStartTime < 14 * 3600_000L) && durationMinutes >= hcMinDurationMinutes) {
                HealthConnectManager.writeSleepSession(this, napStartTime, wakeTime, null)
            }
            prefs.edit().remove(PreferenceKeys.KEY_NAP_START_TIME_MS).apply()
        } else {
            var startTime = 0L
            if (timerStartTime > 0L && wakeTime > timerStartTime && (wakeTime - timerStartTime < 14 * 3600_000L)) {
                startTime = timerStartTime
            } else if (sleepStartTime > 0L && wakeTime > sleepStartTime && (wakeTime - sleepStartTime < 14 * 3600_000L)) {
                startTime = sleepStartTime
            } else if (isWakeAlarmEnabled()) {
                val minSleepMin = prefs.getInt(PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES, AppDefaults.MIN_SLEEP_DURATION_MINUTES)
                startTime = wakeTime - (minSleepMin * 60_000L)
            }

            if (startTime > 0L && wakeTime > startTime) {
                val durationMinutes = (wakeTime - startTime) / 60_000L
                if (healthConnectEnabled && durationMinutes >= hcMinDurationMinutes && (wakeTime - startTime < 14 * 3600_000L)) {
                    HealthConnectManager.writeSleepSession(this, startTime, wakeTime, null)
                }
            }
            prefs.edit()
                .remove(PreferenceKeys.KEY_SLEEP_START_TIME_MS)
                .remove(PreferenceKeys.KEY_TIMER_START_TIME_MS)
                .apply()
        }
    }

    private fun processSleepSessionOnAlarmDismissal() {
        processSleepSession()
    }

    private fun handleAwakeAction() {
        EventLogger.log(this, EventLogger.LEVEL_HIGH, "User marked as awake explicitly")

        val wasNap = isNapAlarmRinging || isNapActive()

        if (wasNap) {
            processSleepSession()
            stopWakeUpAlarmSound()
            cancelSnoozeAlarm()
            cancelNapAlarm(false)
            setNapAlarmRinging(false)
            isWakeUpAlarmRinging = false
            isWakeUpAlarmSnoozed = false
            updateListenersRegistration()
            updateNotification()
            Toast.makeText(this, R.string.toast_awake_registered, Toast.LENGTH_SHORT).show()
            return
        }

        if (isActive) {
            preferences?.edit()
                ?.remove(PreferenceKeys.KEY_SLEEP_START_TIME_MS)
                ?.remove(PreferenceKeys.KEY_TIMER_START_TIME_MS)
                ?.apply()
            updateNotification()
            Toast.makeText(this, R.string.toast_awake_registered, Toast.LENGTH_SHORT).show()
            return
        }

        processSleepSession()

        stopWakeUpAlarmSound()
        cancelSnoozeAlarm()
        cancelNapAlarm(false)
        setNapAlarmRinging(false)
        isWakeUpAlarmRinging = false
        isWakeUpAlarmSnoozed = false

        dismissAutoSleepAlarm()
        updateNextWakeUpTimeOnDismissOrExpiry()
        checkAndScheduleSmartWakeUpAlarm(timerEndsAt)

        updateListenersRegistration()
        updateNotification()
        Toast.makeText(this, R.string.toast_awake_registered, Toast.LENGTH_SHORT).show()
    }

    private fun isWakeAlarmEnabled(): Boolean {
        val pm = preferenceManager ?: return false
        return true == pm.getComputed(PreferenceComputations.IS_WAKE_ALARM_ENABLED)
    }

    fun shouldShowAwakeAction(): Boolean {
        if (isNapActive() || isWakeUpAlarmRinging || isWakeUpAlarmSnoozed) {
            return true
        }
        val pm = preferenceManager ?: return false
        return true == pm.getComputed(PreferenceComputations.SHOULD_SHOW_AWAKE_ACTION)
    }

    private fun updateNextWakeUpTimeOnDismissOrExpiry() {
        val prefs = preferences ?: return
        val goalHour = prefs.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR, AppDefaults.WAKE_UP_GOAL_HOUR)
        val goalMin = prefs.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE, AppDefaults.WAKE_UP_GOAL_MINUTE)

        prefs.edit()
            .putInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, goalHour)
            .putInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, goalMin)
            .remove(KEY_WAKEUP_LAST_SCHEDULED_MS)
            .apply()
    }

    private fun checkAndScheduleSmartWakeUpAlarm(timerEndsAt: Long) {
        if (!isWakeAlarmEnabled()) {
            dismissAutoSleepAlarm()
            return
        }

        val calAlarm = calculateScheduledAlarm(this, System.currentTimeMillis(), timerEndsAt) ?: return

        val targetAlarmTimeMs = calAlarm.timeInMillis
        val lastScheduled = preferences?.getLong(KEY_WAKEUP_LAST_SCHEDULED_MS, 0L) ?: 0L

        if (targetAlarmTimeMs == lastScheduled) {
            return
        }

        dismissAutoSleepAlarm()

        lastScheduledWakeupAlarmTimeMs = targetAlarmTimeMs
        preferences?.edit()?.putLong(KEY_WAKEUP_LAST_SCHEDULED_MS, targetAlarmTimeMs)?.apply()

        val am = alarmManager ?: return

        val intent = Intent(this, MainService::class.java).setAction(ACTION_WAKEUP_ALARM_EXPIRY)
        val pendingIntent = PendingIntent.getService(
            this, 101, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(this, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            this, 102, showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val clockInfo = AlarmManager.AlarmClockInfo(targetAlarmTimeMs, showPendingIntent)

        try {
            am.setAlarmClock(clockInfo, pendingIntent)
            val timeFormat = android.text.format.DateFormat.getTimeFormat(this)
            val formattedTime = timeFormat.format(Date(targetAlarmTimeMs))
            EventLogger.log(this, "Wake-Up Goal Alarm '$ALARM_SEARCH_NAME' scheduled for $formattedTime")
        } catch (e: Exception) {
            EventLogger.log(this, "Failed to schedule wake-up alarm: ${e.message}")
        }
    }

    private fun dismissAutoSleepAlarm() {
        val am = alarmManager
        if (am != null) {
            val alarmTriggerIntent = Intent(this, MainService::class.java).setAction(ACTION_WAKEUP_ALARM_EXPIRY)
            val operationIntent = PendingIntent.getService(
                this, 101, alarmTriggerIntent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (operationIntent != null) {
                am.cancel(operationIntent)
                operationIntent.cancel()
            }
        }

        preferences?.edit()?.remove(KEY_WAKEUP_LAST_SCHEDULED_MS)?.apply()
    }

    private fun cancelSnoozeAlarm() {
        val am = alarmManager
        if (am != null) {
            val snoozeTriggerIntent = Intent(this, MainService::class.java).setAction(ACTION_WAKEUP_ALARM_EXPIRY)
            val snoozeOperation = PendingIntent.getService(
                this, 106, snoozeTriggerIntent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (snoozeOperation != null) {
                am.cancel(snoozeOperation)
                snoozeOperation.cancel()
            }
        }
    }

    fun onTriggerVibration() {
        val v = vibrator
        if (v != null && v.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= 29) {
                v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(70L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(70L)
            }
        }
    }

    fun onPersistState(enabled: Boolean, durationMinutes: Int, timerEndsAt: Long) {
        val prefs = preferences ?: return
        val editor = prefs.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putInt(KEY_DURATION_MINUTES, durationMinutes)
        if (timerEndsAt > 0L) {
            editor.putLong(KEY_TIMER_ENDS_AT, timerEndsAt)
        } else {
            editor.remove(KEY_TIMER_ENDS_AT)
        }
        editor.apply()
    }

    fun onTimerRescheduled() {
        val now = System.currentTimeMillis()
        val prefs = preferences
        if (prefs != null) {
            val editor = prefs.edit()
            editor.putLong(PreferenceKeys.KEY_TIMER_START_TIME_MS, now)

            if (isWakeAlarmEnabled()) {
                val minSleepMin = prefs.getInt(PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES, AppDefaults.MIN_SLEEP_DURATION_MINUTES)
                val minSleepMs = minSleepMin * 60_000L
                val windowMs = (1.2 * minSleepMs).toLong()

                val goalHour = prefs.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR, AppDefaults.WAKE_UP_GOAL_HOUR)
                val goalMin = prefs.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE, AppDefaults.WAKE_UP_GOAL_MINUTE)
                val currentHour = prefs.getInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, goalHour)
                val currentMin = prefs.getInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, goalMin)

                val calCurrent = Calendar.getInstance()
                calCurrent.timeInMillis = now
                calCurrent.set(Calendar.HOUR_OF_DAY, currentHour)
                calCurrent.set(Calendar.MINUTE, currentMin)
                calCurrent.set(Calendar.SECOND, 0)
                calCurrent.set(Calendar.MILLISECOND, 0)
                if (calCurrent.timeInMillis <= now) {
                    calCurrent.add(Calendar.DAY_OF_YEAR, 1)
                }

                val currentAlarmMs = calCurrent.timeInMillis
                val existingSleepStart = prefs.getLong(PreferenceKeys.KEY_SLEEP_START_TIME_MS, 0L)
                if ((existingSleepStart == 0L || now - existingSleepStart >= 14 * 3600_000L)
                    && now >= currentAlarmMs - windowMs && now <= currentAlarmMs
                ) {
                    editor.putLong(PreferenceKeys.KEY_SLEEP_START_TIME_MS, now)
                }
            }
            editor.apply()
        }
        val newTimerEndsAt = timerEndsAt
        if (lastTimerEndsAt > 0L && newTimerEndsAt > lastTimerEndsAt && isNapActive()) {
            val deltaMs = newTimerEndsAt - lastTimerEndsAt
            napAlarmEndsAt += deltaMs
            prefs?.edit()?.putLong(KEY_NAP_ALARM_ENDS_AT, napAlarmEndsAt)?.apply()
            scheduleNapAlarm(napAlarmEndsAt)
            EventLogger.log(this, EventLogger.LEVEL_HIGH, "Pushed nap alarm forward by ${deltaMs / 60_000L}m")
        }
        lastTimerEndsAt = newTimerEndsAt
        scheduleExpiry()

        if (isWakeAlarmEnabled() && prefs != null) {
            val minSleepMin = prefs.getInt(PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES, AppDefaults.MIN_SLEEP_DURATION_MINUTES)
            val timerDuration = prefs.getInt(PreferenceKeys.KEY_DURATION_MINUTES, AppDefaults.DURATION_MINUTES)
            val minSleepMs = minSleepMin * 60_000L
            val sleepStartTime = prefs.getLong(PreferenceKeys.KEY_SLEEP_START_TIME_MS, 0L)
            val requiredWakeTime: Long
            val baseTime: Long
            if (newTimerEndsAt > 0L) {
                baseTime = newTimerEndsAt
                val effectiveMinSleepMs = Math.max(0L, (minSleepMin - timerDuration) * 60_000L)
                requiredWakeTime = newTimerEndsAt + effectiveMinSleepMs
            } else if (sleepStartTime > 0L && (now - sleepStartTime < 14 * 3600_000L)) {
                baseTime = sleepStartTime
                val effectiveMinSleepMs = Math.max(0L, (minSleepMin - timerDuration) * 60_000L)
                requiredWakeTime = sleepStartTime + effectiveMinSleepMs
            } else {
                baseTime = now
                requiredWakeTime = now + minSleepMs
            }

            val goalHour = prefs.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR, AppDefaults.WAKE_UP_GOAL_HOUR)
            val goalMin = prefs.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE, AppDefaults.WAKE_UP_GOAL_MINUTE)
            val currentHour = prefs.getInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, goalHour)
            val currentMin = prefs.getInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, goalMin)

            val calCurrent = Calendar.getInstance()
            calCurrent.timeInMillis = now
            calCurrent.set(Calendar.HOUR_OF_DAY, currentHour)
            calCurrent.set(Calendar.MINUTE, currentMin)
            calCurrent.set(Calendar.SECOND, 0)
            calCurrent.set(Calendar.MILLISECOND, 0)
            if (calCurrent.timeInMillis <= now) {
                calCurrent.add(Calendar.DAY_OF_YEAR, 1)
            }

            val calGoal = Calendar.getInstance()
            calGoal.timeInMillis = now
            calGoal.set(Calendar.HOUR_OF_DAY, goalHour)
            calGoal.set(Calendar.MINUTE, goalMin)
            calGoal.set(Calendar.SECOND, 0)
            calGoal.set(Calendar.MILLISECOND, 0)
            if (calGoal.timeInMillis <= now) {
                calGoal.add(Calendar.DAY_OF_YEAR, 1)
            }

            val currentAlarmMs = calCurrent.timeInMillis
            val goalAlarmMs = calGoal.timeInMillis
            val windowMs = (1.2 * minSleepMs).toLong()

            if (requiredWakeTime > currentAlarmMs) {
                val calRequired = Calendar.getInstance()
                calRequired.timeInMillis = requiredWakeTime
                val pushedHour = calRequired.get(Calendar.HOUR_OF_DAY)
                val pushedMin = calRequired.get(Calendar.MINUTE)
                prefs.edit()
                    .putInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, pushedHour)
                    .putInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, pushedMin)
                    .remove(KEY_WAKEUP_LAST_SCHEDULED_MS)
                    .apply()
                EventLogger.log(this, EventLogger.LEVEL_HIGH, "Pushed wake alarm forward to ${formatTime(pushedHour, pushedMin)} due to min sleep safeguard")
            } else if (currentAlarmMs - baseTime <= windowMs) {
                val earlierWakeMs = Math.max(goalAlarmMs, requiredWakeTime)
                if (earlierWakeMs < currentAlarmMs) {
                    val calEarlier = Calendar.getInstance()
                    calEarlier.timeInMillis = earlierWakeMs
                    val earlierHour = calEarlier.get(Calendar.HOUR_OF_DAY)
                    val earlierMin = calEarlier.get(Calendar.MINUTE)
                    prefs.edit()
                        .putInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, earlierHour)
                        .putInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, earlierMin)
                        .remove(KEY_WAKEUP_LAST_SCHEDULED_MS)
                        .apply()
                    EventLogger.log(this, EventLogger.LEVEL_HIGH, "Moved wake alarm earlier to ${formatTime(earlierHour, earlierMin)} (within 1.2x min sleep window)")
                }
            }
        }

        checkAndScheduleSmartWakeUpAlarm(timerEndsAt)
    }

    private fun isNapActive(): Boolean {
        val pm = preferenceManager ?: return napAlarmEndsAt > System.currentTimeMillis()
        return true == pm.getComputed(PreferenceComputations.IS_NAP_ACTIVE)
    }

    private fun setDndMode(enable: Boolean) {
        if (preferenceManager != null && true != preferenceManager?.getComputed(PreferenceComputations.IS_NAP_DND_ENABLED)) {
            return
        }
        if (Build.VERSION.SDK_INT >= 23) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager?
            if (nm != null && nm.isNotificationPolicyAccessGranted) {
                try {
                    val targetFilter = if (enable)
                        NotificationManager.INTERRUPTION_FILTER_PRIORITY
                    else
                        NotificationManager.INTERRUPTION_FILTER_ALL
                    isNapDndChanging = true
                    nm.setInterruptionFilter(targetFilter)
                    handler.postDelayed({ isNapDndChanging = false }, 1000L)
                    EventLogger.log(this, EventLogger.LEVEL_HIGH, if (enable) "DND enabled for nap" else "DND disabled after nap")
                } catch (e: Exception) {
                    isNapDndChanging = false
                    EventLogger.log(this, "Failed to set DND mode: ${e.message}")
                }
            }
        }
    }

    private fun startNapAlarm(durationMinutes: Int) {
        val now = System.currentTimeMillis()
        napAlarmEndsAt = now + durationMinutes * 60_000L
        preferences?.edit()
            ?.putInt(KEY_NAP_DURATION_MINUTES, durationMinutes)
            ?.putLong(KEY_NAP_ALARM_ENDS_AT, napAlarmEndsAt)
            ?.putLong(PreferenceKeys.KEY_NAP_START_TIME_MS, now)
            ?.apply()
        scheduleNapAlarm(napAlarmEndsAt)
        setDndMode(true)
        EventLogger.log(this, EventLogger.LEVEL_HIGH, "Nap alarm started for ${formatDurationString(durationMinutes)}")
        Toast.makeText(this, getString(R.string.toast_nap_started, formatDurationString(durationMinutes)), Toast.LENGTH_SHORT).show()
        updateNotification()
    }

    private fun scheduleNapAlarm(triggerAtMs: Long) {
        val am = alarmManager ?: return
        val intent = Intent(this, MainService::class.java).setAction(ACTION_NAP_EXPIRY)
        val pendingIntent = PendingIntent.getService(
            this, 107, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(this, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            this, 102, showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val clockInfo = AlarmManager.AlarmClockInfo(triggerAtMs, showPendingIntent)
        try {
            am.setAlarmClock(clockInfo, pendingIntent)
        } catch (e: Exception) {
            EventLogger.log(this, "Failed to schedule nap alarm: ${e.message}")
        }
    }

    private fun setNapAlarmRinging(ringing: Boolean) {
        this.isNapAlarmRinging = ringing
        if (preferences != null) {
            if (ringing) {
                preferences?.edit()?.putBoolean(KEY_NAP_ALARM_RINGING, true)?.apply()
            } else {
                preferences?.edit()?.remove(KEY_NAP_ALARM_RINGING)?.apply()
            }
        }
    }

    private fun cancelNapAlarm(showToast: Boolean) {
        val am = alarmManager
        if (am != null) {
            val intent = Intent(this, MainService::class.java).setAction(ACTION_NAP_EXPIRY)
            val pendingIntent = PendingIntent.getService(
                this, 107, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                am.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
        napAlarmEndsAt = 0L
        preferences?.edit()
            ?.remove(KEY_NAP_ALARM_ENDS_AT)
            ?.remove(PreferenceKeys.KEY_NAP_START_TIME_MS)
            ?.apply()
        setDndMode(false)
        if (showToast) {
            setNapAlarmRinging(false)
            EventLogger.log(this, EventLogger.LEVEL_HIGH, "Nap alarm cancelled")
            Toast.makeText(this, R.string.toast_nap_cancelled, Toast.LENGTH_SHORT).show()
            updateNotification()
        }
    }

    private fun ensureAudibleAlarmStreamVolume() {
        val am = audioManager ?: return
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        if (maxVol <= 0) {
            return
        }
        val currentVol = am.getStreamVolume(AudioManager.STREAM_ALARM)
        val minAudibleVol = Math.max(1, Math.round(maxVol * 0.3f))
        if (currentVol < minAudibleVol) {
            try {
                am.setStreamVolume(AudioManager.STREAM_ALARM, minAudibleVol, 0)
                EventLogger.log(this, "Adjusted STREAM_ALARM volume from $currentVol to $minAudibleVol for wake-up alarm")
            } catch (e: Exception) {
                EventLogger.log(this, "Failed to adjust STREAM_ALARM volume: ${e.message}")
            }
        }
    }

    private fun playWakeUpAlarmSound() {
        stopWakeUpAlarmSound()
        try {
            ensureAudibleAlarmStreamVolume()
            var alarmUri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
            currentAlarmRingtone = getRingtone(applicationContext, alarmUri)
            if (currentAlarmRingtone != null) {
                AlarmAudioUtils.configureAlarmAudioAttributes(currentAlarmRingtone)
                if (Build.VERSION.SDK_INT >= 28) {
                    currentAlarmRingtone?.setVolume(0.0f)
                }
                currentAlarmRingtone?.play()
                EventLogger.log(this, "Wake-Up Goal alarm tone started playing")
                startWakeUpAlarmCrescendo()
            }
        } catch (e: Exception) {
            EventLogger.log(this, "Failed to play wake-up alarm sound: ${e.message}")
        }
    }

    private fun startWakeUpAlarmCrescendo() {
        alarmCrescendoRunnable?.let { handler.removeCallbacks(it) }
        alarmCrescendoStartTimeMs = System.currentTimeMillis()
        alarmCrescendoRunnable = Runnable { runWakeUpAlarmCrescendoStep() }
        handler.post(alarmCrescendoRunnable!!)
    }

    private fun runWakeUpAlarmCrescendoStep() {
        val ringtone = currentAlarmRingtone ?: return
        val elapsedTimeMs = System.currentTimeMillis() - alarmCrescendoStartTimeMs
        val linearProgress = Math.min(1.0f, elapsedTimeMs.toFloat() / ALARM_CRESCENDO_DURATION_MS)
        val gain = linearProgress * linearProgress

        if (Build.VERSION.SDK_INT >= 28) {
            try {
                ringtone.setVolume(gain)
            } catch (ignored: Exception) {
            }
        }

        if (elapsedTimeMs < ALARM_CRESCENDO_DURATION_MS && isWakeUpAlarmRinging) {
            alarmCrescendoRunnable?.let { handler.postDelayed(it, ALARM_CRESCENDO_INTERVAL_MS) }
        } else {
            alarmCrescendoRunnable = null
        }
    }

    fun getRingtone(context: Context, uri: Uri?): Ringtone? {
        var ringtone = RingtoneManager.getRingtone(context, uri)
        if (ringtone == null) {
            try {
                val constructor = Ringtone::class.java.getDeclaredConstructor(Context::class.java, Boolean::class.javaPrimitiveType)
                constructor.isAccessible = true
                ringtone = constructor.newInstance(context, false)
            } catch (ignored: Exception) {
            }
        }
        return ringtone
    }

    private fun stopWakeUpAlarmSound() {
        alarmCrescendoRunnable?.let {
            handler.removeCallbacks(it)
            alarmCrescendoRunnable = null
        }
        currentAlarmRingtone?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
            } catch (ignored: Exception) {
            }
            currentAlarmRingtone = null
        }
    }

    private fun snoozeWakeUpAlarm() {
        val am = alarmManager ?: return
        val snoozeTimeMs = System.currentTimeMillis() + SNOOZE_DURATION_MS

        val intent = Intent(this, MainService::class.java).setAction(ACTION_WAKEUP_ALARM_EXPIRY)
        val pendingIntent = PendingIntent.getService(
            this, 106, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(this, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            this, 102, showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val clockInfo = AlarmManager.AlarmClockInfo(snoozeTimeMs, showPendingIntent)

        try {
            am.setAlarmClock(clockInfo, pendingIntent)
            val timeFormat = android.text.format.DateFormat.getTimeFormat(this)
            val formattedTime = timeFormat.format(Date(snoozeTimeMs))
            EventLogger.log(this, "Wake-Up Goal alarm snoozed until $formattedTime")
        } catch (e: Exception) {
            EventLogger.log(this, "Failed to schedule snooze alarm: ${e.message}")
        }
    }

    private fun snoozeWakeUpAlarmViaFlip() {
        EventLogger.log(this, "Wake-Up Goal alarm snoozed via flip gesture")
        stopWakeUpAlarmSound()
        snoozeWakeUpAlarm()
        isWakeUpAlarmRinging = false
        isWakeUpAlarmSnoozed = true
        onTriggerVibration()
        updateListenersRegistration()
        updateNotification()
    }

    private fun dismissWakeUpAlarmViaVolumeKey() {
        EventLogger.log(this, EventLogger.LEVEL_HIGH, "Wake-Up Goal alarm dismissed via volume button")
        processSleepSessionOnAlarmDismissal()
        if (!isNapAlarmRinging) {
            updateNextWakeUpTimeOnDismissOrExpiry()
        }
        stopWakeUpAlarmSound()
        cancelSnoozeAlarm()
        cancelNapAlarm(false)
        setNapAlarmRinging(false)
        isWakeUpAlarmRinging = false
        isWakeUpAlarmSnoozed = false
        onTriggerVibration()
        updateListenersRegistration()
        updateNotification()
        Toast.makeText(this, R.string.toast_alarm_dismissed, Toast.LENGTH_SHORT).show()
    }

    private fun pauseMediaViaAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            val focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .build()
            am.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }

    private fun buildNotification(): Notification {
        val title: String
        var contentText: String
        val formattedDurationStr = formatDurationString(configuredDurationMinutes)

        val now = System.currentTimeMillis()
        val scheduledAlarm = calculateScheduledAlarm(this, now, timerEndsAt)
        val showWakeAlarm = isWakeAlarmEnabled() && scheduledAlarm != null &&
                (scheduledAlarm.timeInMillis - now) >= (configuredDurationMinutes * 1.5 * 60_000L).toLong()

        if (isWakeUpAlarmRinging) {
            title = getString(R.string.wakeup_alarm_title)
            contentText = getString(R.string.wakeup_alarm_text)
        } else if (isWakeUpAlarmSnoozed) {
            title = getString(R.string.wakeup_alarm_title)
            contentText = getString(R.string.wakeup_alarm_snoozed_text)
        } else if (!isEnabled) {
            title = getString(R.string.timer_off)
            contentText = if (showWakeAlarm) {
                val formattedAlarmTime = formatTime(scheduledAlarm!!.get(Calendar.HOUR_OF_DAY), scheduledAlarm.get(Calendar.MINUTE))
                getString(R.string.timer_off_expanded_alarm, formattedDurationStr, formattedAlarmTime)
            } else {
                getString(R.string.timer_off_collapsed, formattedDurationStr)
            }
        } else if (isFading) {
            title = getString(R.string.fading_title)
            contentText = getString(R.string.fading_collapsed)
        } else if (isActive) {
            val targetTimeStr = formatTargetTime()
            title = getString(R.string.active_title)

            contentText = if (showWakeAlarm) {
                val formattedAlarmTime = formatTime(scheduledAlarm!!.get(Calendar.HOUR_OF_DAY), scheduledAlarm.get(Calendar.MINUTE))
                getString(R.string.active_expanded_alarm, targetTimeStr, formattedDurationStr, formattedAlarmTime)
            } else {
                getString(R.string.active_collapsed, targetTimeStr, formattedDurationStr)
            }
        } else {
            title = getString(R.string.waiting_title)
            contentText = if (showWakeAlarm) {
                val formattedAlarmTime = formatTime(scheduledAlarm!!.get(Calendar.HOUR_OF_DAY), scheduledAlarm.get(Calendar.MINUTE))
                getString(R.string.waiting_expanded_alarm, formattedDurationStr, formattedAlarmTime)
            } else {
                getString(R.string.waiting_collapsed, formattedDurationStr)
            }
        }

        if (!isWakeUpAlarmRinging && !isWakeUpAlarmSnoozed && isNapActive()) {
            val timeFormat = android.text.format.DateFormat.getTimeFormat(this)
            val formattedNapTime = timeFormat.format(Date(napAlarmEndsAt))
            contentText += getString(R.string.notification_nap_suffix, formattedNapTime)
        }

        val contentIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_zzz)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(contentPendingIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)

        if (isWakeUpAlarmRinging) {
            builder.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    getString(R.string.action_awake),
                    awakeIntent()
                ).build()
            )
            builder.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_lock_idle_alarm),
                    getString(R.string.action_snooze_alarm),
                    snoozeWakeUpAlarmIntent()
                ).build()
            )
        } else if (isWakeUpAlarmSnoozed) {
            builder.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    getString(R.string.action_awake),
                    awakeIntent()
                ).build()
            )
        } else {
            val toggleAction: Notification.Action = if (isEnabled) {
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                    getString(R.string.action_turn_off),
                    turnOffIntent()
                ).build()
            } else {
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_play),
                    getString(R.string.action_turn_on),
                    turnOnIntent()
                ).build()
            }
            builder.addAction(toggleAction)

            val secondAction: Notification.Action = if (shouldShowAwakeAction()) {
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_lock_idle_alarm),
                    getString(R.string.action_awake),
                    awakeIntent()
                ).build()
            } else {
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_lock_idle_alarm),
                    getString(R.string.action_nap),
                    startNapIntent()
                ).build()
            }
            builder.addAction(secondAction)
        }

        return builder.build()
    }

    private fun reloadSettingsAndUpdate() {
        val prefs = preferences
        if (prefs == null) {
            updateNotification()
            return
        }

        val savedEnabled = prefs.getBoolean(KEY_ENABLED, true)
        val savedDuration = prefs.getInt(KEY_DURATION_MINUTES, AppDefaults.DURATION_MINUTES)
        val now = System.currentTimeMillis()
        val musicActive = audioManager != null && audioManager!!.isMusicActive

        reloadTimerSettings(savedEnabled, savedDuration, musicActive, now)

        val goalEnabled = isWakeAlarmEnabled()
        if (goalEnabled) {
            checkAndScheduleSmartWakeUpAlarm(timerEndsAt)
        } else {
            dismissAutoSleepAlarm()
        }

        updateNotification()
    }

    private fun showOrHideNotification() {
        startForeground(NOTIFICATION_ID, buildNotification())
        isForeground = true
    }

    private fun updateNotification() {
        showOrHideNotification()
    }

    private fun turnOffIntent(): PendingIntent {
        val intent = Intent(this, MainService::class.java).setAction(ACTION_TURN_OFF)
        return PendingIntent.getService(
            this, 5, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun turnOnIntent(): PendingIntent {
        val intent = Intent(this, MainService::class.java).setAction(ACTION_TURN_ON)
        return PendingIntent.getService(
            this, 7, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun startNapIntent(): PendingIntent {
        val intent = Intent(this, MainService::class.java).setAction(ACTION_START_NAP)
        return PendingIntent.getService(
            this, 12, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun snoozeWakeUpAlarmIntent(): PendingIntent {
        val intent = Intent(this, MainService::class.java).setAction(ACTION_SNOOZE_WAKEUP_ALARM)
        return PendingIntent.getService(
            this, 15, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun awakeIntent(): PendingIntent {
        val intent = Intent(this, MainService::class.java).setAction(ACTION_AWAKE)
        return PendingIntent.getService(
            this, 16, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun formatTargetTime(): String {
        val endsAt = timerEndsAt
        if (endsAt <= 0L) {
            return ""
        }
        val timeFormat = android.text.format.DateFormat.getTimeFormat(this)
        return timeFormat.format(Date(endsAt))
    }

    private fun formatTime(hour: Int, minute: Int): String {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        val timeFormat = android.text.format.DateFormat.getTimeFormat(this)
        return timeFormat.format(cal.time)
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager?
        if (manager != null) {
            val existingChannel = manager.getNotificationChannel(CHANNEL_ID)
            if (existingChannel != null && existingChannel.importance != NotificationManager.IMPORTANCE_LOW) {
                manager.deleteNotificationChannel(CHANNEL_ID)
            }
            val channel = NotificationChannel(
                CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW
            )
            channel.description = getString(R.string.notification_channel_description)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null && event.sensor != null && event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val now = System.currentTimeMillis()
            if (now - lastSensorEventTimeMs < SENSOR_THROTTLE_MS) {
                return
            }
            lastSensorEventTimeMs = now

            val z = event.values[2]
            var currentOrientation = ORIENTATION_UNKNOWN
            if (z < -8.5f) {
                currentOrientation = ORIENTATION_FACE_DOWN
            } else if (z > 8.5f) {
                currentOrientation = ORIENTATION_FACE_UP
            }

            if (currentOrientation != ORIENTATION_UNKNOWN) {
                if (lastOrientation != ORIENTATION_UNKNOWN && lastOrientation != currentOrientation) {
                    handler.post {
                        if (isWakeUpAlarmRinging) {
                            snoozeWakeUpAlarmViaFlip()
                        } else {
                            onPhoneFlipped(now)
                        }
                    }
                }
                lastOrientation = currentOrientation
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    override fun onDestroy() {
        EventLogger.log(this, EventLogger.LEVEL_LOW, "MainService destroyed")
        stopWakeUpAlarmSound()
        isWakeUpAlarmRinging = false
        isWakeUpAlarmSnoozed = false
        setNapAlarmRinging(false)
        unregisterSensorListener()
        unregisterVolumeObserver()
        unregisterDndReceiver()
        unregisterAudioPlaybackCallback()
        cancelTimerCallbacks()
        preferenceManager?.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        const val ACTION_SET_DURATION = "com.bas080.autosleepdroid.SET_DURATION"
        const val ACTION_TURN_OFF = "com.bas080.autosleepdroid.TURN_OFF"
        const val ACTION_TURN_ON = "com.bas080.autosleepdroid.TURN_ON"
        const val ACTION_ALARM_EXPIRY = "com.bas080.autosleepdroid.ALARM_EXPIRY"
        const val ACTION_WAKEUP_ALARM_EXPIRY = "com.bas080.autosleepdroid.AUTO_SLEEP_ALARM_EXPIRY"
        const val ACTION_DISMISS_WAKEUP_ALARM = "com.bas080.autosleepdroid.DISMISS_WAKEUP_ALARM"
        const val ACTION_SNOOZE_WAKEUP_ALARM = "com.bas080.autosleepdroid.SNOOZE_WAKEUP_ALARM"
        const val ACTION_AWAKE = "com.bas080.autosleepdroid.AWAKE"
        const val ACTION_REDRAW_NOTIFICATION = "com.bas080.autosleepdroid.REDRAW_NOTIFICATION"
        const val ACTION_CLEAR_GOAL = "com.bas080.autosleepdroid.CLEAR_GOAL"
        const val ACTION_AUTO_TIMER_CHECK = "com.bas080.autosleepdroid.AUTO_TIMER_CHECK"
        const val ACTION_START_NAP = "com.bas080.autosleepdroid.START_NAP"
        const val ACTION_CANCEL_NAP = "com.bas080.autosleepdroid.CANCEL_NAP"
        const val ACTION_NAP_EXPIRY = "com.bas080.autosleepdroid.NAP_EXPIRY"
        const val EXTRA_DURATION = "com.bas080.autosleepdroid.DURATION"
        const val EXTRA_NAP_DURATION_MINUTES = "extra_nap_duration_minutes"
        const val ALARM_SEARCH_NAME = "Auto Sleep"
        const val KEY_WAKEUP_LAST_SCHEDULED_MS = PreferenceKeys.KEY_WAKEUP_LAST_SCHEDULED_MS
        const val KEY_NAP_DURATION_MINUTES = PreferenceKeys.KEY_NAP_DURATION_MINUTES
        const val KEY_NAP_ALARM_ENDS_AT = PreferenceKeys.KEY_NAP_ALARM_ENDS_AT
        const val KEY_NAP_ALARM_RINGING = PreferenceKeys.KEY_NAP_ALARM_RINGING

        private const val CHANNEL_ID = "sleep_timer"
        private const val NOTIFICATION_ID = 1001
        private const val SNOOZE_DURATION_MS = AppDefaults.SNOOZE_DURATION_MS
        private const val PREFERENCES = PreferenceKeys.PREFERENCES_NAME
        private const val KEY_ENABLED = PreferenceKeys.KEY_ACTIVE
        private const val KEY_DURATION_MINUTES = PreferenceKeys.KEY_DURATION_MINUTES
        private const val KEY_TIMER_ENDS_AT = PreferenceKeys.KEY_TIMER_ENDS_AT
        private const val REMOTE_INPUT_KEY = "duration_minutes"
        private const val PAUSE_RESET_DELAY_MS = 500L
        private const val SENSOR_THROTTLE_MS = 300L
        private const val ALARM_CRESCENDO_DURATION_MS = AppDefaults.ALARM_CRESCENDO_DURATION_MS
        private const val ALARM_CRESCENDO_INTERVAL_MS = AppDefaults.ALARM_CRESCENDO_INTERVAL_MS

        private const val ORIENTATION_UNKNOWN = 0
        private const val ORIENTATION_FACE_UP = 1
        private const val ORIENTATION_FACE_DOWN = 2

        @JvmStatic
        fun isValidDuration(minutes: Int): Boolean {
            return minutes >= AppDefaults.MINUTES_MIN && minutes <= AppDefaults.MINUTES_MAX
        }

        @JvmStatic
        fun formatDurationString(totalMinutes: Int): String {
            return DurationUtils.formatDurationString(totalMinutes)
        }

        @JvmStatic
        fun parseDurationMinutes(input: String?): Int {
            return DurationUtils.parseDurationMinutes(input)
        }

        @JvmStatic
        fun calculateScheduledAlarm(context: Context?, now: Long, timerEndsAt: Long): Calendar? {
            context ?: return null
            val prefs = context.getSharedPreferences(PREFERENCES, MODE_PRIVATE)
            val wakeAlarmEnabled = prefs.getBoolean(PreferenceKeys.KEY_WAKE_UP_GOAL_ENABLED, false)
            if (!wakeAlarmEnabled) {
                return null
            }

            val goalHour = prefs.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_HOUR, AppDefaults.WAKE_UP_GOAL_HOUR)
            val goalMin = prefs.getInt(PreferenceKeys.KEY_WAKE_UP_GOAL_MINUTE, AppDefaults.WAKE_UP_GOAL_MINUTE)
            val currentHour = prefs.getInt(PreferenceKeys.KEY_CURRENT_WAKE_HOUR, goalHour)
            val currentMin = prefs.getInt(PreferenceKeys.KEY_CURRENT_WAKE_MINUTE, goalMin)
            val minSleepMin = prefs.getInt(PreferenceKeys.KEY_MIN_SLEEP_DURATION_MINUTES, AppDefaults.MIN_SLEEP_DURATION_MINUTES)

            val calCurrent = Calendar.getInstance()
            calCurrent.timeInMillis = now
            calCurrent.set(Calendar.HOUR_OF_DAY, currentHour)
            calCurrent.set(Calendar.MINUTE, currentMin)
            calCurrent.set(Calendar.SECOND, 0)
            calCurrent.set(Calendar.MILLISECOND, 0)

            if (calCurrent.timeInMillis <= now) {
                calCurrent.add(Calendar.DAY_OF_YEAR, 1)
            }

            var scheduledAlarmMillis = calCurrent.timeInMillis

            val timerDuration = prefs.getInt(PreferenceKeys.KEY_DURATION_MINUTES, AppDefaults.DURATION_MINUTES)
            val sleepStartTime = prefs.getLong(PreferenceKeys.KEY_SLEEP_START_TIME_MS, 0L)
            var minWakeTimeMillis = 0L
            if (timerEndsAt > 0L) {
                val effectiveMinSleepMs = Math.max(0L, (minSleepMin - timerDuration) * 60_000L)
                minWakeTimeMillis = timerEndsAt + effectiveMinSleepMs
            } else if (sleepStartTime > 0L && (now - sleepStartTime < 14 * 3600_000L)) {
                val effectiveMinSleepMs = Math.max(0L, (minSleepMin - timerDuration) * 60_000L)
                minWakeTimeMillis = sleepStartTime + effectiveMinSleepMs
            }

            if (minWakeTimeMillis > scheduledAlarmMillis) {
                scheduledAlarmMillis = minWakeTimeMillis
            }

            val calAlarm = Calendar.getInstance()
            calAlarm.timeInMillis = scheduledAlarmMillis
            return calAlarm
        }
    }
}
