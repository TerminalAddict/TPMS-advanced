package com.masselis.tpmsadvanced.feature.background.interfaces

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Duration.Companion.seconds

/** A new connection cancels the pending stop, while initial waiting is reported immediately. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun Flow<RidePresence>.withDisconnectGrace(isMonitoring: () -> Boolean): Flow<RidePresence> =
    transformLatest { presence ->
        if (!presence.present && isMonitoring()) delay(20.seconds)
        emit(presence)
    }
