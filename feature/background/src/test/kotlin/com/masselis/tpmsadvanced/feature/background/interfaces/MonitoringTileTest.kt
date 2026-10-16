package com.masselis.tpmsadvanced.feature.background.interfaces

import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus
import org.junit.Test
import kotlin.test.assertEquals

internal class MonitoringTileTest {
    @Test fun `tile distinguishes stopped waiting active and bluetooth suspension`() {
        assertEquals("Stopped", monitoringTileLabel(false, false, ScanStatus.Idle))
        assertEquals("Waiting for ride", monitoringTileLabel(true, false, ScanStatus.Scanning))
        assertEquals("Active", monitoringTileLabel(true, true, ScanStatus.Scanning))
        assertEquals("Suspended", monitoringTileLabel(true, true, ScanStatus.WaitingForBluetooth))
    }
    @Test fun `notification never describes stale or future readings as current`() {
        assertEquals("10s ago", readingAge(100.0, 110.0))
        assertEquals("2m ago", readingAge(100.0, 220.0))
        assertEquals("stale · 2m ago", readingAge(100.0, 221.0))
        assertEquals("time unavailable", readingAge(110.0, 100.0))
    }
}
