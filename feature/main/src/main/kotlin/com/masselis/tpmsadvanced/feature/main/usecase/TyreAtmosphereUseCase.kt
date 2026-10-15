package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.TyreAtmosphere
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

public class TyreAtmosphereUseCase internal constructor(
    private val listenTyreUseCase: ListenTyreUseCase,
    private val detectLeaks: StateFlow<Boolean> = MutableStateFlow(false),
) {
    public fun listen(): Flow<TyreAtmosphere> = listenTyreUseCase
        .listen()
        .combine(detectLeaks) { record, enabled ->
            TyreAtmosphere(
                record.timestamp,
                record.sensorId,
                if (record.isAlarm) 0f.kpa else record.pressure,
                record.temperature,
                record.sensorBattery,
                record.pressureLoss.takeIf { enabled },
            )
        }
}
