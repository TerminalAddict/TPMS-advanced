package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import com.masselis.tpmsadvanced.core.common.dematerializeCompletion
import com.masselis.tpmsadvanced.core.common.materializeCompletion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update

/** All consumers share one radio callback. Mode switches await cancellation/cleanup of the old scan. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SharedAdvertisementScan<T>(scope: CoroutineScope, scan: (unfiltered: Boolean) -> Flow<T>) {
    private val discoveryUsers = MutableStateFlow(0)
    val readings: Flow<T> = discoveryUsers.map { it > 0 }.distinctUntilChanged()
        .flatMapLatest(scan)
        .materializeCompletion()
        .shareIn(scope, SharingStarted.WhileSubscribed())
        .dematerializeCompletion()

    fun discover(): Flow<T> = flow {
        discoveryUsers.update { it + 1 }
        try { emitAll(readings) }
        finally { discoveryUsers.update { it - 1 } }
    }
}
