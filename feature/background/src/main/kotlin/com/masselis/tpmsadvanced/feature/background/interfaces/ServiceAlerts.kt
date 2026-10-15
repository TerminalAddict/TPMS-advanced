package com.masselis.tpmsadvanced.feature.background.interfaces

import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.TyreAtmosphere
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State
import com.masselis.tpmsadvanced.feature.main.usecase.VehicleRangesUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Re-evaluate the retained readings as soon as thresholds change, without waiting for a broadcast. */
internal fun Flow<List<Pair<Location, TyreAtmosphere>>>.alertStates(
    ranges: VehicleRangesUseCase,
): Flow<State> = combine(
    this, ranges.highTemp, ranges.lowPressure, ranges.highPressure, ranges.rearPressuresIfSeparated()
) { readings, highTemp, lowPressure, highPressure, rearPressures ->
    readings.firstOrNull { (location, reading) ->
        val isRear = when (location) {
            is Location.Axle -> location == Location.Axle.REAR
            is Location.Wheel -> location.toAxle() == Location.Axle.REAR
            is Location.Side -> false
        }
        val (low, high) = if (isRear && rearPressures != null) rearPressures else lowPressure to highPressure
        reading.pressure !in low..high
    }?.second?.let(State::PressureAlert)
        ?: readings.firstOrNull { it.second.temperature > highTemp }?.second?.let(State::TemperatureAlert)
        ?: readings.firstOrNull { it.second.pressureLoss != null }?.second?.let(State::PressureLossAlert)
        ?: readings.firstOrNull { it.second.sensorBattery?.isLow == true }?.second?.let(State::BatteryAlert)
        ?: State.NoAlert
}
