@file:Suppress("LongMethod", "MagicNumber")

package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.data.vehicle.ioc.VehicleToolsDependencies
import com.masselis.tpmsadvanced.data.vehicle.model.MaintenanceRecord
import com.masselis.tpmsadvanced.data.vehicle.model.MaintenanceReminder
import com.masselis.tpmsadvanced.data.vehicle.model.PressureProfile
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.VehicleRideTools
import com.masselis.tpmsadvanced.data.vehicle.model.WheelReadiness
import com.masselis.tpmsadvanced.data.vehicle.model.WheelHealth
import com.masselis.tpmsadvanced.data.vehicle.model.backupLocation
import com.masselis.tpmsadvanced.feature.main.usecase.FleetHealth
import com.masselis.tpmsadvanced.feature.main.usecase.RideRuntime
import com.masselis.tpmsadvanced.feature.main.usecase.VehicleHealth
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDate

@Composable
public fun ReadyToRide(vehicle: Vehicle, modifier: Modifier = Modifier) {
    val tools by (appGraph as VehicleToolsDependencies).rideTools.values.collectAsState()
    ReadinessCard(vehicle, tools.insights, modifier)
}

@Composable
internal fun ReadinessCard(vehicle: Vehicle, insights: com.masselis.tpmsadvanced.data.vehicle.model.DrivingInsights,
    modifier: Modifier = Modifier) {
    val fleet by FleetHealth.values.collectAsState()
    val combination = insights.activeFor(vehicle.uuid,
        fleet.associate { it.vehicle.uuid.toString() to it.vehicle.kind })
    val members = listOfNotNull(vehicle.uuid.toString(), combination?.trailer).mapNotNull { id ->
        fleet.firstOrNull { it.vehicle.uuid.toString() == id }
    }
    val health = com.masselis.tpmsadvanced.feature.main.usecase.groupedVehicleHealth(members)
    val status by RideRuntime.status.collectAsState()
    val scanNotice by FleetHealth.scanNotice.collectAsState()
    val parked by RideRuntime.parked.collectAsState()
    val started by RideRuntime.started.collectAsState()
    var open by remember { mutableStateOf(false) }
    var currentTime by remember { mutableDoubleStateOf(now()) }
    LaunchedEffect(open) {
        if (open) while (isActive) { currentTime = now(); delay(1000) }
    }
    Card(onClick = { open = true }, modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(Modifier.padding(10.dp)) {
            Text(readinessTitle(health, scanNotice != null, started != null),
                style = MaterialTheme.typography.titleSmall)
            Text((scanNotice ?: status) + if (parked) " · Parked" else "",
                style = MaterialTheme.typography.bodySmall)
        }
    }
    if (open) RideToolsDialog("Ready-to-Ride · ${combination?.name ?: vehicle.name}", { open = false }) {
        Text(status)
        scanNotice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("Healthy means a reading within 2 minutes and within your limits. Sleeping sensors are unconfirmed; " +
            "wake them and wait for readings before relying on this check.")
        members.forEach { member ->
            Text(member.vehicle.name, style = MaterialTheme.typography.titleLarge)
            member.wheels.forEach { wheel -> WheelReadinessCard(wheel, currentTime) }
            member.imbalances.forEach { Text("${it.axle} pressure imbalance: ${it.differenceKpa} kPa") }
            member.maintenance?.let { Text("Maintenance due: $it") }
        }
        Row {
            Text("Parked · pause missing-sensor warnings", Modifier.weight(1f))
            Switch(parked, {
                if (!it && RideRuntime.started.value != null) RideRuntime.started.value = now()
                RideRuntime.parked.value = it
            })
        }
        Text("Use Parked when stationary. Other pressure and temperature alerts remain enabled. " +
            "After resuming, sensors must report again before missing warnings can start.")
    }
}

private fun readinessTitle(health: VehicleHealth?, scanWarning: Boolean, monitoring: Boolean): String = when {
    health == null -> "Ready-to-Ride · checking readings"
    health.warning || scanWarning -> "Ready-to-Ride · warnings to review"
    health.ready && !monitoring -> "Sensors healthy · monitoring not active"
    health.ready -> "Ready-to-Ride · all sensors recently healthy"
    else -> "Ready-to-Ride · waiting for sensors"
}

