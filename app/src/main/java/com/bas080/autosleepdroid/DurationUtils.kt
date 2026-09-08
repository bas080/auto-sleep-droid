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
        if (input == null) {
            return -1
        }
        val raw = input.trim()
        if (raw.isEmpty()) {
            return -1
        }
        val s = raw.replace("\\s+".toRegex(), "").lowercase(Locale.US)

        try {
            if (s.matches("^[0-9]+([.,][0-9]+)?$".toRegex())) {
                val valNum = s.replace(',', '.').toDouble()
                if (valNum <= 0) {
                    return -1
                }
                val totalMins = if (defaultUnit == DefaultUnit.HOURS) Math.round(valNum * 60.0) else Math.round(valNum)
                return if (totalMins in 1..Int.MAX_VALUE) totalMins.toInt() else -1
            }

            var m = Pattern.compile("^([0-9]+([.,][0-9]+)?)h$").matcher(s)
            if (m.matches()) {
                val hours = m.group(1)!!.replace(',', '.').toDouble()
                val total = Math.round(hours * 60.0)
                return if (total in 1..Int.MAX_VALUE) total.toInt() else -1
            }

            m = Pattern.compile("^([0-9]+([.,][0-9]+)?)m$").matcher(s)
            if (m.matches()) {
                val mins = m.group(1)!!.replace(',', '.').toDouble()
                val total = Math.round(mins)
                return if (total in 1..Int.MAX_VALUE) total.toInt() else -1
            }

            m = Pattern.compile("^([0-9]+)h([0-9]+)m$").matcher(s)
            if (m.matches()) {
                val hours = m.group(1)!!.toLong()
                val mins = m.group(2)!!.toLong()
                val total = hours * 60L + mins
                return if (total in 1..Int.MAX_VALUE) total.toInt() else -1
            }

            m = Pattern.compile("^([0-9]+)m[0-9]+s$").matcher(s)
            if (m.matches()) {
                val mins = m.group(1)!!.toLong()
                return if (mins in 1..Int.MAX_VALUE) mins.toInt() else -1
            }

            m = Pattern.compile("^([0-9]+)h[0-9]+s$").matcher(s)
            if (m.matches()) {
                val hours = m.group(1)!!.toLong()
                val total = hours * 60L
                return if (total in 1..Int.MAX_VALUE) total.toInt() else -1
            }

            m = Pattern.compile("^([0-9]+)h([0-9]+)m[0-9]+s$").matcher(s)
            if (m.matches()) {
                val hours = m.group(1)!!.toLong()
                val mins = m.group(2)!!.toLong()
                val total = hours * 60L + mins
                return if (total in 1..Int.MAX_VALUE) total.toInt() else -1
            }
        } catch (ignored: NumberFormatException) {
            return -1
        }

        return -1
    }
}
