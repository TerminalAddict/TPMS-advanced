package com.masselis.tpmsadvanced.feature.background.interfaces

import android.bluetooth.le.ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.Failure
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Duration.Companion.minutes

internal data class ScanNotice(val message: String, val urgent: Boolean = false, val canRestart: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
internal fun Flow<ScanStatus>.notices(): Flow<ScanNotice?> = transformLatest { status ->
    when (status) {
        ScanStatus.Idle, ScanStatus.Starting -> emit(ScanNotice("Starting Bluetooth monitoring"))
        ScanStatus.Scanning -> emit(null)
        ScanStatus.WaitingForBluetooth -> emit(
            ScanNotice("Bluetooth is off. Monitoring will resume automatically when it is enabled.")
        )
        is ScanStatus.Retrying -> {
            val detail = status.reason?.let { " (error $it)" }.orEmpty()
            emit(ScanNotice("Bluetooth scan unavailable$detail. Retrying automatically."))
            delay(10.minutes - status.since.elapsedNow())
            emit(ScanNotice(
                "Bluetooth scan keeps failing$detail. Tyres are not monitored; still retrying.", urgent = true
            ))
        }
        is ScanStatus.Failed -> emit(
            when {
                status.cause is SecurityException -> ScanNotice(
                    "Bluetooth permission is missing. Open TyrePing and grant permission to monitor tyres.",
                    urgent = true
                )
                (status.cause as? Failure.Scan)?.reason == SCAN_FAILED_FEATURE_UNSUPPORTED -> ScanNotice(
                    "This phone does not support the requested Bluetooth scan. Monitoring has stopped.", urgent = true
                )
                else -> ScanNotice("Monitoring stopped after an unexpected error. Restart the app to try again.",
                    urgent = true, canRestart = true)
            }
        )
    }
}
