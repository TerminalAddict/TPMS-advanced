package com.masselis.tpmsadvanced.data.vehicle.interfaces

import android.os.SystemClock
import com.masselis.tpmsadvanced.data.vehicle.model.BleAdvertisement
import com.masselis.tpmsadvanced.data.vehicle.model.ReceptionQuality
import com.masselis.tpmsadvanced.data.vehicle.model.ReceptionTracker

/** Updated once before the shared scan fans out to consumers; never starts an extra scan. */
public object SensorReception {
    private val tracker = ReceptionTracker()
    public fun status(status: BluetoothLeScanner.ScanStatus) {
        tracker.available(status == BluetoothLeScanner.ScanStatus.Scanning)
    }
    public fun record(packet: BleAdvertisement) {
        // ScanResult uses the same monotonic clock; preserve reception time when Android batches callbacks.
        packet.reading?.let { tracker.record(it.sensorId, packet.rssi, packet.elapsedNanos / NANOS_PER_MILLISECOND) }
    }
    public fun snapshot(id: Int, expectedSeconds: Int?): ReceptionQuality? =
        tracker.snapshot(id, SystemClock.elapsedRealtime(), expectedSeconds)
    public fun reset(): Unit = tracker.reset()
    private const val NANOS_PER_MILLISECOND = 1_000_000L
}
