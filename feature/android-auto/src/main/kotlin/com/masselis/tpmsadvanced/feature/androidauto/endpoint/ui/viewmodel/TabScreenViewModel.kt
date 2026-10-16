package com.masselis.tpmsadvanced.feature.androidauto.endpoint.ui.viewmodel

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.TyreComponent
import com.masselis.tpmsadvanced.feature.main.usecase.CurrentVehicleUseCase
import com.masselis.tpmsadvanced.feature.main.usecase.TyreIconStateFlow
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow
import com.masselis.tpmsadvanced.feature.main.usecase.VehicleListUseCase
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flattenMerge
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.UUID

@Suppress("OPT_IN_USAGE")
@AssistedInject
internal class TabScreenViewModel(
    private val currentVehicleUseCase: CurrentVehicleUseCase,
    vehicleListUseCase: VehicleListUseCase,
    @Assisted lifecycleOwner: LifecycleOwner,
) : LifecycleOwner by lifecycleOwner {

    @AssistedFactory
    interface Factory {
        operator fun invoke(lifecycleOwner: LifecycleOwner): TabScreenViewModel
    }

    sealed interface State {
        data object Loading : State
        data class Tabs(
            val list: List<Tab.Available>,
            val displayed: Tab.Displayed,
        ) : State {
            sealed interface Tab {
                val vehicle: Vehicle

                @JvmInline
                value class Available(override val vehicle: Vehicle) : Tab

                data class Displayed(
                    override val vehicle: Vehicle,
                    val tyres: Map<Location, Pair<TyreIconStateFlow.State, TyreStatsStateFlow.State>>
                ) : Tab
            }
        }
    }

    private val mutableStateFlow = MutableStateFlow<State>(State.Loading)
    val stateFlow = mutableStateFlow.asStateFlow()

    init {
        @Suppress("MaxLineLength")
        combine(
            vehicleListUseCase.vehicleListFlow,
            currentVehicleUseCase
        ) { list, current -> list to current }
            .flatMapLatest { (list, current) ->
                if (list.none { it.uuid == current.vehicle.uuid }) return@flatMapLatest flowOf(State.Loading)
                current
                    .vehicle.kind.locations.map { current.TyreComponent(it) }
                    .map { tyreComponent ->
                        combine(
                            tyreComponent.tyreIconStateFlow,
                            tyreComponent.tyreStatsStateFlow,
                        ) { iconState, statsState ->
                            tyreComponent.location to (iconState to statsState)
                        }
                    }
                    .asFlow()
                    .flattenMerge()
                    .runningFold(emptyMap<Location, Pair<TyreIconStateFlow.State, TyreStatsStateFlow.State>>()) { acc, (location, pairOfStates) ->
                        acc + (location to pairOfStates)
                    }
                    .map {
                        State.Tabs(
                            list.map { vehicle -> State.Tabs.Tab.Available(vehicle) },
                            State.Tabs.Tab.Displayed(list.first { vehicle -> vehicle.uuid == current.vehicle.uuid }, it)
                        )
                    }
            }
            .onEach { mutableStateFlow.value = it }
            .launchIn(lifecycleScope)
    }

    fun currentVehicle(uuid: UUID) = lifecycleScope.launch {
        currentVehicleUseCase.setAsCurrent(uuid)
    }
}
