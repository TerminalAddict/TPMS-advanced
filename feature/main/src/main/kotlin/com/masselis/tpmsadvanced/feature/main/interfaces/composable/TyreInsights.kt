@file:Suppress("LongMethod", "MagicNumber")

package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.data.vehicle.ioc.VehicleToolsDependencies
import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorReception
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.SensorCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.thermalChange
import com.masselis.tpmsadvanced.data.vehicle.model.ColdBaseline
import com.masselis.tpmsadvanced.data.vehicle.model.ColdSample
import com.masselis.tpmsadvanced.data.vehicle.model.backupLocation
import com.masselis.tpmsadvanced.feature.main.usecase.FleetHealth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun TyreInsights(vehicle: Vehicle, close: () -> Unit) {
    val deps = remember { appGraph as VehicleToolsDependencies }
    val data by deps.rideTools.values.collectAsState()
    val fleet by FleetHealth.values.collectAsState()
    val calibrations by deps.toolsCalibration.values.collectAsState()
    val health = fleet.firstOrNull { it.vehicle.uuid == vehicle.uuid }
    val key = vehicle.uuid.toString()
    var threshold by remember(key) { mutableStateOf(data.insights.imbalanceKpa[key]?.toString() ?: "20") }
    var message by remember { mutableStateOf("") }
    var confirmCold by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    RideToolsDialog("Tyre insights · ${vehicle.name}", close) {
        Text("Left / right pressure imbalance", style = MaterialTheme.typography.titleMedium)
        Text("Compare matching tyres on each axle using calibrated readings within 2 minutes of now and " +
            "within 1 minute of each other. A warning can occur while both tyres are within their limits. " +
            "Audible warnings require active monitoring and pause while Parked.")
        ToolsField("Difference threshold · kPa (5–200)", threshold) { threshold = it }
        FlowRow {
            TextButton(onClick = { runCatching {
                val value = threshold.toFloat(); require(value in 5f..200f)
                deps.rideTools.update { it.copy(insights = it.insights.copy(
                    imbalanceKpa = it.insights.imbalanceKpa + (key to value))) }
                scope.launch { FleetHealth.snapshot() }; message = "Imbalance warning enabled."
            }.onFailure { message = "Enter a difference from 5 to 200 kPa." } }) { Text("Enable / update") }
            TextButton(onClick = {
                deps.rideTools.update {
                    it.copy(insights = it.insights.copy(imbalanceKpa = it.insights.imbalanceKpa - key))
                }
                scope.launch { FleetHealth.snapshot() }; message = "Imbalance warning disabled."
            }) { Text("Disable") }
        }
        Text(data.insights.imbalanceKpa[key]?.let { "Enabled · $it kPa" } ?: "Disabled")
        health?.imbalances?.forEach { Text("${it.axle}: ${it.differenceKpa} kPa difference",
            color = MaterialTheme.colorScheme.error) }
        Text("Cold versus hot", style = MaterialTheme.typography.titleMedium)
        Text("Only confirm a baseline when stationary with tyres cooled to ambient temperature. " +
            "The first transmission alone cannot establish that a tyre is cold. " +
            "Comparisons expire after 12 hours; confirm a new baseline for each ride.")
        Button(onClick = { confirmCold = true }) { Text("Confirm current readings as cold") }
        val baseline = data.insights.cold[key]
        if (baseline == null) Text("No confirmed cold baseline.") else {
            Text("Confirmed ${java.time.Instant.ofEpochSecond(baseline.confirmedAt.toLong())}")
            health?.wheels?.forEach { wheel ->
                val change = wheel.reading?.let { thermalChange(baseline, wheel.location, it,
                    calibrations[it.sensorId] ?: SensorCalibration(), now()) }
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text(wheel.label, style = MaterialTheme.typography.titleMedium)
                    if (change == null) Text("Waiting for a newer, fresh reading from the same sensor; " +
                        "expired baselines need reconfirmation.") else {
                        Text("Cold: ${change.coldPressure.kpa} kPa · ${change.coldTemperature.celsius} °C")
                        Text("Now: ${wheel.reading?.pressure?.kpa} kPa · ${wheel.reading?.temperature?.celsius} °C")
                        Text("Change: %+.1f kPa · %+.1f °C".format(
                            change.pressureRiseKpa, change.temperatureRiseCelsius))
                    }
                } }
            }
            TextButton(onClick = { deps.rideTools.update {
                it.copy(insights = it.insights.copy(cold = it.insights.cold - key))
            } }) {
                Text("Clear baseline")
            }
        }
        Text(message)
    }
    if (confirmCold) AlertDialog(onDismissRequest = { confirmCold = false }, title = { Text("Are all tyres cold?") },
        text = { Text("Confirm only after the tyres have cooled. Every expected wheel must have a bound sensor " +
            "and a positive reading received within the last 2 minutes. This replaces the previous baseline.") },
        confirmButton = { TextButton(onClick = {
            confirmCold = false
            scope.launch {
                runCatching { captureColdBaseline(vehicle, deps) }.onSuccess { baseline ->
                    deps.rideTools.update {
                        it.copy(insights = it.insights.copy(cold = it.insights.cold + (key to baseline)))
                    }
                    message = "Cold baseline saved. Later readings will show changes here."
                }.onFailure { message = "Cannot capture: bind every wheel and wait for fresh, positive readings." }
            }
        }) { Text("Tyres are cold · save") } }, dismissButton = {
            TextButton(onClick = { confirmCold = false }) { Text("Cancel") }
        })
}

