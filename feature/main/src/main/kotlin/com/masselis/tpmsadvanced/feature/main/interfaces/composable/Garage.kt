@file:Suppress("LongMethod", "MagicNumber")

package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import com.masselis.tpmsadvanced.data.vehicle.ioc.VehicleToolsDependencies
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.feature.main.usecase.FleetHealth
import com.masselis.tpmsadvanced.feature.main.usecase.RideRuntime
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.withLock

@Composable
@Suppress("CyclomaticComplexMethod") // Lifecycle, warning filter and selection outcomes are independent UI states.
public fun Garage(modifier: Modifier = Modifier, onSelected: () -> Unit = {}) {
    val tools = remember { appGraph as VehicleToolsDependencies }
    val fleet by FleetHealth.values.collectAsState()
    val status by RideRuntime.status.collectAsState()
    val notice by FleetHealth.scanNotice.collectAsState()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val scope = rememberCoroutineScope()
    val pressureUnit by tools.toolsUnits.pressure.collectAsState()
    val temperatureUnit by tools.toolsUnits.temperature.collectAsState()
    var currentTime by remember { mutableStateOf(now()) }
    var warningsOnly by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Vehicle?>(null) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(lifecycle) {
        if (lifecycle.isAtLeast(Lifecycle.State.RESUMED)) coroutineScope {
            launch { FleetHealth.observe() }
            while (isActive) { currentTime = now(); delay(5000) }
        }
    }
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${fleet.size} vehicles · ${fleet.count { it.warning }} with warnings",
            style = MaterialTheme.typography.titleLarge)
        Text(notice ?: status)
        Text("Last known readings across your garage. Sleeping or stale sensors are unconfirmed. " +
            "Opening this page does not start an additional Bluetooth scan.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!warningsOnly, { warningsOnly = false }, label = { Text("All vehicles") })
            FilterChip(warningsOnly, { warningsOnly = true }, label = { Text("Warnings") })
        }
        SensorInventoryButton()
        fleet.filter { !warningsOnly || it.warning }.sortedByDescending { it.warning }.forEach { health ->
            GarageVehicleCard(health, currentTime, pressureUnit, temperatureUnit,
                tools.maintenanceMode.active(health.vehicle.uuid), onDetails = { detail = health.vehicle }, onSelect = {
                scope.launch {
                    try {
                        RideRuntime.manualSelection.value = true
                        RideRuntime.selectionMutex.withLock {
                            tools.toolsVehicles.setIsCurrent(health.vehicle.uuid, true)
                        }
                        onSelected()
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: IllegalStateException) { message = "Could not select vehicle. Refresh and retry." }
                }
            })
        }
        if (warningsOnly && fleet.none { it.warning }) Text("No current warnings. Check freshness before riding.")
        Text(message)
    }
    detail?.let { vehicle -> RideToolsDialog(vehicle.name, { detail = null }) {
        fleet.firstOrNull { it.vehicle.uuid == vehicle.uuid }?.let { health ->
            health.wheels.forEach { WheelReadinessCard(it, currentTime) }
            health.imbalances.forEach { Text("${it.axle}: pressure imbalance ${it.differenceKpa} kPa") }
            health.maintenance?.let { Text("Maintenance reminder: $it") }
        }
        ManufacturerSpecificationsButton(vehicle)
    } }
}

@Composable
internal fun GarageVehicleCard(health: com.masselis.tpmsadvanced.feature.main.usecase.VehicleHealth,
    time: Double, pressureUnit: com.masselis.tpmsadvanced.data.unit.model.PressureUnit,
    temperatureUnit: com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit,
    maintenance: Boolean, onDetails: () -> Unit, onSelect: () -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(health.vehicle.name, style = MaterialTheme.typography.titleLarge)
        Text(when { health.warning -> "Warnings to review"; health.ready -> "All sensors recently healthy"
            else -> "Waiting / sleeping sensors · health unconfirmed" })
        if (maintenance) Text("Maintenance active · pressure alerts paused")
        health.wheels.forEach { wheel ->
            Text("${wheelLabel(wheel.location)} · ${wheel.state.name.lowercase()}")
            wheel.reading?.let { reading ->
                Text("${reading.pressure.string(pressureUnit)} · ${reading.temperature.string(temperatureUnit)} · " +
                    "${((time - reading.timestamp).coerceAtLeast(0.0) / 60).toLong()}m ago")
                reading.sensorBattery?.let { Text("Battery ${it.label()}") }
            } ?: Text("No reading")
            wheel.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        health.imbalances.forEach { Text("${it.axle} pressure imbalance") }
        health.maintenance?.let { Text("Maintenance due: $it") }
        FlowRow { TextButton(onClick = onDetails) { Text("Details") }
            TextButton(onClick = onSelect) { Text("Open vehicle") } }
    } }
}
