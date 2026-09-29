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
import java.util.Collections
import java.util.Date
import java.util.Locale

object EventLogger {
    const val LEVEL_LOW: Int = 0
    const val LEVEL_NORMAL: Int = 1
    const val LEVEL_HIGH: Int = 2

    @Volatile
    private var listener: Listener? = null

    @Volatile
    internal var appContext: Context? = null

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

        val timestamp = SimpleDateFormat("M/d HH:mm:ss", Locale.US).format(Date())
        val marker = when (level) {
            LEVEL_LOW -> '\u0000'
            LEVEL_HIGH -> '\u0002'
            else -> '\u0001'
        }

        val line = "$timestamp $marker$message"
        EventLogStorage.writeLogToFile(targetContext, line)

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
        if (context != null) {
            appContext = context.applicationContext
        }
        val targetContext = context ?: appContext
        return EventLogStorage.getEvents(targetContext)
    }

    fun isDarkMode(context: Context?): Boolean {
        val targetContext = context ?: appContext
        return EventLogFormatter.isDarkMode(targetContext)
    }

    fun formatColoredEvent(line: String?): CharSequence {
        return formatColoredEvent(null, line)
    }

    fun formatColoredEvent(context: Context?, line: String?): CharSequence {
        val targetContext = context ?: appContext
        return EventLogFormatter.formatColoredEvent(targetContext, line)
    }

    @Synchronized
    fun clear(context: Context?) {
        val targetContext = context ?: appContext
        EventLogStorage.clear(targetContext)
        if (context != null) {
            appContext = context.applicationContext
        } else {
            appContext = null
        }
    }
}

internal object EventLogStorage {
    private const val LOG_FILE_NAME = "event_logs.txt"
    private const val MAX_LOG_FILE_BYTES = 1_000_000L
    private const val TRIM_TO_LOGS = 50

    fun writeLogToFile(context: Context?, line: String) {
        if (context == null) return
        val appCtx = context.applicationContext
        val file = getLogFile(appCtx)
        if (file.exists() && file.length() >= MAX_LOG_FILE_BYTES) {
            pruneFile(file)
        }
        file.appendText(line + "\n")
    }

    fun getEvents(context: Context?): List<String> {
        val appCtx = context?.applicationContext ?: return emptyList()
        val file = getLogFile(appCtx)
        return if (file.exists()) {
            val lines = file.readLines().filter { it.trim().isNotEmpty() }
            Collections.unmodifiableList(lines)
        } else {
            emptyList()
        }
    }

    fun clear(context: Context?) {
        if (context != null) {
            val appCtx = context.applicationContext
            val file = getLogFile(appCtx)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    private fun pruneFile(file: File) {
        val lines = file.readLines().filter { it.trim().isNotEmpty() }
        val trimmed = lines.takeLast(TRIM_TO_LOGS)
        val content = trimmed.joinToString("\n", postfix = "\n")
        file.writeText(content)
    }

    private fun getLogFile(context: Context): File {
        return File(context.filesDir, LOG_FILE_NAME)
    }
}

internal object EventLogFormatter {
    fun isDarkMode(context: Context?): Boolean {
        if (context == null) return false
        val config = context.resources?.configuration
        val currentNightMode = (config?.uiMode ?: 0) and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    fun formatColoredEvent(context: Context?, line: String?): CharSequence {
        if (line.isNullOrEmpty()) return ""

        val darkMode = isDarkMode(context)
        val (timestamp, level, rawMessage) = extractLogComponents(line)

        val displayString = if (timestamp.isEmpty()) rawMessage.trim() else (timestamp + rawMessage)
        val spannable = SpannableString(displayString)

        applyTimestampStyle(spannable, timestamp, displayString.length, darkMode)

        val messageStart = if (timestamp.isEmpty()) 0 else timestamp.length
        if (messageStart < displayString.length) {
            applyMessageStyle(spannable, messageStart, displayString.length, level, darkMode)
        }

        return spannable
    }

    private fun applyTimestampStyle(
        spannable: SpannableString,
        timestamp: String,
        maxLen: Int,
        darkMode: Boolean
    ) {
        val timestampColor = if (darkMode) -0x777778 else -0x666667
        if (timestamp.isNotEmpty() && timestamp.length <= maxLen) {
            val spanFlag = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            spannable.setSpan(ForegroundColorSpan(timestampColor), 0, timestamp.length, spanFlag)
        }
    }

    private data class LogComponents(val timestamp: String, val level: Int, val message: String)

    private fun extractLogComponents(line: String): LogComponents {
        val spaceIdx = line.indexOf(' ')
        val secondSpaceIdx = if (spaceIdx != -1) line.indexOf(' ', spaceIdx + 1) else -1
        val timestampEnd = when {
            secondSpaceIdx != -1 -> secondSpaceIdx
            spaceIdx != -1 -> spaceIdx
            else -> 0
        }

        val timestamp = if (timestampEnd > 0) line.substring(0, timestampEnd) else ""
        val rawMessageWithMarker = if (timestampEnd < line.length) line.substring(timestampEnd) else ""

        val (level, cleanMessage) = parseMessageLevel(rawMessageWithMarker)
        return LogComponents(timestamp, level, cleanMessage)
    }

    private fun parseMessageLevel(rawMessage: String): Pair<Int, String> {
        return when {
            rawMessage.contains("\u0000") -> Pair(EventLogger.LEVEL_LOW, rawMessage.replace("\u0000", ""))
            rawMessage.contains("\u0002") -> Pair(EventLogger.LEVEL_HIGH, rawMessage.replace("\u0002", ""))
            rawMessage.contains("\u0001") -> Pair(EventLogger.LEVEL_NORMAL, rawMessage.replace("\u0001", ""))
            else -> Pair(EventLogger.LEVEL_NORMAL, rawMessage)
        }
    }

    private fun applyMessageStyle(
        spannable: SpannableString,
        start: Int,
        end: Int,
        level: Int,
        darkMode: Boolean
    ) {
        val textColor: Int
        var isBold = false

        if (darkMode) {
            when (level) {
                EventLogger.LEVEL_LOW -> textColor = -0x555556
                EventLogger.LEVEL_HIGH -> {
                    textColor = -0x1
                    isBold = true
                }
                else -> textColor = -0x222223
            }
        } else {
            when (level) {
                EventLogger.LEVEL_LOW -> textColor = -0x777778
                EventLogger.LEVEL_HIGH -> {
                    textColor = -0x1000000
                    isBold = true
                }
                else -> textColor = -0xbbbbbc
            }
        }

        spannable.setSpan(ForegroundColorSpan(textColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (isBold) {
            spannable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
