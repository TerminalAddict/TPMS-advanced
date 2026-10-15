package com.masselis.tpmsadvanced.data.vehicle.interfaces

import com.masselis.tpmsadvanced.data.vehicle.model.ScannerRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.TimeMark

public interface BluetoothLeScanner {

    public sealed class Failure(message: String) : Exception(message) {
        public class ScannerIsNull(adapterState: Int?) :
            Failure("Failure.ScannerIsNull(adapterState=$adapterState)")

        public class Scan(public val reason: Int) : Failure("Failure.Scan(reason=$reason)")
    }

    public sealed interface ScanStatus {
        public data object Idle : ScanStatus
        public data object Starting : ScanStatus
        public data object Scanning : ScanStatus
        public data object WaitingForBluetooth : ScanStatus
        public data class Retrying(val reason: Int?, val since: TimeMark) : ScanStatus
        public data class Failed(val cause: Throwable) : ScanStatus
    }

    /** Observing status does not start the radio; collect a scan flow to monitor sensors. */
    public val scanStatus: StateFlow<ScanStatus>

    public fun highDutyScan(): Flow<ScannerRecord>
    public fun normalScan(): Flow<ScannerRecord>

    public fun missingPermission(): List<String>

    public val isBluetoothRequired: Boolean
}
