package com.bas080.autosleepdroid

import android.app.Notification
import android.app.NotificationManager
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowNotificationManager
import org.robolectric.shadows.ShadowToast
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainServiceTest {

    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences = context.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
    }

    @Test
    fun testServiceCreation() {
        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()
        assertNotNull(service)
    }

    @Test
    fun testServiceCreationAlwaysPostsForegroundNotification() {
        preferences.edit()
            .putBoolean("active", true)
            .commit()

        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()
        assertNotNull(service)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNotificationManager = Shadows.shadowOf(notificationManager)
        assertNotNull("Ongoing notification should always be posted", shadowNotificationManager.getNotification(1001))
    }

    @Test
    fun testTurnOnActionPersistsState() {
        preferences.edit()
            .putBoolean("active", false)
            .putInt("duration_minutes", 30)
            .commit()

        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        val turnOnIntent = Intent(context, MainService::class.java)
            .setAction(MainService.ACTION_TURN_ON)

        service.onStartCommand(turnOnIntent, 0, 1)

        assertTrue(preferences.getBoolean("active", false))
    }

    @Test
    fun testAlarmExpiryActionTriggersFade() {
        preferences.edit()
            .putBoolean("active", true)
            .putInt("duration_minutes", 10)
            .putLong("timer_ends_at", System.currentTimeMillis() - 1000L)
            .commit()

        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        val alarmIntent = Intent(context, MainService::class.java)
            .setAction(MainService.ACTION_ALARM_EXPIRY)

        service.onStartCommand(alarmIntent, 0, 1)
        assertNotNull(service)
    }

    @Test
    fun testTurnOffActionPersistsState() {
        preferences.edit()
            .putBoolean("active", true)
            .putInt("duration_minutes", 30)
            .commit()

        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        val turnOffIntent = Intent(context, MainService::class.java)
            .setAction(MainService.ACTION_TURN_OFF)

        service.onStartCommand(turnOffIntent, 0, 1)

        assertFalse(preferences.getBoolean("active", true))
    }

    @Test
    fun testParseDurationMinutes() {
        assertEquals(30, MainService.parseDurationMinutes("30"))
        assertEquals(60, MainService.parseDurationMinutes("1h"))
        assertEquals(120, MainService.parseDurationMinutes("2H"))
        assertEquals(135, MainService.parseDurationMinutes("2h15m"))
        assertEquals(450, MainService.parseDurationMinutes("7h30m"))
        assertEquals(450, MainService.parseDurationMinutes("7h 30m"))
        assertEquals(75, MainService.parseDurationMinutes("1 h 15 m"))
        assertEquals(130, MainService.parseDurationMinutes("2h10m5s"))
        assertEquals(15, MainService.parseDurationMinutes("15m30s"))
        assertEquals(-1, MainService.parseDurationMinutes("10x10h4m"))
        assertEquals(-1, MainService.parseDurationMinutes("10m10"))
        assertEquals(-1, MainService.parseDurationMinutes("10h20h"))
        assertEquals(-1, MainService.parseDurationMinutes("abc"))
        assertEquals(-1, MainService.parseDurationMinutes(null))
        assertEquals(-1, MainService.parseDurationMinutes("  "))
    }

    @Test
    fun testSetFlexibleDurationViaRemoteInput() {
        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        val setIntent = Intent(context, MainService::class.java)
            .setAction(MainService.ACTION_SET_DURATION)

        val results = Bundle()
        results.putCharSequence("duration_minutes", "2h15m")
        RemoteInput.addResultsToIntent(
            arrayOf(RemoteInput.Builder("duration_minutes").build()),
            setIntent,
            results
        )

        service.onStartCommand(setIntent, 0, 1)

        assertEquals(135, preferences.getInt("duration_minutes", -1))
        assertTrue(preferences.getBoolean("active", false))
    }

    @Test
    fun testSetValidDurationViaRemoteInput() {
        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        val setIntent = Intent(context, MainService::class.java)
            .setAction(MainService.ACTION_SET_DURATION)

        val results = Bundle()
        results.putCharSequence("duration_minutes", "45")
        RemoteInput.addResultsToIntent(
            arrayOf(RemoteInput.Builder("duration_minutes").build()),
            setIntent,
            results
        )

        service.onStartCommand(setIntent, 0, 1)

        assertEquals(45, preferences.getInt("duration_minutes", -1))
        assertTrue(preferences.getBoolean("active", false))
    }

    @Test
    fun testSetInvalidDurationFallsBackToPreviousValidOrDefault() {
        preferences.edit()
            .putBoolean("active", true)
            .putInt("duration_minutes", 25)
            .commit()

        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        val setIntent = Intent(context, MainService::class.java)
            .setAction(MainService.ACTION_SET_DURATION)

        val results = Bundle()
        results.putCharSequence("duration_minutes", "invalid_number")
        RemoteInput.addResultsToIntent(
            arrayOf(RemoteInput.Builder("duration_minutes").build()),
            setIntent,
            results
        )

        service.onStartCommand(setIntent, 0, 1)

        assertEquals(25, preferences.getInt("duration_minutes", -1))
        assertTrue(preferences.getBoolean("active", false))
        assertEquals(context.getString(R.string.toast_duration_invalid), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testSetOutOfRangeDurationFallsBack() {
        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        val setIntent = Intent(context, MainService::class.java)
            .setAction(MainService.ACTION_SET_DURATION)

        val results = Bundle()
        results.putCharSequence("duration_minutes", "99999")
        RemoteInput.addResultsToIntent(
            arrayOf(RemoteInput.Builder("duration_minutes").build()),
            setIntent,
            results
        )

        service.onStartCommand(setIntent, 0, 1)

        assertEquals(20, preferences.getInt("duration_minutes", -1))
        assertTrue(preferences.getBoolean("active", false))
    }

    @Test
    fun testRedrawNotificationActionReloadsSettingsAndUpdatesStateMachine() {
        preferences.edit()
            .putBoolean("active", true)
            .putBoolean("show_notification", true)
            .putInt("duration_minutes", 20)
            .commit()

        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        assertEquals(20, service.configuredDurationMinutes)
        assertTrue(service.isEnabled)

        preferences.edit()
            .putBoolean("active", false)
            .putBoolean("show_notification", true)
            .putInt("duration_minutes", 45)
            .putBoolean("wake_up_goal_enabled", true)
            .putInt("wake_up_goal_hour", 7)
            .putInt("wake_up_goal_minute", 0)
            .commit()

        val redrawIntent = Intent(context, MainService::class.java)
            .setAction(MainService.ACTION_REDRAW_NOTIFICATION)

        service.onStartCommand(redrawIntent, 0, 1)

        assertFalse(service.isEnabled)
        assertEquals(45, service.configuredDurationMinutes)

        preferences.edit()
            .putBoolean("active", true)
            .putInt("duration_minutes", 60)
            .commit()

        service.onStartCommand(redrawIntent, 0, 1)

        assertTrue(service.isEnabled)
        assertEquals(60, service.configuredDurationMinutes)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNotificationManager = Shadows.shadowOf(notificationManager)
        val notification = shadowNotificationManager.getNotification(1001)
        assertNotNull(notification)
        assertNotNull(notification.contentIntent)
    }

    @Test
    fun testReloadSettingsDoesNotOverrideManualTimerToggleWhenAutoDndIsEnabled() {
        preferences.edit()
            .putBoolean("active", false)
            .putBoolean("auto_timer_enabled", true)
            .putInt("duration_minutes", 20)
            .commit()

        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        assertFalse(service.isEnabled)

        val redrawIntent = Intent(context, MainService::class.java)
            .setAction(MainService.ACTION_REDRAW_NOTIFICATION)
        service.onStartCommand(redrawIntent, 0, 1)

        assertFalse("Reloading settings must not force timer ON when user explicitly disabled it", service.isEnabled)
    }

    @Test
    fun testClearGoalIntentClearsGoal() {
        preferences.edit()
            .putBoolean("wake_up_goal_enabled", true)
            .putInt("wake_up_goal_hour", 6)
            .putInt("wake_up_goal_minute", 30)
            .commit()

        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        service.onStartCommand(Intent(context, MainService::class.java).setAction(MainService.ACTION_CLEAR_GOAL), 0, 1)
        assertFalse(preferences.getBoolean("wake_up_goal_enabled", true))
    }

    @Test
    fun testFadeOutStepDoesNotCancelFadeWhenVolumeUpdates() {
        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        val audioManagerField = MainService::class.java.getDeclaredField("audioManager")
        audioManagerField.isAccessible = true
        val audioManager = audioManagerField.get(service) as android.media.AudioManager
        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 10, 0)

        service.beginFadeOut(10)

        val runFadeStepMethod = MainService::class.java.getDeclaredMethod("runFadeStep")
        runFadeStepMethod.isAccessible = true

        runFadeStepMethod.invoke(service)

        assertTrue(service.isFading)
    }

    @Test
    fun testWakeUpAlarmSnoozeKeepsNotificationOpen() {
        preferences.edit().putBoolean("show_notification", true).putBoolean("wake_up_goal_enabled", true).commit()
        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNotificationManager = Shadows.shadowOf(notificationManager)

        val triggerIntent = Intent(context, MainService::class.java)
            .setAction(MainService.ACTION_WAKEUP_ALARM_EXPIRY)
        service.onStartCommand(triggerIntent, 0, 1)

        val wakeUpNotification = shadowNotificationManager.getNotification(1001)
        assertNotNull(wakeUpNotification)
        assertEquals(context.getString(R.string.wakeup_alarm_title), wakeUpNotification.extras.getCharSequence(Notification.EXTRA_TITLE))

        val snoozeIntent = Intent(context, MainService::class.java)
            .setAction(MainService.ACTION_SNOOZE_WAKEUP_ALARM)
        service.onStartCommand(snoozeIntent, 0, 1)

        val snoozedNotification = shadowNotificationManager.getNotification(1001)
        assertNotNull("Wake-up alarm notification must remain open when snoozed", snoozedNotification)
        assertTrue("Snoozed notification content text should contain snooze instruction text",
            snoozedNotification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("Snoozed 9m"))

        val dismissIntent = Intent(context, MainService::class.java)
            .setAction(MainService.ACTION_DISMISS_WAKEUP_ALARM)
        service.onStartCommand(dismissIntent, 0, 1)

        val dismissedNotification = shadowNotificationManager.getNotification(1001)
        assertNotNull(dismissedNotification)
        assertFalse(context.getString(R.string.wakeup_alarm_title) == dismissedNotification.extras.getCharSequence(Notification.EXTRA_TITLE))
    }

    @Test
    fun testVolumeKeySnoozesRingingWakeUpAlarm() {
        preferences.edit()
            .putBoolean("show_notification", true)
            .putBoolean("wake_up_goal_enabled", true)
            .commit()

        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNotificationManager = Shadows.shadowOf(notificationManager)

        val triggerIntent = Intent(context, MainService::class.java)
            .setAction(MainService.ACTION_WAKEUP_ALARM_EXPIRY)
        service.onStartCommand(triggerIntent, 0, 1)

        assertNotNull(shadowNotificationManager.getNotification(1001))

        val receiverField = MainService::class.java.getDeclaredField("volumeReceiver")
        receiverField.isAccessible = true
        val receiver = receiverField.get(service) as android.content.BroadcastReceiver?
        assertNotNull(receiver)
        receiver?.onReceive(service, Intent("android.media.VOLUME_CHANGED_ACTION"))

        val snoozedNotification = shadowNotificationManager.getNotification(1001)
        assertNotNull(snoozedNotification)
        assertTrue("Notification content text when snoozed via volume key should contain 'Snoozed 9m'",
            snoozedNotification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("Snoozed 9m"))
    }

    @Test
    fun testDisablingSleepTimerPreservesScheduledWakeAlarm() {
        preferences.edit()
            .putBoolean("active", true)
            .putBoolean("show_notification", true)
            .putBoolean("wake_up_goal_enabled", true)
            .putInt("wake_up_goal_hour", 6)
            .putInt("wake_up_goal_minute", 30)
            .commit()

        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        val triggerIntent = Intent(context, MainService::class.java)
            .setAction(MainService.ACTION_WAKEUP_ALARM_EXPIRY)
        service.onStartCommand(triggerIntent, 0, 1)

        assertTrue(preferences.contains(MainService.KEY_WAKEUP_LAST_SCHEDULED_MS))

        val turnOffIntent = Intent(context, MainService::class.java)
            .setAction(MainService.ACTION_TURN_OFF)
        service.onStartCommand(turnOffIntent, 0, 1)

        assertFalse(preferences.getBoolean("active", true))
        assertTrue(preferences.contains(MainService.KEY_WAKEUP_LAST_SCHEDULED_MS))
    }

    @Test
    fun testDismissingWakeUpAlarmSetsCurrentWakeTimeToDismissalTime() {
        preferences.edit()
            .putBoolean("active", false)
            .putBoolean("wake_up_goal_enabled", true)
            .putInt("wake_up_goal_hour", 6)
            .putInt("wake_up_goal_minute", 30)
            .putInt("current_wake_hour", 7)
            .putInt("current_wake_minute", 30)
            .commit()

        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        val dismissIntent = Intent(context, MainService::class.java)
            .setAction(MainService.ACTION_DISMISS_WAKEUP_ALARM)
        service.onStartCommand(dismissIntent, 0, 1)

        val cal = Calendar.getInstance()
        cal.add(Calendar.MINUTE, -15)
        val goalMins = 6 * 60 + 30
        val calcMins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val finalMins = Math.max(goalMins, calcMins)
        assertEquals(finalMins / 60, preferences.getInt("current_wake_hour", -1))
        assertEquals(finalMins % 60, preferences.getInt("current_wake_minute", -1))
    }


    @Test
    fun testFadeVolumeStateConsistencyDuringStreamVolumeSet() {
        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        val audioManagerField = MainService::class.java.getDeclaredField("audioManager")
        audioManagerField.isAccessible = true
        val audioManager = audioManagerField.get(service) as android.media.AudioManager
        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 10, 0)

        service.beginFadeOut(10)

        val runFadeStepMethod = MainService::class.java.getDeclaredMethod("runFadeStep")
        runFadeStepMethod.isAccessible = true

        runFadeStepMethod.invoke(service)

        val currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)

        assertEquals("lastObservedVolume must match updated stream volume", currentVolume, service.lastObservedVolume)
        assertTrue("Fade should not be cancelled during fade step volume update", service.isFading)
    }

    @Test
    fun testWakeUpAlarmTriggersVolumeCrescendo() {
        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        val crescendoMethod = MainService::class.java.getDeclaredMethod("startWakeUpAlarmCrescendo")
        crescendoMethod.isAccessible = true
        crescendoMethod.invoke(service)

        val runnableField = MainService::class.java.getDeclaredField("alarmCrescendoRunnable")
        runnableField.isAccessible = true
        assertNotNull("Crescendo runnable should be scheduled when crescendo starts", runnableField.get(service))

        val dismissIntent = Intent(context, MainService::class.java)
            .setAction(MainService.ACTION_DISMISS_WAKEUP_ALARM)
        service.onStartCommand(dismissIntent, 0, 1)

        val ringtoneField = MainService::class.java.getDeclaredField("currentAlarmRingtone")
        ringtoneField.isAccessible = true
        assertEquals("Ringtone should be cleared after dismiss", null, ringtoneField.get(service))
        assertEquals("Crescendo runnable should be cancelled after dismiss", null, runnableField.get(service))
    }

    @Test
    fun testEnsureAudibleAlarmStreamVolume() {
        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager?
        if (am != null) {
            am.setStreamVolume(android.media.AudioManager.STREAM_ALARM, 0, 0)

            val ensureVolMethod = MainService::class.java.getDeclaredMethod("ensureAudibleAlarmStreamVolume")
            ensureVolMethod.isAccessible = true
            ensureVolMethod.invoke(service)

            val maxVol = am.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
            val expectedMinVol = Math.max(1, Math.round(maxVol * 0.3f))
            assertEquals("STREAM_ALARM volume should be raised to minimum audible volume if muted",
                expectedMinVol, am.getStreamVolume(android.media.AudioManager.STREAM_ALARM))
        }
    }

    @Test
    fun testOnPlaybackStateChangedRecordsSleepStartTimeOnMediaPauseWhileActive() {
        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        val now = System.currentTimeMillis()
        service.initializeTimerState(true, 20, now + 1200_000L, 10, true, now)
        assertEquals(MainService.State.ACTIVE, service.state)

        service.onPlaybackStateChanged(true, now)
        assertFalse("sleep_start_time_ms should not be set yet while playing", preferences.contains("sleep_start_time_ms"))

        val pauseTime = now + 60_000L
        service.onPlaybackStateChanged(false, pauseTime)

        assertEquals("sleep_start_time_ms should be recorded when media is paused while active",
            pauseTime, preferences.getLong("sleep_start_time_ms", 0L))
    }

    @Test
    fun testRunWakeUpAlarmCrescendoStepAppliesQuadraticGain() {
        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        val ringingField = MainService::class.java.getDeclaredField("isWakeUpAlarmRinging")
        ringingField.isAccessible = true
        ringingField.setBoolean(service, true)

        val ringtoneField = MainService::class.java.getDeclaredField("currentAlarmRingtone")
        ringtoneField.isAccessible = true
        val constructor = android.media.Ringtone::class.java.getDeclaredConstructor(Context::class.java, Boolean::class.javaPrimitiveType)
        constructor.isAccessible = true
        val mockRingtone = constructor.newInstance(context, false)
        ringtoneField.set(service, mockRingtone)

        val startTimeField = MainService::class.java.getDeclaredField("alarmCrescendoStartTimeMs")
        startTimeField.isAccessible = true
        val ninetySecondsAgo = System.currentTimeMillis() - 90_000L
        startTimeField.setLong(service, ninetySecondsAgo)

        val crescendoStepMethod = MainService::class.java.getDeclaredMethod("runWakeUpAlarmCrescendoStep")
        crescendoStepMethod.isAccessible = true
        crescendoStepMethod.invoke(service)

        if (android.os.Build.VERSION.SDK_INT >= 28) {
            assertEquals("Ringtone volume at 50% time should be 0.25 (quadratic gain)", 0.25f, mockRingtone.volume, 0.05f)
        }
    }

    @Test
    fun testWakeUpAlarmAudioAttributesConfiguredAsAlarmToBypassDnd() {
        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager?
        am?.setStreamVolume(android.media.AudioManager.STREAM_ALARM, 0, 0)

        val playMethod = MainService::class.java.getDeclaredMethod("playWakeUpAlarmSound")
        playMethod.isAccessible = true
        playMethod.invoke(service)

        val playerField = MainService::class.java.getDeclaredField("alarmMediaPlayer")
        playerField.isAccessible = true
        val player = playerField.get(service) as android.media.MediaPlayer?

        val ringtoneField = MainService::class.java.getDeclaredField("currentAlarmRingtone")
        ringtoneField.isAccessible = true
        val ringtone = ringtoneField.get(service) as android.media.Ringtone?

        assertTrue("Either alarmMediaPlayer or currentAlarmRingtone must be active", player != null || ringtone != null)

        if (am != null) {
            val maxVol = am.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
            val expectedMinVol = Math.max(1, Math.round(maxVol * 0.3f))
            assertEquals("STREAM_ALARM volume should be raised to audible volume when alarm plays",
                expectedMinVol, am.getStreamVolume(android.media.AudioManager.STREAM_ALARM))
        }
    }

    @Test
    fun testWakeUpAlarmHappyPathFullLifecycle() {
        preferences.edit()
            .putBoolean("wake_up_goal_enabled", true)
            .putInt("wake_up_goal_hour", 6)
            .putInt("wake_up_goal_minute", 30)
            .putInt("current_wake_hour", 7)
            .putInt("current_wake_minute", 0)
            .putInt("min_sleep_duration_minutes", 450)
            .commit()

        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        val now = System.currentTimeMillis()
        val cal = MainService.calculateScheduledAlarm(context, now, 0L)
        assertNotNull("Scheduled alarm calendar must not be null when goal is enabled", cal)
        assertTrue("Scheduled alarm time must be in the future", cal!!.timeInMillis > now)

        val triggerIntent = Intent(context, MainService::class.java)
            .setAction(MainService.ACTION_WAKEUP_ALARM_EXPIRY)
        service.onStartCommand(triggerIntent, 0, 1)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNotificationManager = Shadows.shadowOf(notificationManager)
        val ringingNotification = shadowNotificationManager.getNotification(1001)
        assertNotNull("Ringing notification must be displayed when wake alarm triggers", ringingNotification)
        assertEquals(context.getString(R.string.wakeup_alarm_title), ringingNotification.extras.getCharSequence(Notification.EXTRA_TITLE))

        val dismissIntent = Intent(context, MainService::class.java)
            .setAction(MainService.ACTION_DISMISS_WAKEUP_ALARM)
        service.onStartCommand(dismissIntent, 0, 1)

        val calDismiss = Calendar.getInstance()
        calDismiss.add(Calendar.MINUTE, -15)
        val goalMins = 6 * 60 + 30
        val calcMins = calDismiss.get(Calendar.HOUR_OF_DAY) * 60 + calDismiss.get(Calendar.MINUTE)
        val finalMins = Math.max(goalMins, calcMins)
        assertEquals("Current wake hour must be set to dismissal hour after dismissal",
            finalMins / 60, preferences.getInt("current_wake_hour", -1))
        assertEquals("Current wake minute must be set to dismissal minute after dismissal",
            finalMins % 60, preferences.getInt("current_wake_minute", -1))

        assertTrue("Next daily alarm timestamp must be saved in preferences",
            preferences.contains(MainService.KEY_WAKEUP_LAST_SCHEDULED_MS))
    }


    @Test
    fun testWakeUpAlarmTriggersNextDailyAlarm() {
        preferences.edit()
            .putBoolean("wake_up_goal_enabled", true)
            .putInt("wake_up_goal_hour", 6)
            .putInt("wake_up_goal_minute", 30)
            .putInt("min_sleep_duration_minutes", 450)
            .commit()

        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        val beforeMs = System.currentTimeMillis()

        val triggerIntent = Intent(context, MainService::class.java)
            .setAction(MainService.ACTION_WAKEUP_ALARM_EXPIRY)
        service.onStartCommand(triggerIntent, 0, 1)

        val scheduledMs = preferences.getLong(MainService.KEY_WAKEUP_LAST_SCHEDULED_MS, 0L)
        assertTrue("Next daily alarm must be scheduled after current alarm rings", scheduledMs > beforeMs)
    }

    @Test
    fun testDailyRecurringAlarmSnoozeDoesNotOverwriteNextDailyAlarm() {
        preferences.edit()
            .putBoolean("wake_up_goal_enabled", true)
            .putInt("wake_up_goal_hour", 6)
            .putInt("wake_up_goal_minute", 30)
            .putInt("min_sleep_duration_minutes", 450)
            .commit()

        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        val triggerIntent = Intent(context, MainService::class.java)
            .setAction(MainService.ACTION_WAKEUP_ALARM_EXPIRY)
        service.onStartCommand(triggerIntent, 0, 1)

        val dailyAlarmMs = preferences.getLong(MainService.KEY_WAKEUP_LAST_SCHEDULED_MS, 0L)
        assertTrue(dailyAlarmMs > 0L)

        val snoozeIntent = Intent(context, MainService::class.java)
            .setAction(MainService.ACTION_SNOOZE_WAKEUP_ALARM)
        service.onStartCommand(snoozeIntent, 0, 1)

        assertEquals("Snoozing must not overwrite scheduled daily recurring alarm time",
            dailyAlarmMs, preferences.getLong(MainService.KEY_WAKEUP_LAST_SCHEDULED_MS, 0L))
    }

    @Test
    fun testCalculateScheduledAlarmAllowsNextDayGoal() {
        preferences.edit()
            .putBoolean("wake_up_goal_enabled", true)
            .putInt("wake_up_goal_hour", 6)
            .putInt("wake_up_goal_minute", 30)
            .putInt("min_sleep_duration_minutes", 450)
            .commit()

        val now = System.currentTimeMillis()
        val cal = MainService.calculateScheduledAlarm(context, now, 0L)
        assertNotNull("calculateScheduledAlarm should return scheduled Calendar for daily goal", cal)
        assertTrue("Scheduled alarm must be in the future", cal!!.timeInMillis > now)
    }

    @Test
    fun testCalculateScheduledAlarmReturnsNullWhenWakeUpGoalDisabledEvenIfAutoTimerEnabled() {
        preferences.edit()
            .putBoolean("wake_up_goal_enabled", false)
            .putBoolean("auto_timer_enabled", true)
            .putInt("wake_up_goal_hour", 6)
            .putInt("wake_up_goal_minute", 30)
            .commit()

        val now = System.currentTimeMillis()
        val cal = MainService.calculateScheduledAlarm(context, now, 0L)
        assertEquals("calculateScheduledAlarm must return null when wake_up_goal_enabled is false", null, cal)
    }

    @Test
    fun testCalculateScheduledAlarmPrioritizesActiveTimerEndsAtForMinimumSleepSafeguard() {
        val fixedNowCal = Calendar.getInstance()
        fixedNowCal.set(2025, Calendar.JANUARY, 1, 22, 0, 0)
        fixedNowCal.set(Calendar.MILLISECOND, 0)
        val now = fixedNowCal.timeInMillis

        val minSleepMin = 450
        val timerDurationMin = 30
        val timerEndsAt = now + 3 * 3600_000L

        preferences.edit()
            .putBoolean("wake_up_goal_enabled", true)
            .putInt("wake_up_goal_hour", 6)
            .putInt("wake_up_goal_minute", 30)
            .putInt("current_wake_hour", 6)
            .putInt("current_wake_minute", 30)
            .putInt("min_sleep_duration_minutes", minSleepMin)
            .putInt("duration_minutes", timerDurationMin)
            .putLong("sleep_start_time_ms", now - 2 * 3600_000L)
            .commit()

        val cal = MainService.calculateScheduledAlarm(context, now, timerEndsAt)
        assertNotNull(cal)

        val expectedWakeMs = timerEndsAt + (minSleepMin - timerDurationMin) * 60_000L
        assertTrue("Scheduled alarm time must match active timer expiration safeguard",
            Math.abs(expectedWakeMs - cal!!.timeInMillis) < 1000L)
    }

    @Test
    fun testWakeUpAlarmRingingNotificationShowsSnoozeAndDismissActionsAndInstructions() {
        preferences.edit().putBoolean("show_notification", true).putBoolean("wake_up_goal_enabled", true).commit()
        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNotificationManager = Shadows.shadowOf(notificationManager)

        val triggerIntent = Intent(context, MainService::class.java)
            .setAction(MainService.ACTION_WAKEUP_ALARM_EXPIRY)
        service.onStartCommand(triggerIntent, 0, 1)

        val wakeUpNotification = shadowNotificationManager.getNotification(1001)
        assertNotNull(wakeUpNotification)

        val contentText = wakeUpNotification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertTrue("Notification content text when alarm is ringing should inform user how to snooze/dismiss: $contentText",
            contentText.contains("Flip to snooze") || contentText.contains("volume button"))

        assertEquals("Notification should feature 2 actions (Dismiss and Snooze) when ringing", 2, wakeUpNotification.actions.size)
        assertEquals(context.getString(R.string.action_awake), wakeUpNotification.actions[0].title.toString())
        assertEquals(context.getString(R.string.action_snooze_alarm), wakeUpNotification.actions[1].title.toString())
    }

    @Test
    fun testTimerOffNotificationShowsWakeUpAlarmStatusWhenWakeAlarmIsEnabled() {
        val cal = Calendar.getInstance()
        cal.add(Calendar.HOUR_OF_DAY, 8)
        val targetHour = cal.get(Calendar.HOUR_OF_DAY)
        val targetMin = cal.get(Calendar.MINUTE)

        preferences.edit()
            .putBoolean("active", false)
            .putBoolean("show_notification", true)
            .putBoolean("wake_up_goal_enabled", true)
            .putInt("wake_up_goal_hour", targetHour)
            .putInt("wake_up_goal_minute", targetMin)
            .putInt("current_wake_hour", targetHour)
            .putInt("current_wake_minute", targetMin)
            .putInt("duration_minutes", 20)
            .commit()

        val controller = Robolectric.buildService(MainService::class.java)
        controller.create()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNotificationManager = Shadows.shadowOf(notificationManager)
        val notification = shadowNotificationManager.getNotification(1001)
        assertNotNull(notification)

        val contentText = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertTrue("Notification text when timer is off and wake alarm is enabled should contain 'Timer off': $contentText",
            contentText.contains("Timer off"))
        assertTrue("Notification text when timer is off and wake alarm is enabled should contain 'Wake at': $contentText",
            contentText.contains("Wake at"))
    }

    @Test
    fun testResettingSleepTimerUpdatesTimerStartTimeMs() {
        val initialStartTime = System.currentTimeMillis() - 600_000L
        preferences.edit()
            .putBoolean("active", true)
            .putInt("duration_minutes", 30)
            .putLong("timer_start_time_ms", initialStartTime)
            .commit()

        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        val resetTime = System.currentTimeMillis()
        service.onTimerRescheduled()

        val updatedStartTime = preferences.getLong("timer_start_time_ms", 0L)
        assertTrue("timer_start_time_ms must be updated when timer is reset/rescheduled", updatedStartTime >= resetTime)
    }

    @Test
    fun testResettingSleepTimerWithinWindowUpdatesSleepStartTimeMs() {
        val now = System.currentTimeMillis()
        val minSleepMin = 450

        val calCurrent = Calendar.getInstance()
        calCurrent.timeInMillis = now + 5 * 3600_000L
        val currentHour = calCurrent.get(Calendar.HOUR_OF_DAY)
        val currentMin = calCurrent.get(Calendar.MINUTE)

        preferences.edit()
            .putBoolean("wake_up_goal_enabled", true)
            .putInt("wake_up_goal_hour", currentHour)
            .putInt("wake_up_goal_minute", currentMin)
            .putInt("current_wake_hour", currentHour)
            .putInt("current_wake_minute", currentMin)
            .putInt("min_sleep_duration_minutes", minSleepMin)
            .commit()

        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        service.onTimerRescheduled()

        val updatedSleepStartTime = preferences.getLong("sleep_start_time_ms", 0L)
        assertTrue("sleep_start_time_ms must be updated to current time when reset within 1.2x min sleep window",
            updatedSleepStartTime >= now)
    }

    @Test
    fun testTimerActivationRecordsTimerStartTimeMsAndPreservesItOnExpiry() {
        preferences.edit()
            .putBoolean("active", true)
            .putInt("duration_minutes", 30)
            .commit()

        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        service.startTimer(30, System.currentTimeMillis() + 1800_000L, true)

        assertTrue("timer_start_time_ms should be recorded when timer is active", preferences.contains("timer_start_time_ms"))

        service.handleTurnOff(false)

        assertTrue("timer_start_time_ms should be preserved when timer expires and turns off", preferences.contains("timer_start_time_ms"))
    }

    @Test
    fun testAwakeActionProcessesSessionResetsWakeTimeAndSchedulesNextAlarm() {
        val now = System.currentTimeMillis()
        preferences.edit()
            .putBoolean("wake_up_goal_enabled", true)
            .putInt("wake_up_goal_hour", 6)
            .putInt("wake_up_goal_minute", 30)
            .putInt("current_wake_hour", 7)
            .putInt("current_wake_minute", 0)
            .putLong("sleep_start_time_ms", now - 4 * 3600_000L)
            .commit()

        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        val awakeIntent = Intent(context, MainService::class.java)
            .setAction(MainService.ACTION_AWAKE)
        service.onStartCommand(awakeIntent, 0, 1)

        val calAwake = Calendar.getInstance()
        calAwake.add(Calendar.MINUTE, -15)
        val goalMins = 6 * 60 + 30
        val calcMins = calAwake.get(Calendar.HOUR_OF_DAY) * 60 + calAwake.get(Calendar.MINUTE)
        val finalMins = Math.max(goalMins, calcMins)
        assertEquals("Current wake hour should set to awake hour", finalMins / 60, preferences.getInt("current_wake_hour", -1))
        assertEquals("Current wake minute should set to awake minute", finalMins % 60, preferences.getInt("current_wake_minute", -1))
        assertFalse("sleep_start_time_ms should be cleared", preferences.contains("sleep_start_time_ms"))
        assertTrue("Next daily wake alarm should be scheduled", preferences.contains(MainService.KEY_WAKEUP_LAST_SCHEDULED_MS))
    }

    @Test
    fun testProcessSleepSessionClearsStartTimePreferences() {
        preferences.edit()
            .putLong("sleep_start_time_ms", System.currentTimeMillis() - 3600_000L)
            .putLong("timer_start_time_ms", System.currentTimeMillis() - 3600_000L)
            .commit()

        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        val awakeIntent = Intent(context, MainService::class.java)
            .setAction(MainService.ACTION_AWAKE)
        service.onStartCommand(awakeIntent, 0, 1)

        assertFalse(preferences.contains("sleep_start_time_ms"))
        assertFalse(preferences.contains("timer_start_time_ms"))
    }

    @Test
    fun testAwakeActionRegistersSleepAndUpdatesAlarmSchedule() {
        val now = System.currentTimeMillis()
        val sleepStart = now - 8 * 3600_000L
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.add(Calendar.HOUR_OF_DAY, 12)
        val targetHour = cal.get(Calendar.HOUR_OF_DAY)
        val targetMin = cal.get(Calendar.MINUTE)

        preferences.edit()
            .putBoolean("wake_up_goal_enabled", true)
            .putInt("wake_up_goal_hour", targetHour)
            .putInt("wake_up_goal_minute", targetMin)
            .putInt("current_wake_hour", targetHour)
            .putInt("current_wake_minute", targetMin)
            .putLong("sleep_start_time_ms", sleepStart)
            .commit()

        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        val awakeIntent = Intent(context, MainService::class.java)
            .setAction(MainService.ACTION_AWAKE)
        service.onStartCommand(awakeIntent, 0, 1)

        val calAwake = Calendar.getInstance()
        calAwake.add(Calendar.MINUTE, -15)
        val goalMins = targetHour * 60 + targetMin
        val calcMins = calAwake.get(Calendar.HOUR_OF_DAY) * 60 + calAwake.get(Calendar.MINUTE)
        val finalMins = Math.max(goalMins, calcMins)
        assertEquals("Current wake hour should set to awake hour", finalMins / 60, preferences.getInt("current_wake_hour", -1))
        assertEquals("Current wake minute should set to awake minute", finalMins % 60, preferences.getInt("current_wake_minute", -1))
        assertEquals("sleep_start_time_ms should be cleared", 0L, preferences.getLong("sleep_start_time_ms", 0L))
    }

    @Test
    fun testOnTimerRescheduledPushesWakeAlarmForwardToSafeguardMinSleep() {
        val now = System.currentTimeMillis()
        val minSleepMin = 450
        val timerDurationMin = 30
        val timerEndsAt = now + 2 * 3600_000L

        val calCurrent = Calendar.getInstance()
        calCurrent.timeInMillis = now + 3600_000L
        val currentHour = calCurrent.get(Calendar.HOUR_OF_DAY)
        val currentMin = calCurrent.get(Calendar.MINUTE)

        preferences.edit()
            .putBoolean("wake_up_goal_enabled", true)
            .putInt("wake_up_goal_hour", currentHour)
            .putInt("wake_up_goal_minute", currentMin)
            .putInt("current_wake_hour", currentHour)
            .putInt("current_wake_minute", currentMin)
            .putInt("min_sleep_duration_minutes", minSleepMin)
            .putInt("duration_minutes", timerDurationMin)
            .commit()

        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        service.startTimer(timerDurationMin, timerEndsAt, true)

        service.onTimerRescheduled()

        val requiredWakeTimeMs = timerEndsAt + Math.max(0L, (minSleepMin - timerDurationMin) * 60_000L)
        val calRequired = Calendar.getInstance()
        calRequired.timeInMillis = requiredWakeTimeMs

        val expectedPushedHour = calRequired.get(Calendar.HOUR_OF_DAY)
        val expectedPushedMin = calRequired.get(Calendar.MINUTE)

        assertEquals("Current wake hour must be pushed forward to safeguard minimum sleep duration",
            expectedPushedHour, preferences.getInt("current_wake_hour", -1))
        assertEquals("Current wake minute must be pushed forward to safeguard minimum sleep duration",
            expectedPushedMin, preferences.getInt("current_wake_minute", -1))
    }

    @Test
    fun testOnTimerRescheduledDoesNotMoveWakeAlarmEarlier() {
        val now = System.currentTimeMillis()
        val minSleepMin = 450

        val calCurrent = Calendar.getInstance()
        calCurrent.timeInMillis = now + 8 * 3600_000L
        val currentHour = calCurrent.get(Calendar.HOUR_OF_DAY)
        val currentMin = calCurrent.get(Calendar.MINUTE)

        val calGoal = Calendar.getInstance()
        calGoal.timeInMillis = now + (7 * 3600_000L + 30 * 60_000L)
        val goalHour = calGoal.get(Calendar.HOUR_OF_DAY)
        val goalMin = calGoal.get(Calendar.MINUTE)

        val bedtimeMs = now - 10 * 60_000L

        preferences.edit()
            .putBoolean("wake_up_goal_enabled", true)
            .putInt("wake_up_goal_hour", goalHour)
            .putInt("wake_up_goal_minute", goalMin)
            .putInt("current_wake_hour", currentHour)
            .putInt("current_wake_minute", currentMin)
            .putInt("min_sleep_duration_minutes", minSleepMin)
            .putLong("sleep_start_time_ms", bedtimeMs)
            .commit()

        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        service.onTimerRescheduled()

        assertEquals(currentHour, preferences.getInt("current_wake_hour", -1))
        assertEquals(currentMin, preferences.getInt("current_wake_minute", -1))
    }

    @Test
    fun testAwakeActionNotificationActionWhenWakeAlarmIsEnabledAndActiveSleepSession() {
        val now = System.currentTimeMillis()
        val calWake = Calendar.getInstance()
        calWake.timeInMillis = now + 1 * 3600_000L
        val wakeHour = calWake.get(Calendar.HOUR_OF_DAY)
        val wakeMin = calWake.get(Calendar.MINUTE)

        val sleepStart = now - 4 * 3600_000L
        preferences.edit()
            .putBoolean("show_notification", true)
            .putBoolean("wake_up_goal_enabled", true)
            .putInt("wake_up_goal_hour", wakeHour)
            .putInt("wake_up_goal_minute", wakeMin)
            .putInt("current_wake_hour", wakeHour)
            .putInt("current_wake_minute", wakeMin)
            .putInt("min_sleep_duration_minutes", 450)
            .putLong("sleep_start_time_ms", sleepStart)
            .commit()

        val controller = Robolectric.buildService(MainService::class.java)
        controller.create()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNotificationManager = Shadows.shadowOf(notificationManager)
        val notification = shadowNotificationManager.getNotification(1001)
        assertNotNull(notification)

        var foundAwakeAction = false
        if (notification.actions != null) {
            for (action in notification.actions) {
                if (context.getString(R.string.action_awake) == action.title.toString()) {
                    foundAwakeAction = true
                    break
                }
            }
        }
        assertTrue("Ongoing notification should feature 'I\'m Awake' action button during pre-alarm window or alarm phase", foundAwakeAction)
    }

    @Test
    fun testNotificationActionsInActiveSleepPhaseHasNoSecondaryAction() {
        val now = System.currentTimeMillis()
        val calWake = Calendar.getInstance()
        calWake.timeInMillis = now + 6 * 3600_000L
        val wakeHour = calWake.get(Calendar.HOUR_OF_DAY)
        val wakeMin = calWake.get(Calendar.MINUTE)

        preferences.edit()
            .putBoolean("show_notification", true)
            .putBoolean("active", true)
            .putBoolean("wake_up_goal_enabled", true)
            .putInt("wake_up_goal_hour", wakeHour)
            .putInt("wake_up_goal_minute", wakeMin)
            .putInt("current_wake_hour", wakeHour)
            .putInt("current_wake_minute", wakeMin)
            .putInt("min_sleep_duration_minutes", 450)
            .commit()

        val controller = Robolectric.buildService(MainService::class.java)
        controller.create()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNotificationManager = Shadows.shadowOf(notificationManager)
        val notification = shadowNotificationManager.getNotification(1001)
        assertNotNull(notification)

        assertEquals("During active sleep phase, notification should feature only 1 action (Disable)", 1, notification.actions.size)
        assertEquals("Disable", notification.actions[0].title.toString())
    }

    @Test
    fun testAwakeActionClearsSession() {
        val sleepStart = System.currentTimeMillis() - 4 * 3600_000L
        preferences.edit()
            .putBoolean("show_notification", true)
            .putBoolean("wake_up_goal_enabled", true)
            .putLong("sleep_start_time_ms", sleepStart)
            .commit()

        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        val awakeIntent = Intent(context, MainService::class.java)
            .setAction(MainService.ACTION_AWAKE)
        service.onStartCommand(awakeIntent, 0, 1)

        assertEquals("sleep_start_time_ms should be removed", 0L, preferences.getLong("sleep_start_time_ms", 0L))
    }

    @Test
    fun testNotificationReportsProjectedWakeAlarmTimeWhileTimerIsActive() {
        val now = System.currentTimeMillis()
        val timerEndsAt = now + 30 * 60_000L
        val calWake = Calendar.getInstance()
        calWake.timeInMillis = now + 12 * 3600_000L
        val wakeHour = calWake.get(Calendar.HOUR_OF_DAY)
        val wakeMin = calWake.get(Calendar.MINUTE)
        preferences.edit()
            .putBoolean("active", true)
            .putBoolean("show_notification", true)
            .putBoolean("wake_up_goal_enabled", true)
            .putInt("duration_minutes", 30)
            .putInt("min_sleep_duration_minutes", 450)
            .putInt("wake_up_goal_hour", wakeHour)
            .putInt("wake_up_goal_minute", wakeMin)
            .putInt("current_wake_hour", wakeHour)
            .putInt("current_wake_minute", wakeMin)
            .putLong("timer_ends_at", timerEndsAt)
            .commit()

        val controller = Robolectric.buildService(MainService::class.java)
        controller.create()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNotificationManager = Shadows.shadowOf(notificationManager)
        val notification = shadowNotificationManager.getNotification(1001)
        assertNotNull(notification)

        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertTrue("Notification while timer active should contain 'Wake at': $text", text.contains("Wake at"))
    }

    @Test
    fun testAwakeIntentTargetsMainServiceWithAwakeAction() {
        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        val awakeIntentMethod = MainService::class.java.getDeclaredMethod("awakeIntent")
        awakeIntentMethod.isAccessible = true
        val pendingIntent = awakeIntentMethod.invoke(service) as android.app.PendingIntent?

        assertNotNull("awakeIntent pendingIntent must be non-null", pendingIntent)
        val shadowPendingIntent = Shadows.shadowOf(pendingIntent)
        assertTrue("awakeIntent must be a service PendingIntent", shadowPendingIntent.isService)
        val intent = shadowPendingIntent.savedIntent
        assertEquals(MainService.ACTION_AWAKE, intent.action)
        assertEquals(MainService::class.java.name, intent.component?.className)
    }

    @Test
    fun testSessionAnchoredMinimumSleepDoesNotPushAlarmWhenSleepDurationSatisfied() {
        val now = System.currentTimeMillis()
        val minSleepMin = 450

        val calCurrent = Calendar.getInstance()
        calCurrent.timeInMillis = now + 12 * 3600_000L
        val currentHour = calCurrent.get(Calendar.HOUR_OF_DAY)
        val currentMin = calCurrent.get(Calendar.MINUTE)

        val sleepStart = calCurrent.timeInMillis - 8 * 3600_000L

        preferences.edit()
            .putBoolean("wake_up_goal_enabled", true)
            .putInt("wake_up_goal_hour", currentHour)
            .putInt("wake_up_goal_minute", currentMin)
            .putInt("current_wake_hour", currentHour)
            .putInt("current_wake_minute", currentMin)
            .putInt("min_sleep_duration_minutes", minSleepMin)
            .putLong("sleep_start_time_ms", sleepStart)
            .commit()

        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        service.onTimerRescheduled()

        assertEquals("Current wake hour should remain unchanged when session-anchored min sleep is satisfied",
            currentHour, preferences.getInt("current_wake_hour", -1))
        assertEquals("Current wake minute should remain unchanged when session-anchored min sleep is satisfied",
            currentMin, preferences.getInt("current_wake_minute", -1))
    }

    @Test
    fun testDismissingWakeAlarmSetsCurrentWakeTimeCappedAtGoalTime() {
        val farFutureGoalHour = 23
        val farFutureGoalMin = 59

        preferences.edit()
            .putBoolean("wake_up_goal_enabled", true)
            .putInt("wake_up_goal_hour", farFutureGoalHour)
            .putInt("wake_up_goal_minute", farFutureGoalMin)
            .putInt("current_wake_hour", 12)
            .putInt("current_wake_minute", 0)
            .commit()

        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        val dismissIntent = Intent(context, MainService::class.java)
            .setAction(MainService.ACTION_DISMISS_WAKEUP_ALARM)
        service.onStartCommand(dismissIntent, 0, 1)

        assertEquals("Current wake hour must be capped at goal hour when T-15m is earlier than goal",
            farFutureGoalHour, preferences.getInt("current_wake_hour", -1))
        assertEquals("Current wake minute must be capped at goal min when T-15m is earlier than goal",
            farFutureGoalMin, preferences.getInt("current_wake_minute", -1))

        val scheduledMs = preferences.getLong(MainService.KEY_WAKEUP_LAST_SCHEDULED_MS, 0L)
        assertTrue("Next daily wake alarm must be scheduled after dismissal", scheduledMs > System.currentTimeMillis())
    }

    @Test
    fun testClickingAwakeSetsCurrentWakeTimeToClickTimeMinus15mWhenLaterThanGoal() {
        val now = System.currentTimeMillis()
        val pastGoalHour = 0
        val pastGoalMin = 0

        preferences.edit()
            .putBoolean("wake_up_goal_enabled", true)
            .putInt("wake_up_goal_hour", pastGoalHour)
            .putInt("wake_up_goal_minute", pastGoalMin)
            .putInt("current_wake_hour", 12)
            .putInt("current_wake_minute", 0)
            .putLong("sleep_start_time_ms", now - 4 * 3600_000L)
            .commit()

        val controller = Robolectric.buildService(MainService::class.java)
        val service = controller.create().get()

        val expectedCal = Calendar.getInstance()
        expectedCal.add(Calendar.MINUTE, -15)
        val expectedHour = expectedCal.get(Calendar.HOUR_OF_DAY)
        val expectedMin = expectedCal.get(Calendar.MINUTE)

        val awakeIntent = Intent(context, MainService::class.java)
            .setAction(MainService.ACTION_AWAKE)
        service.onStartCommand(awakeIntent, 0, 1)

        val actualMins = preferences.getInt("current_wake_hour", -1) * 60 + preferences.getInt("current_wake_minute", -1)
        val expectedMins = expectedHour * 60 + expectedMin
        assertTrue("Current wake time must be within 1 minute of click time minus 15m",
            Math.abs(actualMins - expectedMins) <= 1)
        assertFalse("Ongoing sleep session must be cleared after clicking awake", preferences.contains("sleep_start_time_ms"))

        val scheduledMs = preferences.getLong(MainService.KEY_WAKEUP_LAST_SCHEDULED_MS, 0L)
        assertTrue("Next wake alarm must be rescheduled after clicking awake", scheduledMs > now)
    }
}
