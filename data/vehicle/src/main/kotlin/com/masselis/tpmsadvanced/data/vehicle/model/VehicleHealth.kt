package com.masselis.tpmsadvanced.data.vehicle.model

public enum class WheelReadiness { UNSEEN, SLEEPING, HEALTHY, WARNING, MISSING }

public data class WheelHealth(
    val location: Location, val reading: TyreAtmosphere?, val state: WheelReadiness, val warnings: List<String>,
) {
    public val label: String get() = location.toString().lowercase().replace('_', ' ')
}

public fun wheelHealth(vehicle: Vehicle, location: Location, reading: TyreAtmosphere?, now: Double,
    missing: Boolean = false): WheelHealth {
    val warnings = reading?.let { readingWarnings(vehicle, location, it) }.orEmpty()
    val state = when {
        missing -> WheelReadiness.MISSING
        reading == null -> WheelReadiness.UNSEEN
        now - reading.timestamp !in 0.0..FRESH_SECONDS -> WheelReadiness.SLEEPING
        warnings.isNotEmpty() -> WheelReadiness.WARNING
        else -> WheelReadiness.HEALTHY
    }
    return WheelHealth(location, reading, state, warnings)
}

private const val FRESH_SECONDS = 120.0

private fun readingWarnings(vehicle: Vehicle, location: Location, reading: TyreAtmosphere): List<String> =
    buildList {
        reading.let {
            val rear = location == Location.Axle.REAR || location == Location.Auxiliary.SPARE ||
                (location is Location.Wheel && location.toAxle() == Location.Axle.REAR)
            val low = if (rear && vehicle.separateFrontRearPressure) vehicle.rearLowPressure else vehicle.lowPressure
            val high = if (rear && vehicle.separateFrontRearPressure) vehicle.rearHighPressure else vehicle.highPressure
            if (it.pressure !in low..high) add("Pressure outside limits")
            if (it.temperature > vehicle.highTemp) add("Temperature above limit")
            if (it.sensorBattery?.isLow == true) add("Low sensor battery")
            if (it.pressureLoss != null) add("Possible pressure loss")
        }
    }

/** Monotonic liveness; old database readings cannot enroll a sensor into a new ride. */
public class MissingSensorTracker {
    private data class Seen(val timestamp: Double, val elapsed: Long)
    private val seen = mutableMapOf<String, Seen>()
    private var session: Double? = null
    private var availableSince: Long? = null

    @Suppress("ReturnCount") // Stop and radio-outage guards must clear state before checking timeouts.
    public fun evaluate(readings: Map<String, Double>, rideStarted: Double?, available: Boolean,
        elapsed: Long, timeoutMs: Long): Set<String> {
        if (session != rideStarted) { seen.clear(); session = rideStarted; availableSince = null }
        if (rideStarted == null) return emptySet()
        seen.keys.retainAll(readings.keys)
        readings.forEach { (key, timestamp) ->
            if (timestamp >= rideStarted && (seen[key]?.timestamp ?: Double.NEGATIVE_INFINITY) < timestamp)
                seen[key] = Seen(timestamp, elapsed)
        }
        if (!available) { availableSince = null; return emptySet() }
        if (availableSince == null) availableSince = elapsed
        return seen.filter { (_, value) ->
            elapsed - maxOf(value.elapsed, checkNotNull(availableSince)) >= timeoutMs
        }.keys
    }
}
