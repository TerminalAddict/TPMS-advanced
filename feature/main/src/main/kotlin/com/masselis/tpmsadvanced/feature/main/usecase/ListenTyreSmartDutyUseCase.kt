package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import kotlinx.coroutines.flow.Flow

internal class ListenTyreSmartDutyUseCase(
    private val scanner: LocatedTyreScannerUseCase,
) : ListenTyreUseCase {
    // Keep a single subscription: cancelling after the first reading can stop the platform scan
    // and introduce a delayed restart before monitoring begins.
    override fun listen(): Flow<Tyre.Located> = scanner.normalScan()
}
