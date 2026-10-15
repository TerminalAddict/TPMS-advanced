package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
internal class ScanStartLimiterTest {
    @Test
    fun `concurrent starts respect both spacing and rolling window`() = runTest {
        val limiter = ScanStartLimiter(testScheduler.timeSource)
        val starts = List(8) {
            async {
                limiter.awaitTurn()
                testScheduler.currentTime
            }
        }.awaitAll()
        assertEquals(listOf(0L, 5000L, 10000L, 15000L, 30000L, 35000L, 40000L, 45000L), starts)
    }

    @Test
    fun `cancelled waiter does not reserve a start or hold the lock`() = runTest {
        val limiter = ScanStartLimiter(testScheduler.timeSource)
        limiter.awaitTurn()
        val waiting = launch { limiter.awaitTurn() }
        runCurrent()
        waiting.cancelAndJoin()
        limiter.awaitTurn()
        assertEquals(5000L, testScheduler.currentTime)
    }
}
