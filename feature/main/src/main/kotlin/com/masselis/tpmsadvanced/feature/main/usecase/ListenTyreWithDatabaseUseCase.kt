package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.core.common.dematerializeCompletion
import com.masselis.tpmsadvanced.core.common.materializeCompletion
import com.masselis.tpmsadvanced.data.vehicle.interfaces.TyreDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.PressureLossTracker
import com.masselis.tpmsadvanced.data.vehicle.model.SensorCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn

internal interface ListenTyreWithDatabaseUseCase : ListenTyreUseCase {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Suppress("LongParameterList")
    class Impl(
        vehicle: Vehicle,
        location: Location,
        tyreDatabase: TyreDatabase,
        listenTyreUseCase: ListenTyreUseCase,
        scope: CoroutineScope,
        sensorBindingUseCase: SensorBindingUseCase = SensorBindingUseCase.NoOp,
        detectLeaks: StateFlow<Boolean> = MutableStateFlow(false),
        calibrations: StateFlow<Map<Int, SensorCalibration>> = MutableStateFlow(emptyMap()),
    ) : ListenTyreWithDatabaseUseCase {

        private var latestReading: Tyre.Located? = null
        private val flow = combine(detectLeaks, calibrations) { enabled, values -> enabled to values }
            .onStart { latestReading = null }.flatMapLatest { (enabled, values) ->
            val tracker = PressureLossTracker()
            listenTyreUseCase.listen().map { tyre ->
                val calibration = values[tyre.sensorId] ?: SensorCalibration()
                val corrected = tyre.copy(pressure = calibration.pressure(tyre.pressure),
                    temperature = calibration.temperature(tyre.temperature))
                tyre.copy(pressureLoss = if (enabled) tracker.next(corrected) else null)
            }
                .onEach { tyre ->
                    latestReading = tyre
                    FleetHealth.recordLeak(vehicle.uuid, tyre)
                    tyreDatabase.insert(tyre, vehicle.uuid)
                }
                .onStart {
                    // Settings changes clear the previous session's warning without writing a fake broadcast.
                    latestReading = latestReading?.copy(pressureLoss = null)
                    latestReading?.let { FleetHealth.recordLeak(vehicle.uuid, it) }
                    val bound = sensorBindingUseCase.boundSensor().value
                    latestReading?.takeIf {
                        if (bound == null) !sensorBindingUseCase.isBound(it.sensorId) else bound.id == it.sensorId
                    }?.also { emit(it) }
                }
        }
            .materializeCompletion()
            .shareIn(scope, WhileSubscribed())
            .dematerializeCompletion()
            .onStart {
                val bound = sensorBindingUseCase.boundSensor().value
                (if (bound == null) tyreDatabase.latestByTyreLocationByVehicle(location, vehicle.uuid)
                else tyreDatabase.latestBySensorByVehicle(bound.id, location, vehicle.uuid))
                    .execute()
                    ?.takeIf { bound != null || !sensorBindingUseCase.isBound(it.sensorId) }
                    ?.also { emit(it) }
            }
            .flowOn(Dispatchers.IO)

        override fun listen(): Flow<Tyre.Located> = flow
    }

    class Wrapper(private val source: ListenTyreUseCase) : ListenTyreWithDatabaseUseCase {
        override fun listen(): Flow<Tyre.Located> = source.listen()
    }
}
