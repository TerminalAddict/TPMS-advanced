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
    val reading = decoded?.second
    val tyre = reading as? com.masselis.tpmsadvanced.data.vehicle.model.Tyre
    val quality = buildList {
        if (decoded == null && listOf(RawPecham.SERVICE_UUID, RawBekubeeKy.SERVICE_UUID,
                RawBekubeeTpms.SERVICE_UUID, RawSysgration.SERVICE_UUID, RawWicarlink.SERVICE_UUID)
                .any { record?.serviceUuids?.contains(it) == true })
            add("Possible TPMS packet could not be decoded: unsupported, malformed or checksum failure")
        if (tyre != null) addAll(com.masselis.tpmsadvanced.data.vehicle.model.suspiciousReading(
            tyre.pressure.kpa, tyre.temperature.celsius, tyre.sensorBattery))
    }
    // Non-finite fields cannot be stored as a usable reading. Their packet and explicit warning remain visible.
    val usable = reading.takeUnless { tyre != null &&
        (!tyre.pressure.kpa.isFinite() || !tyre.temperature.celsius.isFinite()) }
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
        decoder = decoded?.first, reading = usable, status = decoded?.third, qualityWarnings = quality,
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
