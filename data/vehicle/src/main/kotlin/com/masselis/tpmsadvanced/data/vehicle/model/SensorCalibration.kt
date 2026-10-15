package com.masselis.tpmsadvanced.data.vehicle.model

/** Corrected = raw × multiplier + offset. Pressure offset is kPa; temperature offset is Celsius. */
@Suppress("MagicNumber") // Human-readable calibration bounds shown in the editor.
public data class SensorCalibration(
    val pressureOffset: Float = 0f,
    val pressureMultiplier: Float = 1f,
    val temperatureOffset: Float = 0f,
    val temperatureMultiplier: Float = 1f,
) {
    public fun isValid(): Boolean = pressureOffset in -100f..100f && pressureMultiplier in .5f..1.5f &&
        temperatureOffset in -30f..30f && temperatureMultiplier in .5f..1.5f

    public fun pressure(raw: Pressure): Pressure =
        if (raw.kpa <= 0) raw else Pressure((raw.kpa * pressureMultiplier + pressureOffset).coerceAtLeast(0f))

    public fun temperature(raw: Temperature): Temperature =
        Temperature(raw.celsius * temperatureMultiplier + temperatureOffset)
}
