@file:Suppress("MagicNumber") // BLE advertising length/type fields and bounded inspection sizes.

package com.masselis.tpmsadvanced.data.vehicle.model

import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

public data class AdvertisementField(val offset: Int, val type: Int, val payload: String) {
    public val label: String get() = when (type) {
        0x01 -> "Flags"; 0x02, 0x03 -> "16-bit service UUIDs"; 0x06, 0x07 -> "128-bit service UUIDs"
        0x08, 0x09 -> "Device name"; 0x0A -> "TX power"; 0x16 -> "16-bit service data"
        0x20 -> "32-bit service data"; 0x21 -> "128-bit service data"; 0xFF -> "Manufacturer data"
        else -> "AD type 0x${type.toString(16).padStart(2, '0')}"
    }
}
public data class AdvertisementStructure(val fields: List<AdvertisementField>, val truncated: Boolean)

/** The length byte includes the type byte. Malformed evidence stays inspectable, never throws. */
@Suppress("ReturnCount") // Stop immediately at malformed input or a truncated AD field.
public fun advertisementStructure(hex: String): AdvertisementStructure {
    if (hex.length > 8192 || hex.length % 2 != 0 || hex.any { it !in "0123456789abcdefABCDEF" })
        return AdvertisementStructure(emptyList(), true)
    val bytes = hex.take(8192).chunked(2).map { it.takeIf { part -> part.length == 2 }?.toIntOrNull(16) }
    if (bytes.any { it == null } || hex.length > 8192) return AdvertisementStructure(emptyList(), true)
    val fields = mutableListOf<AdvertisementField>()
    var offset = 0
    while (offset < bytes.size) {
        val length = checkNotNull(bytes[offset])
        if (length == 0) break
        if (offset + length >= bytes.size) return AdvertisementStructure(fields, true)
        fields += AdvertisementField(offset, checkNotNull(bytes[offset + 1]),
            hex.substring((offset + 2) * 2, (offset + length + 1) * 2))
        offset += length + 1
    }
    return AdvertisementStructure(fields, false)
}

/** Offsets are relative to the full advertisement, grouped by equal packet length. */
public fun varyingByteOffsets(packets: List<BleAdvertisement>): Map<Int, List<Int>> =
    packets.takeLast(1000).groupBy { it.bytes.length / 2 }.mapValues { (size, group) ->
        val first = group.first().bytes
        (0 until size).filter { offset ->
            group.any { !first.regionMatches(offset * 2, it.bytes, offset * 2, 2, ignoreCase = true) }
        }
    }

/** Unknown payloads can themselves contain identifiers. Export structure/changes, never claim raw bytes anonymous. */
public fun anonymisedCompatibilityReport(packets: List<BleAdvertisement>): String {
    val retained = packets.takeLast(1000)
    val devices = retained.map { it.address }.distinct()
    val first = retained.minOfOrNull { it.elapsedNanos } ?: 0
    return buildJsonObject {
        put("format", "TyrePing anonymised structural samples v1")
        put("redaction", "Names, addresses, sensor IDs, absolute times, UUID values and ALL payload bytes omitted. " +
            "Raw local captures are needed to implement and validate a decoder.")
        putJsonArray("devices") { devices.forEachIndexed { index, address ->
            val samples = retained.filter { it.address == address }
            add(buildJsonObject {
                put("device", "sensor-${index + 1}")
                putJsonArray("varyingOffsetsByLength") { varyingByteOffsets(samples).forEach { (size, offsets) ->
                    add(buildJsonObject { put("bytes", size); putJsonArray("offsets") { offsets.forEach { add(it) } } })
                } }
                putJsonArray("samples") { samples.forEach { packet ->
                    val structure = advertisementStructure(packet.bytes)
                    add(buildJsonObject {
                        put("relativeSeconds", (packet.elapsedNanos - first).coerceAtLeast(0) / 1_000_000_000.0)
                        put("rssi", packet.rssi); put("decoded", packet.reading is Tyre)
                        put("bytes", packet.bytes.length / 2); put("truncated", structure.truncated)
                        putJsonArray("fields") { structure.fields.forEach { field -> add(buildJsonObject {
                            put("offset", field.offset); put("type", field.type)
                            put("payloadBytes", field.payload.length / 2)
                        }) } }
                    })
                } }
            })
        } }
    }.toString()
}
