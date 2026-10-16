@file:Suppress("MagicNumber", "LongMethod", "TooGenericExceptionCaught")

package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.data.vehicle.ioc.VehicleToolsDependencies
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.SensorCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.compareGauge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
@Suppress("CyclomaticComplexMethod") // Freshness, binding and calibration checks guard the optional write.
internal fun GaugeComparisonDialog(vehicle: Vehicle, sensor: Sensor, dismiss: () -> Unit) {
    val tools = remember { appGraph as VehicleToolsDependencies }
    val scope = rememberCoroutineScope()
    val unit by tools.toolsUnits.pressure.collectAsState()
    var reading by remember { mutableStateOf<Tyre.Located?>(null) }
    var calibration by remember { mutableStateOf(SensorCalibration()) }
    var gauge by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    suspend fun capture() {
        reading = withContext(Dispatchers.IO) {
            tools.toolsTyres.latestBySensorByVehicle(sensor.id, sensor.location, vehicle.uuid).execute()
        }
        calibration = tools.toolsCalibration.values.value[sensor.id] ?: SensorCalibration()
    }
    LaunchedEffect(sensor.id) { capture() }
    val sample = reading
    val gaugeKpa = gauge.replace(',', '.').toFloatOrNull()?.div(Pressure(1f).convert(unit))
    val comparison = remember(sample, gaugeKpa, calibration) { runCatching {
        compareGauge(checkNotNull(sample).pressure.kpa, checkNotNull(gaugeKpa), calibration)
    }.getOrNull() }
    MonitoringToolDialog("${wheelLabel(sensor.location)} · Gauge comparison", dismiss) {
        Text("Compare at the same tyre temperature, while parked. Take the gauge measurement and capture a " +
            "fresh sensor reading before changing pressure. A single comparison can suggest an offset only.")
        Text("Sensor ${sensor.id.toUInt()}")
        sample?.let { Text("Captured ${reportTime(it.timestamp)}\nRaw ${it.pressure.string(unit)} · " +
            "Calibrated ${calibration.pressure(it.pressure).string(unit)}") }
            ?: Text("No reading yet. Wait for a sensor broadcast.")
        TextButton(onClick = { scope.launch { capture() } }) { Text("Capture latest reading") }
        OutlinedTextField(gauge, { gauge = it }, label = { Text("Trusted gauge reading (${unit.name})") },
            singleLine = true)
        comparison?.let { result ->
            Text("Gauge minus sensor: ${Pressure(result.differenceKpa).string(unit)}")
            result.suggested?.let { suggestion ->
                Text("Suggested offset: ${Pressure(suggestion.pressureOffset).string(unit)}; multiplier stays " +
                    "${suggestion.pressureMultiplier}.")
                TextButton(enabled = sample != null && !sample.isAlarm && now() - sample.timestamp in 0.0..120.0,
                    onClick = { confirm = true }) { Text("Review calibration change…") }
            } ?: Text("Difference exceeds the supported calibration range. Check the sensor and gauge.")
        }
        Text("Applying requires a reading less than two minutes old. Historical raw readings are preserved.")
        Text(message)
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Apply suggested offset?") },
        text = { Text("Use this gauge comparison for sensor ${sensor.id.toUInt()}? Existing pressure multiplier " +
            "and temperature calibration stay unchanged.") },
        confirmButton = { TextButton(onClick = {
            confirm = false
            val selected = sample ?: return@TextButton
            val suggested = comparison?.suggested ?: return@TextButton
            scope.launch {
                try {
                    check(now() - selected.timestamp in 0.0..120.0 && !selected.isAlarm) {
                        "Capture a fresh reading first" }
                    val stillBound = withContext(Dispatchers.IO) {
                        tools.toolsSensors.selectByVehicleAndLocation(vehicle.uuid, sensor.location).execute()
                    }
                    check(stillBound == sensor) { "The assigned sensor changed. Reopen this comparison." }
                    check((tools.toolsCalibration.values.value[sensor.id] ?: SensorCalibration()) == calibration) {
                        "Calibration changed. Capture another comparison."
                    }
                    tools.toolsCalibration.set(sensor.id, suggested)
                    calibration = suggested; message = "Calibration updated"
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { message = failure.message ?: "Could not update calibration" }
            }
        }) { Text("Apply offset") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}
