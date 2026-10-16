@file:Suppress("LongMethod", "MagicNumber", "TooGenericExceptionCaught")

package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.data.vehicle.SensorInventory
import com.masselis.tpmsadvanced.data.vehicle.SensorInstallation
import com.masselis.tpmsadvanced.data.vehicle.ioc.VehicleToolsDependencies
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBattery
import com.masselis.tpmsadvanced.data.vehicle.model.SensorHealthDay
import com.masselis.tpmsadvanced.data.vehicle.model.SensorMetadata
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.sensorHealthFindings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
public fun SensorInventoryButton() {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text("Sensor inventory and health") }
    if (open) SensorInventoryDialog { open = false }
}

@Composable
@Suppress("CyclomaticComplexMethod") // Search, spare filtering and optional metadata are presentation branches.
public fun SensorInventoryDialog(close: () -> Unit) {
    val tools = remember { appGraph as VehicleToolsDependencies }
    var inventory by remember { mutableStateOf(emptyList<SensorInventory>()) }
    var bindings by remember { mutableStateOf(emptyMap<Int, Pair<Vehicle, Sensor>>()) }
    var search by remember { mutableStateOf("") }
    var spares by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var edit by remember { mutableStateOf<SensorMetadata?>(null) }
    var adding by remember { mutableStateOf(false) }
    var limit by remember { mutableStateOf(50) }
    var message by remember { mutableStateOf("") }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    LaunchedEffect(lifecycle, edit) {
        if (lifecycle.isAtLeast(Lifecycle.State.RESUMED)) while (isActive) {
            try {
                inventory = tools.sensorLibrary.inventory()
                bindings = withContext(Dispatchers.IO) { tools.toolsVehicles.selectAll().execute().flatMap { vehicle ->
                    tools.toolsSensors.selectListByVehicleId(vehicle.uuid).execute().map { it.id to (vehicle to it) }
                }.toMap() }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message = "Could not read the sensor library. Close and retry." }
            delay(5000)
        }
    }
    RideToolsDialog("Sensor inventory and spares", close) {
        Text("Known sensors stay here after removal or replacement. Recognised Bluetooth broadcasts are added " +
            "automatically while scanning; unknown protocols remain in Discover sensors.")
        TextButton(onClick = { adding = true; edit = SensorMetadata(0) }) { Text("Add a spare by ID") }
        OutlinedTextField(search, { search = it; limit = 50 },
            label = { Text("Search ID, label, manufacturer or vehicle") },
            modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!spares, { spares = false }, label = { Text("All (${inventory.size})") })
            FilterChip(spares, { spares = true }, label = { Text("Unassigned spares") })
        }
        val visible = inventory.filter { item -> (!spares || item.id.toInt() !in bindings) &&
            listOf(item.id.toInt().toUInt().toString(), item.label, item.manufacturer,
                bindings[item.id.toInt()]?.first?.name.orEmpty()).any { it.contains(search, true) } }
        if (visible.isEmpty()) Text("No matching sensors. Add a spare or use Discover sensors to find one.")
        visible.take(limit).forEach { item ->
            val assignment = bindings[item.id.toInt()]
            Card(onClick = { selected = item.id.toInt() }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${item.label.ifBlank { "Sensor" }} · ${item.id.toInt().toUInt()}",
                        style = MaterialTheme.typography.titleMedium)
                    Text(assignment?.let { "${it.first.name} · ${wheelLabel(it.second.location)}" }
                        ?: "Unassigned spare")
                    Text(item.manufacturer.ifBlank { item.protocol.ifBlank { "Manufacturer not recorded" } })
                    Text("Battery: ${SensorBattery.fromStored(item.voltage, item.percent)?.label() ?: "unknown"}")
                    Text("Last seen: ${item.lastSeen?.let(::reportTime) ?: "never received"}")
                    item.warning?.let { Text("Last reception warning: $it", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        if (visible.size > limit) TextButton(onClick = { limit += 50 }) { Text("Show more") }
        Text(message)
    }
    selected?.let { id -> inventory.firstOrNull { it.id.toInt() == id }?.let { item ->
        SensorInventoryDetails(item, bindings[id], { selected = null }, { adding = false; edit = it })
    } }
    edit?.let { value -> SensorMetadataEditor(value, adding,
        { edit = null }) }
}

@Composable
@Suppress("CyclomaticComplexMethod") // Optional battery units, history and assignment dialogs share this detail screen.
private fun SensorInventoryDetails(item: SensorInventory, assignment: Pair<Vehicle, Sensor>?, close: () -> Unit,
    edit: (SensorMetadata) -> Unit) {
    val tools = remember { appGraph as VehicleToolsDependencies }
    val scope = rememberCoroutineScope()
    var history by remember { mutableStateOf(emptyList<SensorInstallation>()) }
    var days by remember { mutableStateOf(emptyList<SensorHealthDay>()) }
    var range by remember { mutableStateOf(90) }
    var visibleDays by remember(range) { mutableStateOf(31) }
    val settings by tools.rideTools.values.collectAsState()
    var assigning by remember { mutableStateOf(false) }
    var unassign by remember { mutableStateOf(false) }
    var diagnostics by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(item.id, item.lastSeen, range, assignment) {
        try {
            history = tools.sensorLibrary.installations(item.id.toInt())
            days = tools.sensorLibrary.days(item.id.toInt(), (now() / 86400).toLong() - range + 1)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { message = "Could not read sensor history." }
    }
    RideToolsDialog("Sensor ${item.id.toInt().toUInt()}", close) {
        Text(item.label.ifBlank { "Unnamed sensor" }, style = MaterialTheme.typography.titleMedium)
        Text("Manufacturer: ${item.manufacturer.ifBlank { "not recorded" }}\n" +
            "Protocol: ${item.protocol.ifBlank { "unknown" }}")
        Text("Bluetooth address: ${item.address.ifBlank { "not received" }}")
        Text("First seen: ${item.firstSeen?.let(::reportTime) ?: "unknown"}\n" +
            "Last seen: ${item.lastSeen?.let(::reportTime) ?: "never received"}")
        Text("Battery: ${SensorBattery.fromStored(item.voltage, item.percent)?.label() ?: "unknown"}")
        Text("Current assignment: " + (assignment?.let { "${it.first.name} · ${wheelLabel(it.second.location)}" }
            ?: "unassigned spare"))
        if (item.notes.isNotBlank()) Text(item.notes)
        TextButton(onClick = { edit(SensorMetadata(item.id.toInt(), item.label, item.manufacturer,
            item.notes)) }) { Text("Edit sensor details") }
        TextButton(onClick = { assigning = true }) { Text("Assign / move to a wheel…") }
        if (assignment != null) TextButton(onClick = { unassign = true }) { Text("Return to spare library…") }
        TextButton(onClick = { diagnostics = true }) { Text("Questionable packets and diagnostics") }
        Text("Long-term health", style = MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(30, 90, 365).forEach { number ->
            FilterChip(range == number, { range = number }, label = { Text("${number}d") })
        } }
        Text("${days.sumOf { it.packets }} packets on ${days.size} observed days; " +
            "%.1f hours between packets while scanning".format(days.sumOf { it.observedSeconds } / 3600))
        Text("${days.sumOf { it.missed }} missed expected broadcasts; " +
            "longest observed gap %.0f seconds".format(days.maxOfOrNull { it.longestGap } ?: 0.0))
        val expected = settings.insights.broadcastSeconds[item.id.toInt()]?.let { "$it seconds" } ?: "not configured"
        Text("Expected interval: $expected. " +
            "Only set this if you know the sensor's active broadcast interval. Off periods are excluded; " +
            "sleeping sensors may still create gaps while the scanner is running.")
        sensorHealthFindings(days).forEach { Text(it) }
        Text("Daily observations (UTC). Battery values are daily averages in the reported unit. " +
            "Older raw readings are not treated as evidence of continuous scanning.")
        days.asReversed().take(visibleDays).forEach { day ->
            Text("${java.time.LocalDate.ofEpochDay(day.day)} · ${day.packets} packets · " +
                (day.rssi?.let { "%.0f dBm · ".format(it) } ?: "") +
                (day.voltage?.let { "%.2f V".format(it) } ?: day.percent?.let { "%.0f%%".format(it) }
                    ?: "battery unknown") +
                " · ${day.missed} missed · ${day.questionable} questionable")
        }
        if (days.size > visibleDays) TextButton(onClick = { visibleDays += 31 }) {
            Text("Show older daily observations")
        }
        Text("Installation history (latest 200)", style = MaterialTheme.typography.titleLarge)
        if (history.isEmpty()) Text("No recorded assignments.")
        history.forEach { install -> Text("${install.vehicleName} · ${storedWheelLabel(install.location)}\n" +
            "${reportTime(install.started)} → ${install.ended?.let(::reportTime) ?: "current"}\n${install.origin}") }
        Text(message)
    }
    if (assigning) LibraryAssignment(item, { assigning = false })
    if (diagnostics) SensorIssuesDialog(item.id.toInt()) { diagnostics = false }
    if (unassign && assignment != null) AlertDialog(onDismissRequest = { unassign = false },
        title = { Text("Return sensor to spares?") }, text = {
            Text("Unassign from ${assignment.first.name}, ${wheelLabel(assignment.second.location)}? " +
                "The inventory, calibration and history are preserved.")
        }, confirmButton = { TextButton(onClick = {
            unassign = false
            scope.launch {
                try { tools.toolsSensors.unbindIfCurrent(assignment.first.uuid, assignment.second) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { message = failure.message ?: "Could not unassign sensor" }
            }
        }) { Text("Unassign") } }, dismissButton = { TextButton(onClick = { unassign = false }) { Text("Cancel") } })
}

@Composable
private fun SensorMetadataEditor(initial: SensorMetadata, new: Boolean, close: () -> Unit) {
    val tools = remember { appGraph as VehicleToolsDependencies }
    val scope = rememberCoroutineScope()
    var id by remember { mutableStateOf(if (new) "" else initial.id.toUInt().toString()) }
    var label by remember { mutableStateOf(initial.label) }
    var maker by remember { mutableStateOf(initial.manufacturer) }
    var notes by remember { mutableStateOf(initial.notes) }
    var interval by remember { mutableStateOf(if (new) "" else
        tools.rideTools.read().insights.broadcastSeconds[initial.id]?.toString().orEmpty()) }
    var message by remember { mutableStateOf("") }
    RideToolsDialog(if (new) "Add spare sensor" else "Sensor details", close) {
        OutlinedTextField(id, { id = it }, enabled = new, label = { Text("Sensor ID (decimal or 0x hex)") })
        OutlinedTextField(label, { label = it }, label = { Text("Label") })
        OutlinedTextField(maker, { maker = it }, label = { Text("Manufacturer (from your sensor or packaging)") })
        OutlinedTextField(interval, { interval = it },
            label = { Text("Expected broadcast seconds (optional, 1–3600)") })
        Text("This is the same interval used by Sensor reception quality. Clearing it disables missed-broadcast " +
            "estimates for future observations; existing daily summaries remain unchanged.")
        OutlinedTextField(notes, { notes = it }, label = { Text("Notes") })
        Text("Battery and last-seen details come from broadcasts. Saving a spare does not assign it to a vehicle.")
        TextButton(onClick = { scope.launch {
            try {
                val sensorId = (if (id.startsWith("0x", true)) id.drop(2).toUIntOrNull(16) else id.toUIntOrNull())
                require(sensorId != null) { "Enter a valid 32-bit sensor ID." }
                require(interval.isBlank() || interval.toIntOrNull() in 1..3600) {
                    "Use 1–3600 seconds or leave blank."
                }
                if (new) require(tools.sensorLibrary.inventory().none { it.id.toInt() == sensorId.toInt() }) {
                    "That sensor already exists. Edit its details from the library."
                }
                tools.sensorLibrary.metadata(SensorMetadata(sensorId.toInt(), label, maker, notes))
                withContext(Dispatchers.IO) { tools.rideTools.update { data ->
                    val seconds = interval.toIntOrNull()
                    data.copy(insights = data.insights.copy(broadcastSeconds = if (seconds == null)
                        data.insights.broadcastSeconds - sensorId.toInt() else
                        data.insights.broadcastSeconds + (sensorId.toInt() to seconds)))
                } }
                close()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { message = failure.message ?: "Could not save sensor details" }
        } }) { Text("Save") }
        Text(message)
    }
}

private fun storedWheelLabel(value: Long): String {
    val location = when (value) {
        in 0..9 -> Location.Axle.entries.getOrNull(value.toInt())
        in 10..19 -> Location.Side.entries.getOrNull(value.toInt() - 10)
        in 20..29 -> Location.Wheel.entries.getOrNull(value.toInt() - 20)
        in 30..39 -> Location.Auxiliary.entries.getOrNull(value.toInt() - 30)
        else -> null
    }
    return location?.let(::wheelLabel) ?: "Unknown wheel"
}
