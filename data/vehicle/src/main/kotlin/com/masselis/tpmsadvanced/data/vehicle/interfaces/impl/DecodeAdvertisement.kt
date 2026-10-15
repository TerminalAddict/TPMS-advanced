package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import android.bluetooth.le.ScanResult
import co.touchlab.kermit.Logger

/** Share the same parser chain between radio reception and debug replay validation. */
@Suppress("TooGenericExceptionCaught") // Packet decoders index byte arrays; reject truncated input.
internal fun decodeAdvertisement(result: ScanResult): Raw? = try {
    RawPecham(result) ?: RawBekubeeKy(result) ?: RawWicarlink(result) ?:
        RawBekubeeTpms(result) ?: RawSysgration(result)
} catch (cause: IndexOutOfBoundsException) {
    Logger.withTag("BluetoothLeScannerImpl").w("Truncated TPMS packet", cause)
    null
} catch (cause: IllegalArgumentException) {
    Logger.withTag("BluetoothLeScannerImpl").w("Invalid TPMS packet", cause)
    null
}
