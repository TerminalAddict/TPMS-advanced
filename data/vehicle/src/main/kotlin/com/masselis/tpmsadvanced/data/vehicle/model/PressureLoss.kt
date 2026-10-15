package com.masselis.tpmsadvanced.data.vehicle.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
public data class PressureLoss(val drop: Pressure, val since: Double) : Parcelable

/** Experimental trend warning. Cooling can also reduce pressure; this is not a puncture diagnosis. */
public class PressureLossTracker {
    private var latest: Tyre? = null
    private var peak: Tyre? = null
    private var firstDrop: Double? = null
    private var loss: PressureLoss? = null

    public fun reset() { latest = null; peak = null; firstDrop = null; loss = null }

    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    public fun next(reading: Tyre): PressureLoss? {
        if (!reading.timestamp.isFinite() || !reading.pressure.kpa.isFinite() ||
            !reading.temperature.celsius.isFinite()) {
            reset()
            return null
        }
        val previous = latest
        if (previous != null && reading.sensorId != previous.sensorId) reset()
        else if (previous != null && reading.timestamp <= previous.timestamp) return loss
        else if (previous != null && reading.timestamp - previous.timestamp >= RIDE_GAP_SECONDS) reset()
        if (reading.pressure.kpa < OFF_VALVE_KPA) { reset(); return null }
        latest = reading
        val highest = peak
        if (highest == null || reading.pressure >= highest.pressure) {
            peak = reading
            firstDrop = null
            loss = null
            return null
        }
        val drop = highest.pressure.kpa - reading.pressure.kpa
        if (highest.temperature.celsius - reading.temperature.celsius > COOLING_DEGREES) {
            // Start a fresh baseline after cooling instead of blaming the pressure change on a leak.
            peak = reading
            firstDrop = null
            loss = null
        } else if (drop >= MIN_DROP_KPA) {
            val first = firstDrop
            if (first == null) firstDrop = reading.timestamp
            else if (reading.timestamp - first >= CONFIRM_SECONDS)
                loss = PressureLoss(Pressure(drop), highest.timestamp)
        } else {
            firstDrop = null
            loss = null
        }
        return loss
    }

    private companion object {
        const val MIN_DROP_KPA = 7f
        const val COOLING_DEGREES = 1f
        const val OFF_VALVE_KPA = 10f
        const val RIDE_GAP_SECONDS = 600.0
        const val CONFIRM_SECONDS = 5.0
    }
}
