package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
internal class SharedAdvertisementScanTest {
    @Test fun `discovery widens a single scan and restores monitoring on cancellation`() = runTest {
        val packets = MutableSharedFlow<Int>()
        val modes = mutableListOf<Boolean>()
        var active = 0
        var maximumActive = 0
        val shared = SharedAdvertisementScan(backgroundScope) { unfiltered -> flow {
            active++
            maximumActive = maxOf(active, maximumActive)
            modes += unfiltered
            try { packets.collect { emit(it) } } finally { active-- }
        } }
        val monitored = mutableListOf<Int>()
        val discovered = mutableListOf<Int>()
        val monitor = backgroundScope.launch { shared.readings.collect { monitored += it } }
        runCurrent()
        packets.emit(1)
        runCurrent()
        val discovery = backgroundScope.launch { shared.discover().collect { discovered += it } }
        runCurrent()
        packets.emit(2); packets.emit(2)
        runCurrent()
        discovery.cancelAndJoin()
        runCurrent()
        packets.emit(3)
        runCurrent()
        assertEquals(listOf(false, true, false), modes)
        assertEquals(1, maximumActive)
        assertEquals(listOf(1, 2, 2, 3), monitored)
        assertEquals(listOf(2, 2), discovered)
        monitor.cancelAndJoin()
        runCurrent()
        assertEquals(0, active)
    }

    @Test fun `multiple discovery subscribers cannot stop each other's scan`() = runTest {
        val modes = mutableListOf<Boolean>()
        var active = 0
        val shared = SharedAdvertisementScan<Int>(backgroundScope) { mode -> flow {
            modes += mode; active++
            try { kotlinx.coroutines.awaitCancellation() } finally { active-- }
        } }
        val first = backgroundScope.launch { shared.discover().collect {} }
        runCurrent()
        val second = backgroundScope.launch { shared.discover().collect {} }
        runCurrent()
        first.cancelAndJoin(); runCurrent()
        assertEquals(listOf(true), modes)
        assertEquals(1, active)
        second.cancelAndJoin(); runCurrent()
        assertEquals(0, active)
        val normal = backgroundScope.launch { shared.readings.collect {} }
        runCurrent()
        assertEquals(false, modes.last())
        normal.cancelAndJoin(); runCurrent()
        assertEquals(0, active)
    }
}
