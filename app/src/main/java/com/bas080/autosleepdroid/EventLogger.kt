package com.bas080.autosleepdroid

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Collections
import java.util.Date
import java.util.Locale

object EventLogger {
    const val LEVEL_LOW: Int = 0
    const val LEVEL_NORMAL: Int = 1
    const val LEVEL_HIGH: Int = 2

    private const val LOG_FILE_NAME = "event_logs.txt"
    private const val PREF_NAME = "event_logger"
    private const val KEY_LOGS = "logs"
    private const val MAX_LOGS = 500
    private val events = ArrayList<String>()
    private var loaded = false
    @Volatile
    private var listener: Listener? = null
    @Volatile
    private var appContext: Context? = null

    fun interface Listener {
        fun onEventLogged(event: String)
    }

    @Synchronized
    fun setListener(l: Listener?) {
        listener = l
    }

    @Synchronized
    fun log(context: Context?, level: Int, message: String) {
        if (context != null) {
            appContext = context.applicationContext
        }
        val targetContext = context ?: appContext
        ensureLoaded(targetContext)

        val timestamp = SimpleDateFormat("M/d HH:mm:ss", Locale.US).format(Date())
        val marker = when (level) {
            LEVEL_LOW -> '\u0000'
            LEVEL_HIGH -> '\u0002'
            else -> '\u0001'
        }

        val line = "$timestamp $marker$message"

        events.add(line)
        val exceeded = events.size > MAX_LOGS
        if (exceeded) {
            events.removeAt(0)
        }

        if (targetContext != null) {
            val appCtx = targetContext.applicationContext
            if (exceeded) {
                rewriteLogs(appCtx)
            } else {
                appendLogLine(appCtx, line)
            }
        }

        val currentListener = listener
        if (currentListener != null) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                currentListener.onEventLogged(line)
            } else {
                Handler(Looper.getMainLooper()).post { currentListener.onEventLogged(line) }
            }
        }
    }

    @Synchronized
    fun log(context: Context?, message: String) {
        log(context, LEVEL_NORMAL, message)
    }

    @Synchronized
    fun log(level: Int, message: String) {
        log(null, level, message)
    }

    @Synchronized
    fun log(message: String) {
        log(null, LEVEL_NORMAL, message)
    }

    @Synchronized
    fun getEvents(context: Context?): List<String> {
        ensureLoaded(context)
        return Collections.unmodifiableList(ArrayList(events))
    }

    fun isDarkMode(context: Context?): Boolean {
        val ctx = context ?: appContext ?: return false
        val resources = ctx.resources ?: return false
        val config = resources.configuration ?: return false
        val currentNightMode = config.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    fun formatColoredEvent(line: String?): CharSequence {
        return formatColoredEvent(null, line)
    }

    fun formatColoredEvent(context: Context?, line: String?): CharSequence {
        if (line.isNullOrEmpty()) {
            return ""
        }

        val darkMode = isDarkMode(context)

        val spaceIdx = line.indexOf(' ')
        val secondSpaceIdx = if (spaceIdx != -1) line.indexOf(' ', spaceIdx + 1) else -1
        val timestampEnd = if (secondSpaceIdx != -1) secondSpaceIdx else (if (spaceIdx != -1) spaceIdx else 0)

        var level = LEVEL_NORMAL
        val timestamp = if (timestampEnd > 0) line.substring(0, timestampEnd) else ""
        var rawMessage = if (timestampEnd < line.length) line.substring(timestampEnd) else ""

        if (rawMessage.contains("\u0000")) {
            level = LEVEL_LOW
            rawMessage = rawMessage.replace("\u0000", "")
        } else if (rawMessage.contains("\u0002")) {
            level = LEVEL_HIGH
            rawMessage = rawMessage.replace("\u0002", "")
        } else if (rawMessage.contains("\u0001")) {
            level = LEVEL_NORMAL
            rawMessage = rawMessage.replace("\u0001", "")
        }

        val displayString = if (timestamp.isEmpty()) rawMessage.trim() else (timestamp + rawMessage)
        val spannable = SpannableString(displayString)

        val timestampColor = if (darkMode) -0x777778 else -0x666667
        if (timestamp.isNotEmpty() && timestamp.length <= displayString.length) {
            spannable.setSpan(ForegroundColorSpan(timestampColor), 0, timestamp.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val messageStart = if (timestamp.isEmpty()) 0 else timestamp.length

        val textColor: Int
        var isBold = false

        if (darkMode) {
            if (level == LEVEL_LOW) {
                textColor = -0x555556
            } else if (level == LEVEL_HIGH) {
                textColor = -0x1
                isBold = true
            } else {
                textColor = -0x222223
            }
        } else {
            if (level == LEVEL_LOW) {
                textColor = -0x777778
            } else if (level == LEVEL_HIGH) {
                textColor = -0x1000000
                isBold = true
            } else {
                textColor = -0xbbbbbc
            }
        }

        if (messageStart < displayString.length) {
            spannable.setSpan(ForegroundColorSpan(textColor), messageStart, displayString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (isBold) {
                spannable.setSpan(StyleSpan(Typeface.BOLD), messageStart, displayString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        return spannable
    }

    @Synchronized
    fun clear(context: Context?) {
        events.clear()
        val targetContext = context ?: appContext
        if (targetContext != null) {
            val appCtx = targetContext.applicationContext
            val file = getLogFile(appCtx)
            if (file.exists()) {
                file.delete()
            }
            val prefs = appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_LOGS).commit()
        }
        if (context != null) {
            appContext = context.applicationContext
        } else {
            appContext = null
        }
        loaded = false
    }

    private fun getLogFile(context: Context): File {
        return File(context.filesDir, LOG_FILE_NAME)
    }

    private fun ensureLoaded(context: Context?) {
        if (!loaded && context != null) {
            val appCtx = context.applicationContext
            val file = getLogFile(appCtx)
            if (file.exists()) {
                file.useLines { lines ->
                    for (line in lines) {
                        if (line.trim().isNotEmpty()) {
                            events.add(line)
                        }
                    }
                }
            } else {
                val prefs = appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                val raw = prefs.getString(KEY_LOGS, null)
                if (!raw.isNullOrEmpty()) {
                    val lines = raw.split("\n")
                    for (line in lines) {
                        if (line.trim().isNotEmpty()) {
                            events.add(line)
                        }
                    }
                    prefs.edit().remove(KEY_LOGS).commit()
                }
            }

            while (events.size > MAX_LOGS) {
                events.removeAt(0)
            }

            if (!file.exists() && events.isNotEmpty()) {
                rewriteLogs(appCtx)
            }

            loaded = true
        }
    }

    private fun appendLogLine(context: Context, line: String) {
        val file = getLogFile(context)
        file.appendText(line + "\n")
    }

    private fun rewriteLogs(context: Context) {
        val file = getLogFile(context)
        if (events.isEmpty()) {
            if (file.exists()) {
                file.delete()
            }
        } else {
            val content = events.joinToString("\n", postfix = "\n")
            file.writeText(content)
        }
    }
}
