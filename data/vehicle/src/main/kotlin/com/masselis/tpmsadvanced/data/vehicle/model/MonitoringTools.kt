@file:Suppress("MagicNumber") // User-visible bounds and seconds-based report calculations.

package com.masselis.tpmsadvanced.data.vehicle.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
public enum class ParkedAlertRoute { ALL, NOTIFICATION_ONLY, CRITICAL_ONLY, OFF }

@Serializable
public data class MonitoringTools(
    val liveNotification: Boolean = false,
    val parkedRoutes: Map<String, ParkedAlertRoute> = emptyMap(),
    val readingDays: Int = 0, val eventDays: Int = 0, val reportDays: Int = 0,
    val sensorHealthDays: Int = 0,
) {
    public fun validate() {
        require(parkedRoutes.size <= 100)
        parkedRoutes.keys.forEach { require(UUID.fromString(it).toString() == it) }
        listOf(readingDays, eventDays, reportDays, sensorHealthDays).forEach { require(it in 0..3650) }
    }
}

public data class AlertDelivery(val notification: Boolean, val audible: Boolean)

public fun alertDelivery(active: Boolean, route: ParkedAlertRoute, critical: Boolean): AlertDelivery =
    when {
        active || route == ParkedAlertRoute.ALL -> AlertDelivery(true, true)
        route == ParkedAlertRoute.NOTIFICATION_ONLY -> AlertDelivery(true, false)
        route == ParkedAlertRoute.CRITICAL_ONLY && critical -> AlertDelivery(true, true)
        else -> AlertDelivery(false, false)
    }

@Serializable
public data class MaintenanceWindow(
    val vehicle: String, val elapsedDeadline: Long, val wallDeadline: Double, val boot: Int,
) {
    public fun active(elapsed: Long, wall: Double, currentBoot: Int): Boolean =
        boot >= 0 && boot == currentBoot && elapsed < elapsedDeadline && wall < wallDeadline &&
            elapsedDeadline - elapsed in 1..3_600_000L && wallDeadline - wall in 0.0..3600.0
}

/** One gauge point can suggest an offset; it cannot establish a new multiplier. */
public data class GaugeComparison(val correctedKpa: Float, val differenceKpa: Float,
    val suggested: SensorCalibration?)

public fun compareGauge(rawKpa: Float, gaugeKpa: Float, calibration: SensorCalibration): GaugeComparison {
    require(rawKpa.isFinite() && rawKpa > 0 && rawKpa <= 2000)
    require(gaugeKpa.isFinite() && gaugeKpa > 0 && gaugeKpa <= 2000 && calibration.isValid())
    val corrected = calibration.pressure(Pressure(rawKpa)).kpa
    val suggested = calibration.copy(pressureOffset = gaugeKpa - rawKpa * calibration.pressureMultiplier)
    return GaugeComparison(corrected, gaugeKpa - corrected, suggested.takeIf { it.isValid() })
}

@Serializable
public data class RideWheelSummary(
    val wheel: String, val sensor: Int, val firstAt: Double, val lastAt: Double,
    val startKpa: Float, val endKpa: Float, val maxCelsius: Float, val readings: Long = 1,
    val longGaps: Int = 0,
)

@Serializable
public data class RideSummary(
    val wheels: List<RideWheelSummary>, val neverReported: List<String>, val alerts: Long,
)

public fun decodeRideSummary(text: String): RideSummary = kotlinx.serialization.json.Json.decodeFromString(text)

/** Streaming summary keeps memory proportional to wheel/sensor count, not ride length. */
public class RideSummaryBuilder(private val expected: Set<String>, private val gapSeconds: Double) {
    private val wheels = linkedMapOf<Pair<String, Int>, RideWheelSummary>()
    public fun add(reading: Tyre.Located) {
        val wheel = backupLocation(reading.location)
        val key = wheel to reading.sensorId
        val previous = wheels[key]
        if (previous != null && reading.timestamp <= previous.lastAt) return
        wheels[key] = if (previous == null) RideWheelSummary(wheel, reading.sensorId, reading.timestamp,
            reading.timestamp, reading.pressure.kpa, reading.pressure.kpa, reading.temperature.celsius)
        else previous.copy(lastAt = reading.timestamp, endKpa = reading.pressure.kpa,
            maxCelsius = maxOf(previous.maxCelsius, reading.temperature.celsius), readings = previous.readings + 1,
            longGaps = previous.longGaps + if (reading.timestamp - previous.lastAt > gapSeconds) 1 else 0)
    }
    public fun finish(alerts: Long, started: Double? = null, ended: Double? = null): RideSummary = RideSummary(
        wheels.values.map { wheel -> wheel.copy(longGaps = wheel.longGaps +
            (if (started != null && wheel.firstAt - started > gapSeconds) 1 else 0) +
            (if (ended != null && ended - wheel.lastAt > gapSeconds) 1 else 0)) },
        (expected - wheels.values.map { it.wheel }.toSet()).toList(), alerts)
}

public data class AlertObservation(
    val key: String, val vehicle: String, val vehicleName: String, val wheel: String,
    val sensor: Int?, val kind: String, val critical: Boolean,
)
