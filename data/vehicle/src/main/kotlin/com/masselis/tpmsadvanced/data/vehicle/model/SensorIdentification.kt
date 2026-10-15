package com.masselis.tpmsadvanced.data.vehicle.model

/** Evidence for a guided assignment; the user still confirms the sensor ID and any reassignment. */
public class SensorIdentification {
    private val baseline = mutableMapOf<Int, Float>()
    private val changed = mutableSetOf<Int>()
    public fun observe(reading: Tyre): Boolean {
        val first = baseline.getOrPut(reading.sensorId) { reading.pressure.kpa }
        if (kotlin.math.abs(first - reading.pressure.kpa) >= CHANGE_KPA) changed += reading.sensorId
        return reading.sensorId in changed
    }
    private companion object { const val CHANGE_KPA = 15f }
}
