package com.masselis.tpmsadvanced.data.vehicle.model

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.add

/** Immutable radio evidence, including packets none of our TPMS decoders understand. */
public data class BleAdvertisement(
    val timestamp: Double,
    val elapsedNanos: Long,
    val address: String,
    val name: String?,
    val rssi: Int,
    val bytes: String,
    val manufacturerData: Map<String, String>,
    val serviceUuids: List<String>,
    val serviceData: Map<String, String>,
    val connectable: Boolean,
    val dataStatus: Int,
    val decoder: String? = null,
    val reading: ScannerRecord? = null,
    val status: String? = null,
) {
    public fun jsonLine(): String = buildJsonObject {
        put("format", "TyrePing BLE discovery v1")
        put("timestamp", timestamp); put("elapsedNanos", elapsedNanos)
        put("address", address); put("name", name); put("rssi", rssi)
        put("bytes", bytes); put("connectable", connectable); put("dataStatus", dataStatus)
        putJsonObject("manufacturerData") { manufacturerData.forEach { (key, value) -> put(key, value) } }
        putJsonArray("serviceUuids") { serviceUuids.forEach { add(it) } }
        putJsonObject("serviceData") { serviceData.forEach { (key, value) -> put(key, value) } }
        put("decoder", decoder); put("status", status)
        reading?.let { put("sensorId", it.sensorId.toUInt().toString()) }
        (reading as? Tyre)?.let {
            put("pressureKpa", it.pressure.kpa); put("temperatureCelsius", it.temperature.celsius)
        }
    }.toString()
}
