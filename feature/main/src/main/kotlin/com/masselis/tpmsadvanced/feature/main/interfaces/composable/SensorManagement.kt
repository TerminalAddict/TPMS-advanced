package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.data.vehicle.ioc.VehicleToolsDependencies
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorIdentification
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("LongMethod", "CyclomaticComplexMethod", "TooGenericExceptionCaught")
@Composable
internal fun SensorManagementDialog(vehicle: Vehicle, location: Location, dismiss: () -> Unit) {
    val tools = remember { appGraph as VehicleToolsDependencies }
    val bindings by remember(vehicle.uuid) { tools.toolsSensors.selectListByVehicleId(vehicle.uuid).asFlow() }
        .collectAsState(emptyList())
    val bound = bindings.firstOrNull { it.location == location }
    var calibrate by remember { mutableStateOf(false) }
    var gauge by remember { mutableStateOf(false) }
    var replacing by remember { mutableStateOf<Sensor?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf<Map<Int, Pair<Tyre, Boolean>>>(emptyMap()) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var action by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    fun confirm(text: String, run: suspend () -> Unit) { confirmation = text; action = run }
    fun assign(id: Int) {
        val old = replacing ?: bound
        if (replacing?.id == id) { error = "Choose a different replacement sensor."; return }
        scope.launch {
            val (owner, source) = withContext(Dispatchers.IO) {
                tools.toolsVehicles.selectBySensorId(id).execute() to tools.toolsSensors.selectById(id).execute()
            }
            confirm("Assign sensor ${id.toUInt()} to ${wheelLabel(location)} on ${vehicle.name}? " +
                (if (old != null) "This replaces sensor ${old.id.toUInt()}. Its historical readings and identity " +
                    "are kept. The new sensor uses its own calibration; the old calibration is not copied. " else "") +
                (owner?.let { "It will be removed from its current wheel on ${it.name}." } ?: "")) {
                tools.toolsSensors.assign(Sensor(id, location), vehicle.uuid, old,
                    if (owner != null && source != null) owner.uuid to source else null)
                replacing = null
                status = "Sensor assigned. Previous readings remain in history."
            }
        }
    }
    LaunchedEffect(scanning, lifecycle) {
        if (scanning && lifecycle.isAtLeast(Lifecycle.State.RESUMED)) {
            val identification = SensorIdentification()
            try {
                tools.toolsScanner.highDutyScan().collect { record ->
                    if (record is Tyre) candidates = (candidates +
                        (record.sensorId to (record to identification.observe(record))))
                        .entries.sortedByDescending { it.value.first.timestamp }.take(100).associate { it.toPair() }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: RuntimeException) { error = "Scanning stopped. Check Bluetooth and permissions, then try again." }
        }
    }
    if (gauge && bound != null) {
        GaugeComparisonDialog(vehicle, bound) { gauge = false }
    } else if (calibrate && bound != null) {
        CalibrationEditor(bound.id, tools.toolsCalibration) { calibrate = false }
    } else AlertDialog(onDismissRequest = dismiss, title = { Text("${vehicle.name}: ${wheelLabel(location)}") },
        modifier = Modifier.testTag(BindSensorTags.Dialog.root),
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                replacing?.let { old ->
                    Text("Replace sensor · Step 2 of 3", style = MaterialTheme.typography.titleMedium)
                    Text("Old sensor ${old.id.toUInt()} stays assigned until you confirm its replacement. " +
                        "Fit only the replacement sensor, then identify its broadcasts or enter its ID below. " +
                        "Existing readings, vehicle settings and maintenance records are retained.")
                    TextButton(onClick = { replacing = null; scanning = false }) { Text("Cancel replacement") }
                }
                bound?.takeIf { replacing == null }?.let { sensor ->
                    Text("Assigned sensor: ${sensor.id.toUInt()}")
                    TextButton(onClick = { confirm("Replace sensor ${sensor.id.toUInt()} on ${wheelLabel(location)}? " +
                        "Step 1 of 3: review the old sensor. Next, identify its replacement. " +
                        "Use maintenance mode while removing and fitting sensors.") {
                        replacing = sensor; scanning = true; candidates = emptyMap(); manual = ""
                    } }) { Text("Replace sensor…") }
                    TextButton(onClick = { gauge = true; scanning = false }) { Text("Compare with pressure gauge") }
                    TextButton(onClick = { calibrate = true }) { Text("Calibrate sensor") }
                    TextButton(onClick = { confirm("Remove sensor ${sensor.id.toUInt()} from this wheel?") {
                        tools.toolsSensors.unbind(vehicle.uuid, location)
                    } }) { Text("Unassign sensor") }
                    Text("Move / swap with:")
                    vehicle.kind.locations.filter { it != location }.forEach { destination ->
                        val occupied = bindings.any { it.location == destination }
                        TextButton(onClick = {
                            val moves = if (occupied) mapOf(location to destination, destination to location)
                                else mapOf(location to destination)
                            confirm("${if (occupied) "Swap" else "Move"} ${wheelLabel(location)} " +
                                "${if (occupied) "↔" else "→"} ${wheelLabel(destination)}? " +
                                "Calibration follows each sensor.") {
                                tools.toolsSensors.move(vehicle.uuid, bindings, moves)
                            }
                        }) { Text(wheelLabel(destination)) }
                    }
                    if (bindings.size >= 3) TextButton(onClick = {
                        val order = vehicle.kind.locations.filter { wheel -> bindings.any { it.location == wheel } }
                        val moves = order.mapIndexed { index,
                            wheel -> wheel to order[(index + 1) % order.size] }.toMap()
                        confirm("Rotate assignments:\n" + moves.entries.joinToString("\n") {
                            "${wheelLabel(it.key)} → ${wheelLabel(it.value)}"
                        }) { tools.toolsSensors.move(vehicle.uuid, bindings, moves) }
                    }) { Text("Rotate all assigned sensors…") }
                }
                TextButton(onClick = { scanning = !scanning; candidates = emptyMap() }) {
                    Text(if (scanning) "Stop identifying" else "Identify sensor via Bluetooth")
                }
                if (scanning) {
                    Text("While parked, remove and refit the sensor you want. A pressure change highlights " +
                        "a candidate. Verify its ID before assigning; nearby vehicles can also appear.")
                    candidates.values.filter { now() - it.first.timestamp < 30 }.sortedByDescending { it.second }
                        .forEach { (reading, changed) ->
                            TextButton(onClick = { assign(reading.sensorId) }) {
                                Text("${if (changed) "Pressure changed · " else "Nearby · "}" +
                                    "${reading.sensorId.toUInt()} " +
                                    "${reading.pressure.kpa.toInt()} kPa · ${reading.rssi} dBm")
                            }
                        }
                    if (candidates.isEmpty()) Text("Waiting for a sensor broadcast…")
                }
                OutlinedTextField(manual, { manual = it }, singleLine = true,
                    modifier = Modifier.testTag(BindSensorTags.Dialog.manualId),
                    label = { Text("Sensor ID (decimal or 0x hex)") })
                val id = if (manual.startsWith("0x", true)) manual.drop(2).toUIntOrNull(16)?.toInt()
                    else manual.toUIntOrNull()?.toInt()
                TextButton(enabled = id != null, onClick = { id?.let(::assign) },
                    modifier = Modifier.testTag(BindSensorTags.Dialog.assign)) { Text("Assign entered ID…") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                status?.let { Text(it) }
            }
        }, confirmButton = {
            TextButton(onClick = dismiss, modifier = Modifier.testTag(BindSensorTags.Dialog.done)) { Text("Done") }
        })
    confirmation?.let { message ->
        AlertDialog(onDismissRequest = { confirmation = null }, title = { Text("Confirm sensor change") },
            text = { Text(message) }, confirmButton = { TextButton(
                modifier = Modifier.testTag(BindSensorTags.Dialog.apply), onClick = {
                val pending = action
                confirmation = null
                scope.launch {
                    try { scanning = false; pending?.invoke() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: RuntimeException) { error = failure.message ?: "Sensor change could not be saved." }
                }
            }) { Text("Apply") } },
                dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } })
    }
}

internal fun wheelLabel(location: Location): String = location.toString().lowercase().replace('_', ' ')

@Composable
public fun SensorManagementSettings(vehicle: Vehicle) {
    var selected by remember { mutableStateOf<Location?>(null) }
    Text("Sensors", style = MaterialTheme.typography.titleMedium)
    SensorDiscoveryButton(vehicle)
    Text("Assign, calibrate, move, swap or rotate sensor assignments.")
    vehicle.kind.locations.forEach { wheel ->
        TextButton(onClick = { selected = wheel }) { Text(wheelLabel(wheel)) }
    }
    selected?.let { SensorManagementDialog(vehicle, it) { selected = null } }
}
