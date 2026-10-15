package com.masselis.tpmsadvanced.feature.qrcode.usecase

import androidx.camera.view.CameraController
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.feature.main.usecase.CurrentVehicleUseCase
import com.masselis.tpmsadvanced.feature.qrcode.interfaces.CameraAnalyser
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeSensors
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

internal class QrCodeSensorUseCase(
    private val cameraAnalyser: CameraAnalyser,
    private val currentVehicleUseCase: CurrentVehicleUseCase,
) {
    fun analyse(controller: CameraController): Flow<Pair<QrCodeSensors, Set<Location>>> = cameraAnalyser
        .findQrCode(controller)
        .map(QrSensorParser::parse)
        .combine(currentVehicleUseCase.map { it.vehicle.kind }.distinctUntilChanged()) { sensors, kind ->
            val assigned = sensors.forVehicle(kind)
            sensors to kind.locations.subtract(assigned.map { it.location }.toSet())
        }.flowOn(Default)

    data class SingleSensorQrCode(val sensorId: Int) : IllegalArgumentException()
}
