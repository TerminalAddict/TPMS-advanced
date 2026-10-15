package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.content.Context
import kotlinx.coroutines.flow.Flow

/** Release builds contain no mock receiver or packet generator. */
@Suppress("UnusedParameter")
internal fun Flow<ScanResult>.withMockAdvertisements(context: Context, filters: List<ScanFilter>): Flow<ScanResult> = this
