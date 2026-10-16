@file:Suppress("MagicNumber")

package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.vehicle.model.AlertObservation
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.WheelReadiness
import com.masselis.tpmsadvanced.data.vehicle.model.backupLocation

/** The same events feed durable history and secondary-vehicle delivery. */
public fun VehicleHealth.monitoringEvents(maintenance: Boolean): List<AlertObservation> = buildList {
    fun event(wheel: String, sensor: Int?, kind: String, critical: Boolean = false) {
        add(AlertObservation("${vehicle.uuid}:$wheel:$sensor:$kind:$critical", vehicle.uuid.toString(),
            vehicle.name, wheel, sensor, kind, critical))
    }
    wheels.forEach { wheel ->
        val label = backupLocation(wheel.location)
        val reading = wheel.reading
        if (wheel.state == WheelReadiness.MISSING && !maintenance)
            event(label, reading?.sensorId, "Sensor missing")
        if (wheel.state == WheelReadiness.WARNING && reading != null) {
            wheel.warnings.forEach { warning ->
                val pressure = warning == "Pressure outside limits" || warning == "Possible pressure loss"
                if (!maintenance || !pressure) event(label, reading.sensorId, warning,
                    vehicle.critical(wheel.location, reading, warning))
            }
        }
    }
    if (!maintenance) imbalances.forEach { event(it.axle, null, "Left/right pressure imbalance") }
}

private fun com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.critical(location: Location,
    reading: com.masselis.tpmsadvanced.data.vehicle.model.TyreAtmosphere, warning: String): Boolean {
    val rear = location == Location.Axle.REAR || location == Location.Auxiliary.SPARE ||
        (location is Location.Wheel && location.toAxle() == Location.Axle.REAR)
    val low = if (rear && separateFrontRearPressure) rearLowPressure else lowPressure
    val high = if (rear && separateFrontRearPressure) rearHighPressure else highPressure
    return when (warning) {
        "Pressure outside limits" -> reading.pressure.kpa <= low.kpa * .75f || reading.pressure.kpa >= high.kpa * 1.2f
        "Temperature above limit" -> reading.temperature.celsius >= highTemp.celsius + 20
        else -> false
    }
}
