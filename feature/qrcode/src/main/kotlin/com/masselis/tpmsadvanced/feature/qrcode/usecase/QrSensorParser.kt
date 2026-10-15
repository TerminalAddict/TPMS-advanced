package com.masselis.tpmsadvanced.feature.qrcode.usecase

import com.masselis.tpmsadvanced.data.vehicle.model.Location.Wheel
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeSensor
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeSensors

/** Parse the whole code; never accept a valid-looking fragment of an unrelated QR code. */
internal object QrSensorParser {
    private val idPattern = "[0-9a-fA-F]{6}".toRegex()

    @Suppress("MagicNumber", "ThrowsCount") // Distinct failures drive the existing QR result dialogs.
    fun parse(text: String): QrCodeSensors {
        val parts = text.trim().split('&')
        if (parts.size !in setOf(1, 2, 4) || parts.any { !idPattern.matches(it) }) throw InvalidCode()
        if (parts.size == 1) throw QrCodeSensorUseCase.SingleSensorQrCode(
            checkNotNull(SensorIdParser.parse(parts.single()))
        )
        val sensors = parts.mapIndexed { index, hex ->
            val wheel = when (hex.first()) {
                '1' -> Wheel.FRONT_LEFT
                '2' -> Wheel.FRONT_RIGHT
                '3' -> Wheel.REAR_LEFT
                '4' -> Wheel.REAR_RIGHT
                else -> Wheel.entries[index]
            }
            QrCodeSensor(checkNotNull(SensorIdParser.parse(hex)) shl 8, wheel)
        }
        if (sensors.distinctBy { it.id }.size != sensors.size) throw QrCodeSensors.DuplicateId(sensors.map { it.id })
        if (sensors.distinctBy { it.wheel }.size != sensors.size) {
            throw QrCodeSensors.DuplicateWheelLocation(sensors.map { it.wheel })
        }
        return when (sensors.size) {
            2 -> QrCodeSensors.TwoWheel(sensors[0], sensors[1])
            else -> QrCodeSensors.FourWheel(sensors[0], sensors[1], sensors[2], sensors[3])
        }
    }

    class InvalidCode : IllegalArgumentException("Not a supported TPMS QR code")
}
