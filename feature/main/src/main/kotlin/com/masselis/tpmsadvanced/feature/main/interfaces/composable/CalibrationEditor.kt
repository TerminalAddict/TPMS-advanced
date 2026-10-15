package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.masselis.tpmsadvanced.data.vehicle.interfaces.CalibrationRepository
import com.masselis.tpmsadvanced.data.vehicle.model.SensorCalibration

@Composable
internal fun CalibrationEditor(sensorId: Int, repository: CalibrationRepository, dismiss: () -> Unit) {
    val saved = remember(sensorId) { repository.values.value[sensorId] ?: SensorCalibration() }
    CalibrationForm(sensorId, saved, { repository.set(sensorId, it); dismiss() }, dismiss)
}

@Composable
internal fun CalibrationForm(
    sensorId: Int,
    saved: SensorCalibration,
    apply: (SensorCalibration) -> Unit,
    dismiss: () -> Unit,
) {
    var pressureOffset by remember { mutableStateOf(saved.pressureOffset.toString()) }
    var pressureMultiplier by remember { mutableStateOf(saved.pressureMultiplier.toString()) }
    var temperatureOffset by remember { mutableStateOf(saved.temperatureOffset.toString()) }
    var temperatureMultiplier by remember { mutableStateOf(saved.temperatureMultiplier.toString()) }
    val draft = SensorCalibration(pressureOffset.toFloatOrNull() ?: Float.NaN,
        pressureMultiplier.toFloatOrNull() ?: Float.NaN, temperatureOffset.toFloatOrNull() ?: Float.NaN,
        temperatureMultiplier.toFloatOrNull() ?: Float.NaN)
    AlertDialog(onDismissRequest = dismiss, title = { Text("Calibrate sensor ${sensorId.toUInt()}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Corrected = raw × multiplier + offset. Calibration follows this sensor when moved. " +
                    "Raw history is preserved. Cancel discards this draft.")
                OutlinedTextField(pressureOffset, { pressureOffset = it },
                    label = { Text("Pressure offset (kPa, −100 to 100)") })
                OutlinedTextField(pressureMultiplier, { pressureMultiplier = it },
                    label = { Text("Pressure multiplier (0.5–1.5)") })
                OutlinedTextField(temperatureOffset, { temperatureOffset = it },
                    label = { Text("Temperature offset (°C, −30 to 30)") })
                OutlinedTextField(temperatureMultiplier, { temperatureMultiplier = it },
                    label = { Text("Temperature multiplier (0.5–1.5)") })
                TextButton(onClick = { pressureOffset = "0"; pressureMultiplier = "1";
                    temperatureOffset = "0"; temperatureMultiplier = "1" }) { Text("Reset draft to uncalibrated") }
                if (!draft.isValid()) Text("Enter finite values within the ranges shown.")
            }
        },
        confirmButton = { TextButton(enabled = draft.isValid(), onClick = { apply(draft) }) {
            Text("Apply to this sensor")
        } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
