package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

/** A start is reported only after the platform call, including any rate-limit wait. */
internal sealed interface ScanEvent<out T> {
    data object Started : ScanEvent<Nothing>
    data class Reading<T>(val value: T) : ScanEvent<T>
}
