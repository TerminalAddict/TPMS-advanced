package com.masselis.tpmsadvanced.data.vehicle.interfaces

import android.content.Context
import androidx.core.content.edit
import com.masselis.tpmsadvanced.data.vehicle.model.SensorCalibration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Calibration belongs to the physical sensor, independent of its current vehicle or wheel. */
public class CalibrationRepository(context: Context) {
    private val preferences = context.getSharedPreferences("SENSOR_CALIBRATION", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(read())
    public val values: StateFlow<Map<Int, SensorCalibration>> = mutable.asStateFlow()

    public fun set(sensorId: Int, calibration: SensorCalibration) {
        require(calibration.isValid()) { "Calibration values are outside the supported range" }
        preferences.edit(commit = true) {
            putFloat("$sensorId.po", calibration.pressureOffset)
            putFloat("$sensorId.pm", calibration.pressureMultiplier)
            putFloat("$sensorId.to", calibration.temperatureOffset)
            putFloat("$sensorId.tm", calibration.temperatureMultiplier)
        }
        reload()
    }

    public fun reload() { mutable.value = read() }

    private fun read(): Map<Int, SensorCalibration> = preferences.all.keys.mapNotNull {
        it.substringBefore('.').toIntOrNull()
    }.distinct().associateWith { id ->
        SensorCalibration(preferences.getFloat("$id.po", 0f), preferences.getFloat("$id.pm", 1f),
            preferences.getFloat("$id.to", 0f), preferences.getFloat("$id.tm", 1f))
            .takeIf { it.isValid() } ?: SensorCalibration()
    }
}
