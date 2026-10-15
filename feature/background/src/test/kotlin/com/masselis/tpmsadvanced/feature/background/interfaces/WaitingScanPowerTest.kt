package com.masselis.tpmsadvanced.feature.background.interfaces

import android.bluetooth.le.ScanSettings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

internal class WaitingScanPowerTest {
    @Test fun `active ride always stays at full rate even when stationary and locked`() {
        listOf(null, false, true).forEach { motion ->
            assertEquals(ScanSettings.SCAN_MODE_LOW_LATENCY, waitingScanMode(true, true, motion, false))
        }
    }
    @Test fun `motion screen charging and opt out retain full rate`() {
        assertEquals(ScanSettings.SCAN_MODE_LOW_LATENCY, waitingScanMode(false, true, true, false))
        assertEquals(ScanSettings.SCAN_MODE_LOW_LATENCY, waitingScanMode(false, true, false, true))
        assertEquals(ScanSettings.SCAN_MODE_LOW_LATENCY, waitingScanMode(false, false, false, false))
    }
    @Test fun `only confirmed stationary waiting uses low power and missing sensor uses balanced`() {
        assertEquals(ScanSettings.SCAN_MODE_LOW_POWER, waitingScanMode(false, true, false, false))
        assertEquals(ScanSettings.SCAN_MODE_BALANCED, waitingScanMode(false, true, null, false))
    }
    @Test fun `Bluetooth RSSI unavailable sentinel cannot start a ride`() {
        val beacon = BeaconPresence(-80)
        beacon.received(0, 127); beacon.received(2_000, 127)
        assertFalse(beacon.isPresent(2_000))
    }
}
