package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import android.bluetooth.le.ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.Failure
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Recovery policy inspired by persistent-tpms PRs #83 and #85, adapted to our shared scan. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> Flow<ScanEvent<T>>.recoveringScan(
    bluetoothEnabled: Flow<Boolean>,
    timeSource: TimeSource = TimeSource.Monotonic,
    onStatus: (ScanStatus) -> Unit,
): Flow<T> = bluetoothEnabled.distinctUntilChanged().flatMapLatest { enabled ->
    channelFlow {
        if (!enabled) {
            onStatus(ScanStatus.WaitingForBluetooth)
            awaitCancellation()
        }
        var retryDelay = 5.seconds
        var failingSince: TimeMark? = null
        onStatus(ScanStatus.Starting)
        while (isActive) {
            var startedAt: TimeMark? = null
            var settled: Job? = null
            try {
                this@recoveringScan.collect { event ->
                    when (event) {
                        ScanEvent.Started -> {
                            startedAt = timeSource.markNow()
                            // A returned startScan call can still fail via its async callback.
                            settled = launch {
                                delay(10.seconds)
                                failingSince = null
                                onStatus(ScanStatus.Scanning)
                            }
                        }
                        is ScanEvent.Reading -> send(event.value)
                    }
                }
                return@channelFlow
            } catch (cause: Failure) {
                val reason = cause.retryReason()
                settled?.cancel()
                if (startedAt?.elapsedNow()?.let { it >= 2.minutes } == true) retryDelay = 5.seconds
                val since = failingSince ?: timeSource.markNow().also { failingSince = it }
                onStatus(ScanStatus.Retrying(reason, since))
                delay(retryDelay)
                retryDelay = (retryDelay * 2).coerceAtMost(1.minutes)
            } finally {
                settled?.cancel()
            }
        }
    }
}.onCompletion { cause ->
    onStatus(
        if (cause == null || cause is CancellationException) ScanStatus.Idle
        else ScanStatus.Failed(cause)
    )
}

private fun Failure.retryReason(): Int? = when (this) {
    is Failure.ScannerIsNull -> null
    is Failure.Scan -> if (reason == SCAN_FAILED_FEATURE_UNSUPPORTED) throw this else reason
}
