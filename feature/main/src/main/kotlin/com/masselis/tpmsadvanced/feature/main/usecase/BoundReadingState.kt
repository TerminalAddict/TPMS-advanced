package com.masselis.tpmsadvanced.feature.main.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** Used immediately before stateIn: keep its cached state on return, clear it only on a new binding. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> Flow<T>.forBinding(binding: SensorBindingUseCase, empty: T): Flow<T> {
    var previousId: Int? = null
    return binding.boundSensor().map { it?.id }.distinctUntilChanged().flatMapLatest { id ->
        this@forBinding.onStart {
            if (id != previousId) emit(empty)
            previousId = id
        }
    }
}
