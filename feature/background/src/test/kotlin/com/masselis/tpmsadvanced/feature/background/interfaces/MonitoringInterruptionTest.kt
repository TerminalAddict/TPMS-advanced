package com.masselis.tpmsadvanced.feature.background.interfaces

import android.app.ApplicationExitInfo
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class MonitoringInterruptionTest {
    private val session = MonitoringSession(UUID.randomUUID(), "Trailer", false, 1_000, 42, 3, 500)
    private val packageName = "nz.paulwillard.tyreping.debug"
    private val crash = MonitoringProcessExit(42, packageName, 2_000, ApplicationExitInfo.REASON_CRASH)

    @Test fun `fresh installation or deliberate Stop has no pending warning`() {
        assertNull(monitoringInterruption(null, false, packageName, 3, 500, listOf(crash)))
    }

    @Test fun `a running service including a recovered sticky watch does not show a warning`() {
        assertNull(monitoringInterruption(session, true, packageName, 3, 500, listOf(crash)))
    }

    @Test fun `manual monitoring interrupted without an exit report has unknown cause`() {
        assertEquals(MonitoringInterruption(session, MonitoringInterruption.Cause.UNKNOWN), detect())
    }

    @Test fun `an automatic watch waiting for its accessory also needs recovery`() {
        val waiting = session.copy(automatic = true)
        assertEquals(waiting, monitoringInterruption(waiting, false, packageName, 3, 500, emptyList())?.session)
    }

    @Test fun `reports the original process crash time rather than a later app death`() {
        val later = crash.copy(at = 3_000, reason = ApplicationExitInfo.REASON_LOW_MEMORY)
        assertEquals(MonitoringInterruption(session, MonitoringInterruption.Cause.CRASH, 2_000),
            detect(listOf(later, crash)))
    }

    @Test fun `ignores old deaths other processes and other pids`() {
        val unrelated = listOf(crash.copy(at = 999), crash.copy(pid = 77),
            crash.copy(processName = "$packageName:worker"))
        assertEquals(MonitoringInterruption(session, MonitoringInterruption.Cause.UNKNOWN), detect(unrelated))
    }

    @Test fun `restarting a session does not inherit a crash from its earlier session`() {
        val restarted = session.copy(startedAt = 4_000)
        assertEquals(MonitoringInterruption.Cause.UNKNOWN,
            monitoringInterruption(restarted, false, packageName, 3, 500, listOf(crash))?.cause)
    }

    @Test fun `reboot cannot be mistaken for a reused pid after boot`() {
        assertEquals(MonitoringInterruption(session, MonitoringInterruption.Cause.REBOOT),
            monitoringInterruption(session, false, packageName, 4, 500, listOf(crash)))
    }

    @Test fun `unavailable boot count does not invent a reboot`() {
        assertEquals(MonitoringInterruption.Cause.UNKNOWN,
            monitoringInterruption(session.copy(bootCount = -1), false, packageName, 4, 500, emptyList())?.cause)
    }

    @Test fun `app update explains a stopped session even on older Android stop request reports`() {
        assertEquals(MonitoringInterruption(session, MonitoringInterruption.Cause.UPDATE),
            monitoringInterruption(session, false, packageName, 3, 3_000,
                listOf(crash.copy(reason = ApplicationExitInfo.REASON_USER_REQUESTED))))
    }

    @Test fun `force stop is not reported as a battery management kill`() {
        assertEquals(MonitoringInterruption.Cause.STOP_REQUEST, cause(ApplicationExitInfo.REASON_USER_REQUESTED))
    }

    @Test fun `low memory permissions and unresponsive app have different explanations`() {
        assertEquals(MonitoringInterruption.Cause.LOW_MEMORY, cause(ApplicationExitInfo.REASON_LOW_MEMORY))
        assertEquals(MonitoringInterruption.Cause.PERMISSIONS, cause(ApplicationExitInfo.REASON_PERMISSION_CHANGE))
        assertEquals(MonitoringInterruption.Cause.NOT_RESPONDING, cause(ApplicationExitInfo.REASON_ANR))
    }

    @Test fun `native and initialization crashes are recognised`() {
        assertEquals(MonitoringInterruption.Cause.CRASH, cause(ApplicationExitInfo.REASON_CRASH_NATIVE))
        assertEquals(MonitoringInterruption.Cause.CRASH, cause(ApplicationExitInfo.REASON_INITIALIZATION_FAILURE))
    }

    @Test fun `unspecified system exit never claims a specific battery cause`() {
        assertEquals(MonitoringInterruption.Cause.SYSTEM, cause(ApplicationExitInfo.REASON_OTHER))
        assertEquals(MonitoringInterruption.Cause.SYSTEM, cause(ApplicationExitInfo.REASON_SIGNALED))
        assertEquals(MonitoringInterruption.Cause.UNKNOWN, cause(ApplicationExitInfo.REASON_UNKNOWN))
    }

    private fun cause(reason: Int) = detect(listOf(crash.copy(reason = reason)))?.cause
    private fun detect(exits: List<MonitoringProcessExit> = emptyList()) =
        monitoringInterruption(session, false, packageName, 3, 500, exits)
}
