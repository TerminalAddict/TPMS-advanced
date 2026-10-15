package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import java.nio.ByteBuffer
import java.nio.ByteOrder.LITTLE_ENDIAN
import kotlin.math.roundToInt

/** Synthetic payloads for the production decoders; these are never transmitted over the radio. */
@Suppress("MagicNumber")
internal object MockSensorPacket {
    fun bekubee(id: Int, kpa: Float, celsius: Float, volts: Float): ByteArray {
        require(id in 0..0xFFFFFF && kpa in 0f..1000f && celsius in -50f..205f && volts in 1.22f..3.77f)
        val pressure = (kpa + 100).roundToInt()
        val payload = byteArrayOf(
            ((volts - 1.22f) * 100).roundToInt().toByte(), (celsius + 50).roundToInt().toByte(),
            (pressure shr 8).toByte(), pressure.toByte(), id.toByte(), (id shr 8).toByte(),
            (id shr 16).toByte(), 0, 0
        )
        return "050854504D530CFF0200".hexToByteArray() + payload + "030327A8".hexToByteArray()
    }

    fun sysgration(id: Int, kpa: Float, celsius: Float, battery: Int, wheel: Int, alarm: Boolean): ByteArray {
        require(id and 0xFF == 0 && kpa in 0f..1000f && celsius in -50f..150f && battery in 0..100 && wheel in 0..3)
        val payload = ByteBuffer.allocate(16).order(LITTLE_ENDIAN)
            .put((0x80 + wheel).toByte()).put(0xEA.toByte()).put(0xCA.toByte())
            .put((id shr 8).toByte()).put((id shr 16).toByte()).put((id shr 24).toByte())
            .putInt((kpa * 1000).roundToInt()).putInt((celsius * 100).roundToInt())
            .put(battery.toByte()).put(if (alarm) 1.toByte() else 0.toByte()).array()
        return "0201060303B0FB13FFAC00".hexToByteArray() + payload
    }
}
