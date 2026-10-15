package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import androidx.core.util.size
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.data.vehicle.model.BleAdvertisement

/** Decode only validated packets; a malformed packet must remain exportable without ending the scan. */
@SuppressLint("MissingPermission")
internal fun inspectAdvertisement(result: ScanResult): BleAdvertisement {
    val record = result.scanRecord
    val decoded = try {
        decodeAdvertisement(result)?.let { Triple(it.protocolName(), it.asTyre(), it.diagnosticStatus()) }
    } catch (_: IndexOutOfBoundsException) { null }
    catch (_: IllegalArgumentException) { null }
    catch (_: IllegalStateException) { null }
    catch (_: SecurityException) { null }
    val manufacturers = record?.manufacturerSpecificData
    return BleAdvertisement(
        timestamp = now(), elapsedNanos = result.timestampNanos,
        address = result.device.address, name = record?.deviceName, rssi = result.rssi,
        bytes = record?.bytes?.toHexString().orEmpty(),
        manufacturerData = (0 until (manufacturers?.size ?: 0)).associate {
            val companyId = manufacturers!!.keyAt(it).toUShort().toHexString()
            "0x$companyId" to manufacturers.valueAt(it).toHexString()
        },
        serviceUuids = record?.serviceUuids.orEmpty().map { it.toString() },
        serviceData = record?.serviceData.orEmpty().mapKeys { it.key.toString() }.mapValues { it.value.toHexString() },
        connectable = result.isConnectable, dataStatus = result.dataStatus,
        decoder = decoded?.first, reading = decoded?.second, status = decoded?.third,
    )
}

/** Stable in minified release builds as well as debug builds. */
private fun Raw.protocolName(): String = when (this) {
    is RawBekubeeTpms -> "Bekubee TPMS (A827)"
    is RawBekubeeKy -> "Bekubee KY (27A5)"
    is RawPecham -> "Pecham BR"
    is RawWicarlink -> "Wicarlink / LYTPMS"
    is RawSysgration -> "Sysgration"
    else -> "TPMS"
}
