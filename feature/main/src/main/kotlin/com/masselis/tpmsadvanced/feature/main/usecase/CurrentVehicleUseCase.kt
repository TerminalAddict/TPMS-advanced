package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.vehicle.interfaces.VehicleDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.SharingStarted.Companion.Lazily
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import java.util.UUID
import java.util.UUID.randomUUID
import kotlinx.coroutines.sync.withLock

@Suppress("UnnecessaryOptInAnnotation")
@OptIn(DelicateCoroutinesApi::class, ExperimentalForInheritanceCoroutinesApi::class)
public class CurrentVehicleUseCase internal constructor(
    private val database: VehicleDatabase,
    private val stateFlow: StateFlow<VehicleComponent> = database
        .currentVehicle()
        .let { query ->
            val initialValue = VehicleComponent(query.execute())
            query
                .asChillFlow()
                .scan(initialValue) { previous, new ->
                    if (new.uuid != previous.vehicle.uuid) VehicleComponent(new)
                    else previous
                }
                .stateIn(GlobalScope, Lazily, initialValue)
        }
) : StateFlow<VehicleComponent> by stateFlow {

    public suspend fun setAsCurrent(uuid: UUID) {
        if (database.selectAll().execute().none { it.uuid == uuid }) return
        RideRuntime.manualSelection.value = true
        RideRuntime.selectionMutex.withLock { database.setIsCurrent(uuid, true) }
    }

    public suspend fun setAsCurrent(vehicle: Vehicle): Unit = setAsCurrent(vehicle.uuid)

    internal suspend fun insertAsCurrent(carName: String, kind: Vehicle.Kind) {
        RideRuntime.manualSelection.value = true
        RideRuntime.selectionMutex.withLock { database.insert(randomUUID(), kind, carName, true) }
    }
}
