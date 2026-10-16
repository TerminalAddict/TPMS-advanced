@file:Suppress("MagicNumber") // Explicit time, pressure and bounded persistence limits.

package com.masselis.tpmsadvanced.data.vehicle.model

import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.abs

@Serializable
public data class TowingCombination(
    val id: String = UUID.randomUUID().toString(), val name: String, val vehicle: String, val trailer: String,
) {
    public fun validate() {
        listOf(id, vehicle, trailer).forEach { require(UUID.fromString(it).toString() == it) }
        require(name.isNotBlank() && name.length <= 60 && vehicle != trailer)
    }
    public fun isValidFor(vehicles: Map<String, Vehicle.Kind>): Boolean =
        vehicles[vehicle] in TOWING_KINDS && vehicles[trailer] in TRAILER_KINDS
    public companion object {
        public val TOWING_KINDS: Set<Vehicle.Kind> = setOf(Vehicle.Kind.CAR, Vehicle.Kind.CAR_WITH_SPARE,
            Vehicle.Kind.VAN, Vehicle.Kind.UTE)
        public val TRAILER_KINDS: Set<Vehicle.Kind> = setOf(Vehicle.Kind.SINGLE_AXLE_TRAILER,
            Vehicle.Kind.DOUBLE_AXLE_TRAILER)
    }
}

@Serializable
public data class ColdSample(val sensorId: Int, val timestamp: Double, val kpa: Float, val celsius: Float) {
    public fun validate() {
        require(timestamp in 1.0..MAX_BASELINE_TIMESTAMP && kpa in 0f..2000f && celsius in -100f..200f)
    }
}

/** A user-confirmed baseline, never an inference that the first packet is cold. Values remain raw. */
@Serializable
public data class ColdBaseline(val confirmedAt: Double, val samples: Map<String, ColdSample>) {
    public fun validate() {
        require(confirmedAt in 1.0..MAX_BASELINE_TIMESTAMP && samples.size in 1..5)
        require(samples.values.map { it.sensorId }.distinct().size == samples.size)
        samples.forEach { (location, sample) ->
            backupLocation(location); sample.validate()
            require(confirmedAt - sample.timestamp in 0.0..120.0)
        }
    }
}

@Serializable
public data class DrivingInsights(
    val combinations: List<TowingCombination> = emptyList(), val activeCombination: String? = null,
    val imbalanceKpa: Map<String, Float> = emptyMap(),
    val broadcastSeconds: Map<Int, Int> = emptyMap(),
    val cold: Map<String, ColdBaseline> = emptyMap(),
    val highVisibility: Boolean = false, val readoutScale: Float = 1f,
    val readingCards: Boolean = false,
) {
    public fun validate() {
        require(combinations.size <= 50 && combinations.distinctBy { it.id }.size == combinations.size)
        require(combinations.distinctBy { it.name.lowercase() }.size == combinations.size)
        combinations.forEach { it.validate() }
        require(activeCombination == null || combinations.any { it.id == activeCombination })
        require(imbalanceKpa.size <= 100 && cold.size <= 100 && broadcastSeconds.size <= 1000)
        (imbalanceKpa.keys + cold.keys).forEach { require(UUID.fromString(it).toString() == it) }
        require(imbalanceKpa.values.all { it in 5f..200f })
        require(broadcastSeconds.values.all { it in 1..3600 })
        cold.values.forEach { it.validate() }
        require(readoutScale in 1f..2f)
    }

    public fun forVehicles(kinds: Map<String, Vehicle.Kind>): DrivingInsights {
        val kept = combinations.filter { it.isValidFor(kinds) }
        val baselines = cold.mapNotNull { (id, baseline) ->
            val samples = baseline.samples.filterKeys { backupLocation(it) in kinds[id]?.locations.orEmpty() }
            if (samples.isEmpty()) null else id to baseline.copy(samples = samples)
        }.toMap()
        return copy(combinations = kept,
            activeCombination = activeCombination?.takeIf { id -> kept.any { it.id == id } },
            imbalanceKpa = imbalanceKpa.filterKeys { it in kinds }, cold = baselines)
    }

    public fun activeFor(vehicle: UUID, kinds: Map<String, Vehicle.Kind>): TowingCombination? =
        combinations.firstOrNull {
            it.id == activeCombination && it.vehicle == vehicle.toString() && it.isValidFor(kinds)
        }
}

public data class AxleImbalance(val axle: String, val left: Location, val right: Location, val differenceKpa: Float)

/** Compare only fresh, close-in-time readings on the same axle. Never compare front with rear. */
public fun axleImbalances(wheels: List<WheelHealth>, thresholdKpa: Float?, now: Double): List<AxleImbalance> {
    if (thresholdKpa == null) return emptyList()
    val pairs = listOf(Triple("Front", Location.Wheel.FRONT_LEFT, Location.Wheel.FRONT_RIGHT),
        Triple("Rear", Location.Wheel.REAR_LEFT, Location.Wheel.REAR_RIGHT),
        Triple("Trailer", Location.Side.LEFT, Location.Side.RIGHT))
    return pairs.mapNotNull { (axle, left, right) ->
        val first = wheels.firstOrNull { it.location == left }?.reading?.takeIf { it.freshPositive(now) }
        val second = wheels.firstOrNull { it.location == right }?.reading?.takeIf { it.freshPositive(now) }
        if (first == null || second == null || abs(first.timestamp - second.timestamp) > 60.0) null
        else abs(first.pressure.kpa - second.pressure.kpa).takeIf { it >= thresholdKpa }
            ?.let { AxleImbalance(axle, left, right, it) }
    }
}

public data class ThermalChange(val coldPressure: Pressure, val coldTemperature: Temperature,
    val pressureRiseKpa: Float, val temperatureRiseCelsius: Float)

@Suppress("ReturnCount") // No baseline, mismatched sensor and expired data have separate early exits.
public fun thermalChange(baseline: ColdBaseline, location: Location, current: TyreAtmosphere,
    calibration: SensorCalibration, now: Double): ThermalChange? {
    val cold = baseline.samples[backupLocation(location)] ?: return null
    val sameSensorLaterReading = cold.sensorId == current.sensorId && current.timestamp > cold.timestamp
    val validBaseline = cold.kpa > 0 && now - baseline.confirmedAt in 0.0..43_200.0
    if (!sameSensorLaterReading || !validBaseline || !current.freshPositive(now)) return null
    val pressure = calibration.pressure(Pressure(cold.kpa))
    val temperature = calibration.temperature(Temperature(cold.celsius))
    return ThermalChange(pressure, temperature, current.pressure.kpa - pressure.kpa,
        current.temperature.celsius - temperature.celsius)
}

private fun TyreAtmosphere.freshPositive(now: Double): Boolean =
    now - timestamp in 0.0..120.0 && pressure.kpa > 0

private const val MAX_BASELINE_TIMESTAMP = 253_402_300_799.0 // End of year 9999; safe to display as an Instant.
