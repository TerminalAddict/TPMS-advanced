package com.masselis.tpmsadvanced.data.vehicle.model

/** Validate the entire destination layout before replacing bindings in a single transaction. */
public fun moveSensors(sensors: List<Sensor>, locations: Set<Location>, moves: Map<Location, Location>): List<Sensor> {
    require(moves.keys.all { from -> sensors.any { it.location == from } }) { "A source wheel has no sensor" }
    require(moves.values.all { it in locations }) { "A destination is not on this vehicle" }
    val result = sensors.map { it.copy(location = moves[it.location] ?: it.location) }
    require(result.map { it.location }.distinct().size == result.size) { "Two sensors would occupy the same wheel" }
    return result
}