@Composable
internal fun WheelReadinessCard(wheel: WheelHealth, currentTime: Double) {
    val readability = com.masselis.tpmsadvanced.core.ui.LocalReadability.current
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text(wheel.label, style = MaterialTheme.typography.titleMedium)
                Text(when (wheel.state) {
                    WheelReadiness.UNSEEN -> "Not yet received · assign or wake this sensor"
                    WheelReadiness.SLEEPING -> "Sleeping or stale · health not confirmed"
                    WheelReadiness.HEALTHY -> "Recently healthy"
                    WheelReadiness.WARNING -> "Warning"
                    WheelReadiness.MISSING -> "Missing during this ride"
                })
                wheel.reading?.let {
                    Text("${it.pressure.kpa} kPa",
                        fontSize = MaterialTheme.typography.headlineSmall.fontSize * readability.scale)
                    Text("${it.temperature.celsius} °C",
                        fontSize = MaterialTheme.typography.titleMedium.fontSize * readability.scale)
                    Text("Last seen ${((currentTime - it.timestamp).coerceAtLeast(0.0)).toInt()}s ago")
                    Text("Battery: ${it.sensorBattery?.label() ?: "not reported"}")
                }
                wheel.warnings.forEach { Text(it) }
    } }
}

@Composable
public fun RideToolsSettings(vehicle: Vehicle) {
    val deps = remember { appGraph as VehicleToolsDependencies }
    val data by deps.rideTools.values.collectAsState()
    var profiles by remember { mutableStateOf(false) }
    var maintenance by remember { mutableStateOf(false) }
    Text("Ride tools", style = MaterialTheme.typography.titleMedium)
    TextButton(onClick = { profiles = true }) { Text("Tyre pressure profiles") }
    TextButton(onClick = { maintenance = true }) { Text("Tyre maintenance and reminders") }
    Row { Text("Missing-sensor warnings", Modifier.weight(1f)); Switch(data.missingEnabled,
        { deps.rideTools.update { data -> data.copy(missingEnabled = it) } }) }
    Text("Warn only after a sensor reports during active monitoring, then disappears. " +
        "Automatic waiting and Parked pause these warnings.")
    Text("Missing after ${data.missingMinutes} minutes")
    FlowRow { listOf(1, 2, 5, 10, 15, 30, 60).forEach { minutes ->
        FilterChip(selected = data.missingMinutes == minutes, onClick = {
            deps.rideTools.update { it.copy(missingMinutes = minutes) }
        }, label = { Text("${minutes}m") }, modifier = Modifier.padding(end = 6.dp))
    } }
    Row { Text("Speak monitoring diagnostics", Modifier.weight(1f)); Switch(data.spokenDiagnostics,
        { deps.rideTools.update { data -> data.copy(spokenDiagnostics = it) } }) }
    Text("Announce monitoring activation, waiting and Bluetooth interruptions, with the reason. " +
        "Uses alarm volume; off by default.")
    if (profiles) PressureProfiles(vehicle, { profiles = false })
    if (maintenance) MaintenanceRecords(vehicle, { maintenance = false })
}

@Composable
public fun RideToolsDialog(title: String, close: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().imePadding().padding(16.dp)) {
                Row { Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = close) { Text("Close") } }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
            }
        }
    }
}

@Composable
private fun PressureProfiles(vehicle: Vehicle, close: () -> Unit) {
    val deps = remember { appGraph as VehicleToolsDependencies }
    val data by deps.rideTools.values.collectAsState()
    val current by remember(vehicle.uuid) { deps.toolsVehicles.selectByUuid(vehicle.uuid).asFlow() }
        .collectAsState(vehicle)
    val tools = data.vehicles[vehicle.uuid.toString()] ?: VehicleRideTools()
    var editor by remember { mutableStateOf<PressureProfile?>(null) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    RideToolsDialog("Pressure profiles · ${vehicle.name}", close) {
        Text("Save pressure alert limits for Solo, Pillion, Fully Loaded, Touring or Towing. " +
            "Use limits appropriate to your tyres and load. All values below are kPa.")
        tools.profiles.forEach { profile ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text(profile.name + if (profile.matches(current)) " · active limits" else "",
                    style = MaterialTheme.typography.titleMedium)
                Text("${profile.low}–${profile.high} kPa" + if (profile.separateRear)
                    " · rear ${profile.rearLow}–${profile.rearHigh} kPa" else " · all wheels")
                FlowRow {
                    TextButton(onClick = { scope.launch {
                        runCatching { deps.toolsVehicles.applyProfile(vehicle.uuid, profile) }
                            .onSuccess { message = "Applied ${profile.name}; alerts use the new limits immediately." }
                            .onFailure { message = it.message ?: "Could not apply profile" }
                    } }) { Text("Apply") }
                    TextButton(onClick = { editor = profile }) { Text("Edit") }
                    TextButton(onClick = { deps.rideTools.updateVehicle(vehicle.uuid) {
                        it.copy(profiles = it.profiles.filterNot { saved -> saved.id == profile.id })
                    } }) { Text("Delete") }
                }
            } }
        }
        TextButton(onClick = { editor = PressureProfile(name = "", low = current.lowPressure.kpa,
            high = current.highPressure.kpa, separateRear = current.separateFrontRearPressure,
            rearLow = current.rearLowPressure.kpa, rearHigh = current.rearHighPressure.kpa) }) {
            Text("New profile from current limits")
        }
        Text(message)
    }
    editor?.let { initial -> ProfileEditor(initial, vehicle, close = { editor = null }) { profile ->
        deps.rideTools.updateVehicle(vehicle.uuid) {
            it.copy(profiles = it.profiles.filterNot { p -> p.id == profile.id } + profile)
        }
        editor = null
    } }
}

