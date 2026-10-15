package com.masselis.tpmsadvanced.feature.background.interfaces

import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.TimeSource
import org.junit.Test

internal class SpokenDiagnosticsTest {
    @Test fun disabledDoesNotAnnounce() {
        assertNull(diagnosticMessage("Monitoring car", ScanStatus.Scanning, false))
    }
    @Test fun reasonsSurviveStatusMapping() {
        assertContains(diagnosticMessage("Monitoring car: Accessory connected", ScanStatus.Scanning, true)!!,
            "Accessory connected")
        assertContains(diagnosticMessage("Car: waiting for ride trigger", ScanStatus.Idle, true)!!,
            "waiting for ride trigger")
    }
    @Test fun bluetoothAndScanFailuresExplainRecovery() {
        assertContains(diagnosticMessage("Monitoring", ScanStatus.WaitingForBluetooth, true)!!, "Bluetooth is disabled")
        assertContains(diagnosticMessage("Monitoring", ScanStatus.Failed(IllegalStateException()), true)!!,
            "scan failed")
        val first = diagnosticMessage("Monitoring", ScanStatus.Retrying(6, TimeSource.Monotonic.markNow()), true)
        val next = diagnosticMessage("Monitoring", ScanStatus.Retrying(6, TimeSource.Monotonic.markNow()), true)
        assertEquals(first, next) // Retry timestamps cannot produce repeated status announcements.
        assertContains(first!!, "Retrying automatically")
    }
}
