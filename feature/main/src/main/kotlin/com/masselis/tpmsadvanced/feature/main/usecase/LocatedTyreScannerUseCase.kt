package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.ScannerRecord
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

internal class LocatedTyreScannerUseCase(
    private val source: BluetoothLeScanner,
    private val currentLocation: Location,
    private val sensorBindingUseCase: SensorBindingUseCase,
) {
    fun highDutyScan(): Flow<Tyre.Located> = source.highDutyScan().mapWithLocation()

    fun normalScan(): Flow<Tyre.Located> = source.normalScan().mapWithLocation()

    @Suppress("CyclomaticComplexMethod")
    private fun Flow<ScannerRecord>.mapWithLocation() = this
        .mapNotNull { it as? Tyre }
        .mapNotNull { tyreRecord ->
            val bound = sensorBindingUseCase.boundSensor().value
            if (bound != null) {
                return@mapNotNull tyreRecord
                    .takeIf { it.sensorId == bound.id && bound.location == currentLocation }
                    ?.let { Tyre.Located(it, currentLocation) }
            }
            // Embedded wheel positions must not leak a sensor into other vehicles or locations.
            if (sensorBindingUseCase.isBound(tyreRecord.sensorId)) return@mapNotNull null
            when (tyreRecord) {
                is Tyre.Located ->
                    when (currentLocation) {
                        is Location.Auxiliary -> null // Additional slots require an explicit binding.
                        is Location.Wheel ->
                            tyreRecord.location

                        is Location.Axle -> when (tyreRecord.location) {
                            is Location.Wheel -> (tyreRecord.location as Location.Wheel).toAxle()
                            is Location.Axle -> tyreRecord.location
                            is Location.Side, is Location.Auxiliary -> null
                        }

                        is Location.Side -> when (tyreRecord.location) {
                            is Location.Wheel -> (tyreRecord.location as Location.Wheel).toSide()
                            is Location.Axle, is Location.Auxiliary -> null
                            is Location.Side -> tyreRecord.location
                        }
                    }

                is Tyre.Unlocated -> null
            }.let { locationToCompare ->
                if (locationToCompare == currentLocation)
                    Tyre.Located(tyreRecord, currentLocation)
                else
                    null
            }
        }
}