@Composable
internal fun ProfileEditor(initial: PressureProfile, vehicle: Vehicle, close: () -> Unit,
    save: (PressureProfile) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var low by remember { mutableStateOf(initial.low.toString()) }
    var high by remember { mutableStateOf(initial.high.toString()) }
    var rear by remember { mutableStateOf(initial.separateRear) }
    var rearLow by remember { mutableStateOf(initial.rearLow.toString()) }
    var rearHigh by remember { mutableStateOf(initial.rearHigh.toString()) }
    var error by remember { mutableStateOf("") }
    RideToolsDialog("Edit pressure profile", close) {
        ToolsField("Name (e.g. Solo or Towing)", name) { name = it }
        ToolsField("Low pressure · kPa", low) { low = it }
        ToolsField("High pressure · kPa", high) { high = it }
        if (vehicle.kind != Vehicle.Kind.SINGLE_AXLE_TRAILER) Row {
            Text("Separate rear limits", Modifier.weight(1f)); Switch(rear, { rear = it })
        }
        if (rear) {
            ToolsField("Rear low · kPa", rearLow) { rearLow = it }
            ToolsField("Rear high · kPa", rearHigh) { rearHigh = it }
        }
        Text(error)
        Button(onClick = { runCatching {
            val value = initial.copy(name = name.trim(), low = low.toFloat(), high = high.toFloat(),
                separateRear = rear, rearLow = rearLow.toFloat(), rearHigh = rearHigh.toFloat())
            value.validate(); save(value)
        }.onFailure { error = "Use a unique name and valid pressure limits (low < high, 0–2000 kPa)." } }) {
            Text("Save profile")
        }
    }
}

