package com.masselis.tpmsadvanced.feature.background.interfaces

import app.cash.turbine.test
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.Failure
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal class ScanNoticeTest {
    @Test
    fun `persistent failures escalate after ten minutes and clear on recovery`() = runTest {
        val status = MutableStateFlow<ScanStatus>(ScanStatus.Retrying(2, testScheduler.timeSource.markNow()))
        status.notices().test {
            assertFalse(awaitItem()!!.urgent)
            delay(10.minutes - 1.seconds)
            expectNoEvents()
            assertTrue(awaitItem()!!.urgent)
            status.value = ScanStatus.Scanning
            assertNull(awaitItem())
        }
    }

    @Test
    fun `joining an existing failure counts the time already spent retrying`() = runTest {
        val since = testScheduler.timeSource.markNow()
        delay(9.minutes)
        MutableStateFlow<ScanStatus>(ScanStatus.Retrying(3, since)).notices().test {
            assertFalse(awaitItem()!!.urgent)
            delay(59.seconds)
            expectNoEvents()
            assertTrue(awaitItem()!!.urgent)
        }
    }

    @Test
    fun `Bluetooth off cancels pending failure escalation`() = runTest {
        val status = MutableStateFlow<ScanStatus>(ScanStatus.Retrying(3, testScheduler.timeSource.markNow()))
        status.notices().test {
            awaitItem()
            status.value = ScanStatus.WaitingForBluetooth
            val notice = awaitItem()!!
            assertFalse(notice.urgent)
            assertFalse(notice.canRestart)
            delay(20.minutes)
            expectNoEvents()
        }
    }

    @Test
    fun `permission and unsupported errors do not suggest restarting`() = runTest {
        val status = MutableStateFlow<ScanStatus>(ScanStatus.Failed(SecurityException()))
        status.notices().test {
            val permissions = awaitItem()!!
            assertTrue(permissions.message.contains("permission"))
            assertTrue(permissions.urgent)
            assertFalse(permissions.canRestart)
            status.value = ScanStatus.Failed(Failure.Scan(4))
            assertFalse(awaitItem()!!.canRestart)
            status.value = ScanStatus.Failed(IllegalStateException())
            assertTrue(awaitItem()!!.canRestart)
        }
    }
}
