package com.masselis.tpmsadvanced.feature.qrcode.usecase

internal object SensorIdParser {

    private val sensorIdRegex = "^[0-9a-fA-F]{6}$".toRegex()

    /**
     * Converts the six-character ID printed on a sensor into the
     * little-endian 24-bit ID used by compatible BLE sensor packets.
     *
     * Example:
     * 002D56 -> 0x562D00
     */
    @Suppress("MagicNumber") // Three little-endian bytes of a printed sensor ID.
    fun parse(value: String): Int? {
        val normalized = value.trim()

        if (!sensorIdRegex.matches(normalized)) {
            return null
        }

        val bytes = normalized.hexToByteArray()

        return (bytes[0].toInt() and 0xFF) or
            ((bytes[1].toInt() and 0xFF) shl 8) or
            ((bytes[2].toInt() and 0xFF) shl 16)
    }
}
