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
    maintenance: Flow<Boolean> = kotlinx.coroutines.flow.flowOf(false),
): Flow<State> = combine(
    combine(this, maintenance) { readings, suppressed -> readings to suppressed },
    ranges.highTemp, ranges.lowPressure, ranges.highPressure, ranges.rearPressuresIfSeparated()
) { input, highTemp, lowPressure, highPressure, rearPressures ->
    val (readings, suppressed) = input
    readings.firstOrNull { (location, reading) ->
        val isRear = when (location) {
            is Location.Auxiliary -> location == Location.Auxiliary.SPARE
            is Location.Axle -> location == Location.Axle.REAR
            is Location.Wheel -> location.toAxle() == Location.Axle.REAR
            is Location.Side -> false
        }
        val (low, high) = if (isRear && rearPressures != null) rearPressures else lowPressure to highPressure
        !suppressed && reading.pressure !in low..high
    }?.second?.let(State::PressureAlert)
        ?: readings.firstOrNull { it.second.temperature > highTemp }?.second?.let(State::TemperatureAlert)
        ?: readings.firstOrNull { !suppressed && it.second.pressureLoss != null }?.second?.let(State::PressureLossAlert)
        ?: readings.firstOrNull { it.second.sensorBattery?.isLow == true }?.second?.let(State::BatteryAlert)
        ?: State.NoAlert
}
