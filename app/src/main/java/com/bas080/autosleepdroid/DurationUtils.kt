@file:Suppress("MagicNumber")

package com.bas080.autosleepdroid

import java.util.Locale
import java.util.regex.Pattern

object DurationUtils {

    enum class DefaultUnit {
        MINUTES,
        HOURS
    }

    fun formatDurationString(totalMinutes: Int): String {
        if (totalMinutes < 60) {
            return "${totalMinutes}m"
        }
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
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
        val totalMins = if (defaultUnit == DefaultUnit.HOURS) Math.round(valNum * 60.0) else Math.round(valNum)
        return if (valNum > 0 && totalMins in 1..Int.MAX_VALUE) totalMins.toInt() else null
    }

    private fun parseHoursMinutesPattern(s: String): Int? {
        val hoursOnly = Pattern.compile("^([0-9]+([.,][0-9]+)?)h$").matcher(s)
        val minsOnly = Pattern.compile("^([0-9]+([.,][0-9]+)?)m$").matcher(s)
        val hoursMins = Pattern.compile("^([0-9]+)h([0-9]+)m$").matcher(s)

        val totalMins = when {
            hoursOnly.matches() -> Math.round(hoursOnly.group(1)!!.replace(',', '.').toDouble() * 60.0)
            minsOnly.matches() -> Math.round(minsOnly.group(1)!!.replace(',', '.').toDouble())
            hoursMins.matches() -> hoursMins.group(1)!!.toLong() * 60L + hoursMins.group(2)!!.toLong()
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
            hoursSecs.matches() -> hoursSecs.group(1)!!.toLong() * 60L
            hoursMinsSecs.matches() -> hoursMinsSecs.group(1)!!.toLong() * 60L + hoursMinsSecs.group(2)!!.toLong()
            else -> return null
        }
        return if (totalMins in 1..Int.MAX_VALUE) totalMins.toInt() else null
    }
}