@Composable
internal fun ToolsField(label: String, value: String, change: (String) -> Unit) {
    OutlinedTextField(value, change, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun MaintenanceRecords(vehicle: Vehicle, close: () -> Unit) {
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    val deps = remember { appGraph as VehicleToolsDependencies }
    val data by deps.rideTools.values.collectAsState()
    val tools = data.vehicles[vehicle.uuid.toString()] ?: VehicleRideTools()
    var add by remember { mutableStateOf(false) }
    var reminder by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<MaintenanceRecord?>(null) }
    RideToolsDialog("Tyre maintenance · ${vehicle.name}", close) {
        Text("Records use kilometres, millimetres and kPa. Installation and replacement entries preserve tyre history.")
        Button(onClick = { add = true }) { Text("Add maintenance record") }
        TextButton(onClick = { reminder = true }) { Text("Configure maintenance reminder") }
        if (tools.reminder.enabled) Text("${tools.reminder.label}: " +
            listOfNotNull(tools.reminder.dueDate, tools.reminder.dueKm?.let { "$it km" }).joinToString(" or ") +
            if (tools.reminder.isDue(LocalDate.now())) " · DUE" else "")
        Text("Reminders appear on the dashboard and widget when due, with a notification checked daily. " +
            "Android may delay background notifications. Update the odometer here; " +
            "mileage is not tracked automatically.")
        if (tools.records.isEmpty()) Text("No maintenance records yet.")
        tools.records.sortedByDescending { it.date }.forEach { record ->
            MaintenanceCard(record) { deleting = record }
        }
    }
    if (add) MaintenanceEditor(vehicle, { add = false }) { record ->
        deps.rideTools.updateVehicle(vehicle.uuid) { it.copy(records = it.records + record) }; add = false
    }
    if (reminder) ReminderEditor(tools.reminder, { reminder = false }) { value ->
        deps.rideTools.updateVehicle(vehicle.uuid) { it.copy(reminder = value) }; reminder = false
        if (value.enabled && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
            permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    deleting?.let { record -> AlertDialog(onDismissRequest = { deleting = null },
        title = { Text("Delete this maintenance record?") }, text = { Text("${record.date}: ${record.kind}") },
        confirmButton = { TextButton(onClick = { deps.rideTools.updateVehicle(vehicle.uuid) {
            it.copy(records = it.records.filterNot { r -> r.id == record.id }) }; deleting = null
        }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }) }
}

@Composable
internal fun MaintenanceCard(record: MaintenanceRecord, delete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text("${record.date} · ${record.kind}", style = MaterialTheme.typography.titleMedium)
                Text(backupLocation(record.wheel).toString().lowercase().replace('_', ' '))
                if (record.brandModel.isNotEmpty()) Text(record.brandModel)
                record.odometerKm?.let { Text("Odometer: $it km") }
                record.treadMm?.let { Text("Tread: $it mm") }
                record.pressureKpa?.let { Text("Pressure: $it kPa") }
                if (record.notes.isNotEmpty()) Text(record.notes)
                TextButton(onClick = delete) { Text("Delete record") }
    } }
}

@Composable
internal fun MaintenanceEditor(vehicle: Vehicle, close: () -> Unit, save: (MaintenanceRecord) -> Unit) {
    var wheel by remember { mutableStateOf(vehicle.kind.locations.first()) }
    var kind by remember { mutableStateOf(MaintenanceRecord.KINDS.first()) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var model by remember { mutableStateOf("") }
    var km by remember { mutableStateOf("") }
    var tread by remember { mutableStateOf("") }
    var pressure by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    RideToolsDialog("New maintenance record", close) {
        FlowRow { vehicle.kind.locations.forEach { location -> FilterChip(wheel == location, { wheel = location },
            label = { Text(location.toString().lowercase().replace('_', ' ')) }) } }
        FlowRow { MaintenanceRecord.KINDS.forEach { entry -> FilterChip(kind == entry, { kind = entry },
            label = { Text(entry) }) } }
        ToolsField("Date · YYYY-MM-DD", date) { date = it }
        ToolsField("Tyre brand / model", model) { model = it }
        ToolsField("Odometer · km (optional)", km) { km = it }
        ToolsField("Tread depth · mm (optional)", tread) { tread = it }
        ToolsField("Pressure check · kPa (optional)", pressure) { pressure = it }
        ToolsField("Notes", notes) { notes = it }
        Text(error)
        Button(onClick = { runCatching {
            val record = MaintenanceRecord(wheel = backupLocation(wheel), date = date, kind = kind,
                brandModel = model.trim(), odometerKm = km.optionalFloat(), treadMm = tread.optionalFloat(),
                pressureKpa = pressure.optionalFloat(), notes = notes)
            record.validate(); save(record)
        }.onFailure { error = "Check the date and numeric values. Values must be positive and finite." }
        }) { Text("Save record") }
    }
}

@Composable
internal fun ReminderEditor(initial: MaintenanceReminder, close: () -> Unit, save: (MaintenanceReminder) -> Unit) {
    var enabled by remember { mutableStateOf(initial.enabled) }
    var label by remember { mutableStateOf(initial.label) }
    var date by remember { mutableStateOf(initial.dueDate ?: "") }
    var due by remember { mutableStateOf(initial.dueKm?.toString() ?: "") }
    var km by remember { mutableStateOf(initial.currentKm.toString()) }
    var error by remember { mutableStateOf("") }
    RideToolsDialog("Maintenance reminder", close) {
        Row { Text("Enabled", Modifier.weight(1f)); Switch(enabled, { enabled = it }) }
        ToolsField("Reminder", label) { label = it }
        ToolsField("Due date · YYYY-MM-DD (optional)", date) { date = it }
        ToolsField("Due odometer · km (optional)", due) { due = it }
        ToolsField("Current odometer · km", km) { km = it }
        Text("Either due date or mileage can trigger the reminder. Set the next date or mileage after completing it.")
        Text(error)
        Button(onClick = { runCatching {
            val value = MaintenanceReminder(label.trim(), date.takeIf { it.isNotBlank() }, due.optionalFloat(),
                km.toFloat(), enabled)
            value.validate()
            save(value)
        }.onFailure { error = "Enter a label, valid date / mileage, and at least one due value when enabled." }
        }) { Text("Save reminder") }
    }
}

private fun String.optionalFloat(): Float? = if (isBlank()) null else toFloat()
