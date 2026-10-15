package com.masselis.tpmsadvanced.feature.background.interfaces

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** A silent tyre must not hold back the other tyres' alerts. */
internal inline fun <reified T : Any> List<Flow<T>>.partialReadings(): Flow<List<T>> =
    combine(map { source -> source.map<T, T?> { it }.onStart { emit(null) } }) { readings ->
        readings.filterNotNull()
    }
