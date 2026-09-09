package com.bas080.autosleepdroid

enum class SessionPhase {
    IDLE,
    INITIATION_AND_ACTIVE_SLEEP,
    PRE_ALARM_WINDOW,
    ALARM
}

fun getSessionPhase(
    now: Long,
    currentWakeTime: Long,
    minSleepDuration: Long,
    isSessionOngoing: Boolean
): SessionPhase {
    val windowStart = currentWakeTime - (minSleepDuration * 1.2).toLong()
    val preAlarmStart = currentWakeTime - (minSleepDuration * 0.5).toLong()

    if (isSessionOngoing && now >= currentWakeTime) {
        return SessionPhase.ALARM
    }

    if (now >= preAlarmStart && now < currentWakeTime) {
        return SessionPhase.PRE_ALARM_WINDOW
    }

    if (now >= windowStart && now < preAlarmStart) {
        return SessionPhase.INITIATION_AND_ACTIVE_SLEEP
    }

    return SessionPhase.IDLE
}
