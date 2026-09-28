package com.bas080.autosleepdroid

import java.util.Locale
import java.util.regex.Pattern

object DurationUtils {

    private const val MINUTES_PER_HOUR = 60
    private const val MINUTES_PER_HOUR_DOUBLE = 60.0
    private const val MINUTES_PER_HOUR_LONG = 60L

    enum class DefaultUnit {
        MINUTES,
        HOURS
    }

    fun formatDurationString(totalMinutes: Int): String {
        if (totalMinutes < MINUTES_PER_HOUR) {
            return "${totalMinutes}m"
        }
        val hours = totalMinutes / MINUTES_PER_HOUR
        val mins = totalMinutes % MINUTES_PER_HOUR
        return if (mins == 0) {
            "${hours}h"
        } else {
            "${hours}h ${mins}m"
        }
    }

    fun parseDurationMinutes(input: String?, defaultUnit: DefaultUnit = DefaultUnit.MINUTES): Int {
        if (input.isNullOrBlank()) return -1
        val s = input.trim().replace("\\s+".toRegex(), "").lowercase(Locale.US)

        return try {
            parsePlainNumber(s, defaultUnit)
                ?: parseHoursMinutesPattern(s)
                ?: parseSecondsPattern(s)
                ?: -1
        } catch (ignored: NumberFormatException) {
            -1
        }
    }

    private fun parsePlainNumber(s: String, defaultUnit: DefaultUnit): Int? {
        if (!s.matches("^[0-9]+([.,][0-9]+)?$".toRegex())) return null
        val valNum = s.replace(',', '.').toDouble()
        val totalMins = if (defaultUnit == DefaultUnit.HOURS) {
            Math.round(valNum * MINUTES_PER_HOUR_DOUBLE)
        } else {
            Math.round(valNum)
        }
        return if (valNum > 0 && totalMins in 1..Int.MAX_VALUE) totalMins.toInt() else null
    }

    private fun parseHoursMinutesPattern(s: String): Int? {
        val hoursOnly = Pattern.compile("^([0-9]+([.,][0-9]+)?)h$").matcher(s)
        val minsOnly = Pattern.compile("^([0-9]+([.,][0-9]+)?)m$").matcher(s)
        val hoursMins = Pattern.compile("^([0-9]+)h([0-9]+)m$").matcher(s)

        val totalMins = when {
            hoursOnly.matches() -> {
                Math.round(hoursOnly.group(1)!!.replace(',', '.').toDouble() * MINUTES_PER_HOUR_DOUBLE)
            }
            minsOnly.matches() -> Math.round(minsOnly.group(1)!!.replace(',', '.').toDouble())
            hoursMins.matches() -> {
                hoursMins.group(1)!!.toLong() * MINUTES_PER_HOUR_LONG + hoursMins.group(2)!!.toLong()
            }
            else -> return null
        }
        return if (totalMins in 1..Int.MAX_VALUE) totalMins.toInt() else null
    }

    private fun parseSecondsPattern(s: String): Int? {
        val minsSecs = Pattern.compile("^([0-9]+)m[0-9]+s$").matcher(s)
        val hoursSecs = Pattern.compile("^([0-9]+)h[0-9]+s$").matcher(s)
        val hoursMinsSecs = Pattern.compile("^([0-9]+)h([0-9]+)m[0-9]+s$").matcher(s)

        val totalMins = when {
            minsSecs.matches() -> minsSecs.group(1)!!.toLong()
            hoursSecs.matches() -> hoursSecs.group(1)!!.toLong() * MINUTES_PER_HOUR_LONG
            hoursMinsSecs.matches() -> {
                hoursMinsSecs.group(1)!!.toLong() * MINUTES_PER_HOUR_LONG + hoursMinsSecs.group(2)!!.toLong()
            }
            else -> return null
        }
        return if (totalMins in 1..Int.MAX_VALUE) totalMins.toInt() else null
    }
}