private suspend fun captureColdBaseline(vehicle: Vehicle, deps: VehicleToolsDependencies): ColdBaseline =
    withContext(Dispatchers.IO) {
        val samples = vehicle.kind.locations.associate { location ->
            val binding = requireNotNull(deps.toolsSensors.selectByVehicleAndLocation(vehicle.uuid, location).execute())
            val raw = requireNotNull(deps.toolsTyres.latestBySensorByVehicle(
                binding.id, location, vehicle.uuid).execute())
            require(!raw.isAlarm && raw.pressure.kpa > 0)
            backupLocation(location) to ColdSample(raw.sensorId, raw.timestamp,
                raw.pressure.kpa, raw.temperature.celsius)
        }
        ColdBaseline(now(), samples).also { it.validate() }
    }

@Composable
internal fun ReceptionDashboard(vehicle: Vehicle, close: () -> Unit) {
    val deps = remember { appGraph as VehicleToolsDependencies }
    val data by deps.rideTools.values.collectAsState()
    val fleet by FleetHealth.values.collectAsState()
    var tick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) { while (true) { tick = SystemClock.elapsedRealtime(); delay(1000) } }
    RideToolsDialog("Sensor reception · ${vehicle.name}", close) {
        Text("Advertisement reception, not a Bluetooth connection. Counts cover this app process since reset; " +
            "statistics cover the latest 512 packets per sensor. Scan pauses are excluded from gaps. " +
            "Sleep, interference and Android batching can all affect results.")
        fleet.firstOrNull { it.vehicle.uuid == vehicle.uuid }?.wheels?.forEach { wheel ->
            val id = wheel.reading?.sensorId
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text(wheel.label, style = MaterialTheme.typography.titleMedium)
                if (id == null) Text("Waiting for a sensor reading") else {
                    val expected = data.insights.broadcastSeconds[id]
                    val quality = remember(id, expected, tick) { SensorReception.snapshot(id, expected) }
                    if (quality == null) Text("No broadcasts observed in this app process yet.") else {
                        Text("${quality.packets} packets · ${quality.windowPackets} in statistics window")
                        val mean = quality.meanRssi?.let { "%.0f".format(it) } ?: "unknown"
                        Text("RSSI: ${quality.latestRssi ?: "unknown"} dBm · mean $mean")
                        Text("Range: ${quality.minimumRssi ?: "?"} to ${quality.maximumRssi ?: "?"} dBm")
                        Text("Last observed ${(tick - quality.lastSeenMs).coerceAtLeast(0) / 1000}s ago · " +
                            if (quality.available) "scanning" else "scan paused")
                        Text("Longest observed gap: ${quality.longestGapSeconds}s")
                        ReceptionAssessment(quality)
                    }
                    ReceptionInterval(id, expected, deps)
                }
            } }
        }
        TextButton(onClick = { SensorReception.reset(); tick = SystemClock.elapsedRealtime() }) {
            Text("Reset all reception counters")
        }
    }
}

@Composable
private fun ReceptionAssessment(quality: com.masselis.tpmsadvanced.data.vehicle.model.ReceptionQuality) {
    quality.estimatedMissing?.let { missed ->
        val percent = quality.windowPackets * 100.0 / (quality.windowPackets + missed)
        Text("Estimated missing: $missed · observed share %.0f%%".format(percent))
        Text(if (missed > 0) "Intermittent at the configured interval; check placement and sensor sleep."
            else "No missing broadcasts estimated in this window.")
    }
    if ((quality.meanRssi ?: 0.0) < -90.0) Text("Weak average signal; try a closer phone position.")
}

@Composable
private fun ReceptionInterval(id: Int, expected: Int?, deps: VehicleToolsDependencies) {
    var interval by remember(id, expected) { mutableStateOf(expected?.toString() ?: "") }
    var error by remember { mutableStateOf("") }
    ToolsField("Expected broadcast interval · seconds (optional)", interval) { interval = it }
    Text("Use the sensor's known interval while awake. Estimates allow a one-interval grace period.")
    TextButton(onClick = { runCatching {
        val value = interval.takeIf { it.isNotBlank() }?.toInt()
        require(value == null || value in 1..3600)
        deps.rideTools.update { it.copy(insights = it.insights.copy(broadcastSeconds =
            if (value == null) it.insights.broadcastSeconds - id else it.insights.broadcastSeconds + (id to value))) }
        error = "Saved"
    }.onFailure { error = "Enter 1–3600 seconds, or leave blank to disable estimates." } }) { Text("Save interval") }
    Text(error)
}
