package com.masselis.tpmsadvanced.data.vehicle.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

internal class ReceptionTrackerTest {
    @Test fun `counts every unchanged broadcast and ignores unavailable RSSI`() {
        val tracker = ReceptionTracker(); tracker.available(true)
        tracker.record(1, -70, 0); tracker.record(1, -80, 1000); tracker.record(1, 127, 2000)
        val stats = assertNotNull(tracker.snapshot(1, 2000, null))
        assertEquals(3, stats.windowPackets); assertEquals(3L, stats.packets)
        assertEquals(-75.0, stats.meanRssi); assertNull(stats.latestRssi); assertNull(stats.estimatedMissing)
    }
    @Test fun `expected interval estimates include active tail with grace`() {
        val tracker = ReceptionTracker(); tracker.available(true)
        tracker.record(1, -60, 0); tracker.record(1, -60, 30000)
        assertEquals(2L, tracker.snapshot(1, 30000, 10)?.estimatedMissing)
        assertEquals(3L, tracker.snapshot(1, 50000, 10)?.estimatedMissing)
        assertEquals(30L, tracker.snapshot(1, 50000, 10)?.longestGapSeconds)
    }
    @Test fun `scan stops and restarts never count as missing broadcasts`() {
        val tracker = ReceptionTracker(); tracker.available(true); tracker.record(1, -60, 0)
        tracker.available(false)
        assertEquals(0L, tracker.snapshot(1, 100000, 10)?.estimatedMissing)
        tracker.available(true)
        assertEquals(0L, tracker.snapshot(1, 200000, 10)?.estimatedMissing)
        tracker.record(1, -65, 200000)
        assertEquals(0L, tracker.snapshot(1, 200000, 10)?.estimatedMissing)
    }
    @Test fun `window and sensor limits bound memory and reset clears all evidence`() {
        val tracker = ReceptionTracker(sensorLimit = 2, windowLimit = 2); tracker.available(true)
        repeat(5) { tracker.record(1, -60, it * 1000L) }
        assertEquals(2, tracker.snapshot(1, 4000, 1)?.windowPackets)
        assertEquals(5L, tracker.snapshot(1, 4000, 1)?.packets)
        tracker.record(2, -50, 5000); tracker.record(3, -50, 5000)
        assertNull(tracker.snapshot(1, 5000, 1))
        tracker.reset(); assertNull(tracker.snapshot(3, 5000, 1))
    }
    @Test fun `clock reversal discards old gap history and stopped scans ignore observations`() {
        val tracker = ReceptionTracker(); tracker.record(1, -60, 1000)
        assertNull(tracker.snapshot(1, 1000, 1))
        tracker.available(true); tracker.record(1, -60, 10000); tracker.record(1, -60, 100)
        assertEquals(1, tracker.snapshot(1, 100, 1)?.windowPackets)
        assertEquals(0L, tracker.snapshot(1, 100, 1)?.estimatedMissing)
    }
}
