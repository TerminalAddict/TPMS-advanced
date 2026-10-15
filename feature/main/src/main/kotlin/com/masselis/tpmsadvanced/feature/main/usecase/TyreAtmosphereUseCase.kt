package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.TyreAtmosphere
import com.masselis.tpmsadvanced.data.vehicle.model.SensorCalibration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

public class TyreAtmosphereUseCase internal constructor(
    private val listenTyreUseCase: ListenTyreUseCase,
    private val detectLeaks: StateFlow<Boolean> = MutableStateFlow(false),
    private val calibrations: StateFlow<Map<Int, SensorCalibration>> = MutableStateFlow(emptyMap()),
) {
    public fun listen(): Flow<TyreAtmosphere> = listenTyreUseCase
        .listen()
        .combine(combine(detectLeaks, calibrations) { enabled, values -> enabled to values }) { record, settings ->
            val (enabled, values) = settings
            val calibration = values[record.sensorId] ?: SensorCalibration()
            TyreAtmosphere(
                record.timestamp,
                record.sensorId,
                if (record.isAlarm) 0f.kpa else calibration.pressure(record.pressure),
                calibration.temperature(record.temperature),
                record.sensorBattery,
                record.pressureLoss.takeIf { enabled },
            )
        }
}
