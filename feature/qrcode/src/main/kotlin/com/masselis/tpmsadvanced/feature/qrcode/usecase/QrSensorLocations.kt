package com.masselis.tpmsadvanced.feature.qrcode.usecase

import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeSensors

internal fun QrCodeSensors.forVehicle(kind: Kind): List<Sensor> = map { sensor ->
    val wheel = sensor.wheel
    Sensor(sensor.id, when (kind) {
        Kind.CAR, Kind.DOUBLE_AXLE_TRAILER, Kind.CAR_WITH_SPARE, Kind.VAN, Kind.UTE -> wheel
        Kind.MONOWHEEL -> Location.Auxiliary.SINGLE
        Kind.MOTORCYCLE -> wheel.toAxle()
        Kind.SINGLE_AXLE_TRAILER -> wheel.toSide()
        Kind.TADPOLE_THREE_WHEELER -> if (wheel.toAxle() == Location.Axle.REAR) wheel.toAxle() else wheel
        Kind.DELTA_THREE_WHEELER -> if (wheel.toAxle() == Location.Axle.FRONT) wheel.toAxle() else wheel
    })
}.also { sensors ->
    val conflicts = sensors.groupBy { it.location }.filterValues { it.size > 1 }.keys
    if (conflicts.isNotEmpty()) throw ConflictingLocations(conflicts)
}

internal class ConflictingLocations(val locations: Set<Location>) : IllegalArgumentException()
