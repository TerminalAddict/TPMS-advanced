package com.masselis.tpmsadvanced.feature.qrcode.usecase

import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.feature.main.usecase.CurrentVehicleUseCase
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeSensors

internal interface BoundSensorMapUseCase {
    suspend fun bind(qrCodeSensors: QrCodeSensors)
    suspend fun bind(sensorId: Int, location: Location)

    class Impl(
        private val sensorDatabase: SensorDatabase,
        private val currentVehicleUseCase: CurrentVehicleUseCase,
    ) : BoundSensorMapUseCase {
        override suspend fun bind(sensorId: Int, location: Location) {
            sensorDatabase.upsert(Sensor(sensorId, location), currentVehicleUseCase.value.vehicle.uuid)
        }

        override suspend fun bind(qrCodeSensors: QrCodeSensors) {
            val vehicle = currentVehicleUseCase.value.vehicle
            // Validate every converted location before making any changes, then write atomically.
            sensorDatabase.upsert(qrCodeSensors.forVehicle(vehicle.kind), vehicle.uuid)
        }
    }

    object NoOp : BoundSensorMapUseCase {
        override suspend fun bind(qrCodeSensors: QrCodeSensors) {}
        override suspend fun bind(sensorId: Int, location: Location) {}
    }
}
