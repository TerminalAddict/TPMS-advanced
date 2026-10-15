package com.masselis.tpmsadvanced.feature.background.interfaces

import android.app.ApplicationExitInfo

internal data class MonitoringInterruption(
    val session: MonitoringSession,
    val cause: Cause,
    val stoppedAt: Long? = null,
) {
    enum class Cause(val explanation: String) {
        LOW_MEMORY("Android reports that it stopped TyrePing to free memory."),
        CRASH("Android reports that TyrePing crashed."),
        NOT_RESPONDING("Android reports that TyrePing stopped responding."),
        PERMISSIONS("Android reports that TyrePing's permissions changed."),
        STOP_REQUEST("Android reports a stop request, such as Force stop or Stop in Android's active-apps screen."),
        REBOOT("The phone restarted since this monitoring session began."),
        UPDATE("TyrePing was updated since this monitoring session began."),
        SYSTEM("Android reports that it ended TyrePing's process."),
        UNKNOWN("Android did not provide a reason. The monitoring service is no longer running."),
    }
}

internal data class MonitoringProcessExit(val pid: Int, val processName: String, val at: Long, val reason: Int)

/** Match the original process, not a later widget/broadcast process or an unrelated previous crash. */
internal fun monitoringInterruption(
    session: MonitoringSession?,
    running: Boolean,
    packageName: String,
    bootCount: Int,
    appUpdatedAt: Long,
    exits: List<MonitoringProcessExit>,
): MonitoringInterruption? {
    if (session == null || running) return null
    val exit = exits.filter {
        it.pid == session.processId && it.processName == packageName && it.at >= session.startedAt
    }.minByOrNull { it.at }
    return when {
        session.bootCount >= 0 && bootCount >= 0 && session.bootCount != bootCount ->
            MonitoringInterruption(session, MonitoringInterruption.Cause.REBOOT)
        appUpdatedAt > session.appUpdatedAt -> MonitoringInterruption(session, MonitoringInterruption.Cause.UPDATE)
        else -> MonitoringInterruption(session, exit?.reason.toMonitoringCause(), exit?.at)
    }
}

private fun Int?.toMonitoringCause(): MonitoringInterruption.Cause = when (this) {
    ApplicationExitInfo.REASON_LOW_MEMORY -> MonitoringInterruption.Cause.LOW_MEMORY
    ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE,
    ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> MonitoringInterruption.Cause.CRASH
    ApplicationExitInfo.REASON_ANR -> MonitoringInterruption.Cause.NOT_RESPONDING
    ApplicationExitInfo.REASON_PERMISSION_CHANGE -> MonitoringInterruption.Cause.PERMISSIONS
    ApplicationExitInfo.REASON_USER_REQUESTED -> MonitoringInterruption.Cause.STOP_REQUEST
    null, ApplicationExitInfo.REASON_UNKNOWN -> MonitoringInterruption.Cause.UNKNOWN
    else -> MonitoringInterruption.Cause.SYSTEM
}
