package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import app.cash.turbine.test
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.Failure
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
internal class RecoveringScanTest {
    private val enabled = MutableStateFlow(true)
    private val failures = MutableSharedFlow<Exception>()
    private val statuses = mutableListOf<ScanStatus>()
    private var starts = 0
    private var stops = 0

    private fun TestScope.scanner() = flow {
        starts++
        try {
            emit(ScanEvent.Started)
            emit(ScanEvent.Reading(starts))
            failures.collect { throw it }
        } finally {
            stops++
        }
    }.recoveringScan(enabled, testScheduler.timeSource) { statuses += it }

    @Test
    fun `Bluetooth off waits without starting and resumes after enabling`() = runTest {
        enabled.value = false
        scanner().test {
            runCurrent()
            assertEquals(ScanStatus.WaitingForBluetooth, statuses.last())
            delay(5.minutes)
            assertEquals(0, starts)
            enabled.value = true
            assertEquals(1, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, stops)
        assertEquals(ScanStatus.Idle, statuses.last())
    }

    @Test
    fun `turning Bluetooth off cancels the active scan and on restarts it`() = runTest {
        scanner().test {
            assertEquals(1, awaitItem())
            enabled.value = false
            runCurrent()
            assertEquals(1, stops)
            assertEquals(ScanStatus.WaitingForBluetooth, statuses.last())
            delay(1.minutes)
            assertEquals(1, starts)
            enabled.value = true
            assertEquals(2, awaitItem())
        }
    }

    @Test
    fun `refused starts back off and keep their original failure time`() = runTest {
        scanner().test {
            awaitItem()
            var firstRetry: ScanStatus.Retrying? = null
            listOf(5, 10, 20, 40, 60, 60).forEach { seconds ->
                val before = testScheduler.currentTime
                failures.emit(Failure.Scan(2))
                runCurrent()
                val retry = assertIs<ScanStatus.Retrying>(statuses.last())
                assertEquals(2, retry.reason)
                if (firstRetry == null) firstRetry = retry else assertSame(firstRetry.since, retry.since)
                awaitItem()
                assertEquals(seconds * 1000L, testScheduler.currentTime - before)
                assertIs<ScanStatus.Retrying>(statuses.last())
            }
            assertEquals(7, starts)
        }
    }

    @Test
    fun `null scanner while Bluetooth is on retries without waiting for a toggle`() = runTest {
        scanner().test {
            awaitItem()
            failures.emit(Failure.ScannerIsNull(12))
            runCurrent()
            assertEquals(null, assertIs<ScanStatus.Retrying>(statuses.last()).reason)
            assertEquals(2, awaitItem())
            assertEquals(5000L, testScheduler.currentTime)
        }
    }

    @Test
    fun `only a platform start that holds clears retry status`() = runTest {
        scanner().test {
            awaitItem()
            failures.emit(Failure.Scan(3))
            awaitItem()
            delay(9.seconds)
            assertIs<ScanStatus.Retrying>(statuses.last())
            delay(1.seconds)
            runCurrent()
            assertEquals(ScanStatus.Scanning, statuses.last())
        }
    }

    @Test
    fun `a healthy scan resets backoff`() = runTest {
        scanner().test {
            awaitItem()
            repeat(3) {
                failures.emit(Failure.Scan(3))
                awaitItem()
            }
            delay(2.minutes)
            val before = testScheduler.currentTime
            failures.emit(Failure.Scan(3))
            awaitItem()
            assertEquals(5000L, testScheduler.currentTime - before)
        }
    }

    @Test
    fun `Bluetooth toggles cancel backoff and reset it`() = runTest {
        scanner().test {
            awaitItem()
            repeat(2) {
                failures.emit(Failure.Scan(3))
                awaitItem()
            }
            failures.emit(Failure.Scan(3))
            enabled.value = false
            runCurrent()
            assertEquals(ScanStatus.WaitingForBluetooth, statuses.last())
            enabled.value = true
            assertEquals(4, awaitItem())
            val before = testScheduler.currentTime
            failures.emit(Failure.Scan(3))
            awaitItem()
            assertEquals(5000L, testScheduler.currentTime - before)
        }
    }

    @Test
    fun `cancelling while waiting to retry never starts another scan`() = runTest {
        scanner().test {
            awaitItem()
            failures.emit(Failure.Scan(6))
            runCurrent()
            cancelAndIgnoreRemainingEvents()
        }
        delay(2.minutes)
        assertEquals(1, starts)
        assertEquals(1, stops)
        assertEquals(ScanStatus.Idle, statuses.last())
    }

    @Test
    fun `unsupported scan permission and unrelated failures are not retried`() = runTest {
        listOf(Failure.Scan(4), SecurityException("permission revoked"), IllegalArgumentException("bad config"))
            .forEach { failure ->
                val before = starts
                scanner().test {
                    awaitItem()
                    failures.emit(failure)
                    val error = awaitError()
                    assertEquals(failure.javaClass.name, error.javaClass.name)
                    assertEquals(failure.message, error.message)
                }
                assertEquals(before + 1, starts)
                assertEquals(failure.javaClass.name, assertIs<ScanStatus.Failed>(statuses.last()).cause.javaClass.name)
            }
    }

    @Test
    fun `cancelling while Bluetooth is off releases its listener`() = runTest {
        enabled.value = false
        val job = launch { scanner().collect {} }
        runCurrent()
        assertEquals(1, enabled.subscriptionCount.value)
        job.cancelAndJoin()
        assertEquals(0, enabled.subscriptionCount.value)
        enabled.value = true
        delay(1.minutes)
        assertEquals(0, starts)
        assertEquals(ScanStatus.Idle, statuses.last())
    }

    @Test
    fun `no sensors does not cause restarts`() = runTest {
        flow<ScanEvent<Int>> {
            starts++
            emit(ScanEvent.Started)
            awaitCancellation()
        }.recoveringScan(enabled, testScheduler.timeSource) { statuses += it }.test {
            delay(20.minutes)
            assertEquals(1, starts)
            assertEquals(ScanStatus.Scanning, statuses.last())
            expectNoEvents()
        }
    }

    @Test
    fun `waiting for a start slot is not reported as scanning`() = runTest {
        flow<ScanEvent<Int>> {
            delay(30.seconds)
            emit(ScanEvent.Started)
            awaitCancellation()
        }.recoveringScan(enabled, testScheduler.timeSource) { statuses += it }.test {
            delay(29.seconds)
            assertEquals(ScanStatus.Starting, statuses.last())
            delay(11.seconds)
            runCurrent()
            assertEquals(ScanStatus.Scanning, statuses.last())
        }
    }
}
