@file:Suppress("LongMethod", "MagicNumber")

package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.data.vehicle.ioc.VehicleToolsDependencies
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.TowingCombination
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.feature.main.usecase.FleetHealth
import com.masselis.tpmsadvanced.feature.main.usecase.RideRuntime
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Saved combinations change presentation only: ownership, calibration and limits stay per vehicle. */
@Composable
public fun DrivingDashboard(vehicle: Vehicle, snackbarHostState: SnackbarHostState, modifier: Modifier = Modifier) {
    com.masselis.tpmsadvanced.core.ui.KeepScreenOn()
    val deps = remember { appGraph as VehicleToolsDependencies }
    val settings by deps.rideTools.values.collectAsState()
    val fleet by FleetHealth.values.collectAsState()
    val pressureUnit by deps.toolsUnits.pressure.collectAsState()
    val temperatureUnit by deps.toolsUnits.temperature.collectAsState()
    var manage by remember { mutableStateOf<Pair<Vehicle, Location>?>(null) }
    var combinations by remember { mutableStateOf(false) }
    var insights by remember { mutableStateOf(false) }
    val active = settings.insights.activeFor(vehicle.uuid,
        fleet.associate { it.vehicle.uuid.toString() to it.vehicle.kind })
    Column(modifier) {
        FlowRow(Modifier.padding(horizontal = 12.dp)) {
            TextButton(onClick = { combinations = true }) { Text(active?.name ?: "Towing combinations") }
            TextButton(onClick = { insights = true }) { Text("Tyre insights") }
            if (active != null) TextButton(onClick = {
                deps.rideTools.update { it.copy(insights = it.insights.copy(activeCombination = null)) }
            }) { Text("Individual view") }
        }
        if (active == null) CurrentVehicle(snackbarHostState, Modifier.weight(1f).fillMaxWidth())
        else CombinedReadings(listOf(active.vehicle, active.trailer).mapNotNull { id ->
            fleet.firstOrNull { it.vehicle.uuid.toString() == id }
        }, pressureUnit, temperatureUnit, now(), Modifier.weight(1f), onWheel = { v, l -> manage = v to l })
    }
    if (combinations) TowingCombinations { combinations = false }
    if (insights) TyreInsights(vehicle) { insights = false }
    manage?.let { (owner, location) -> SensorManagementDialog(owner, location) { manage = null } }
}

@Composable
public fun DrivingSettings(vehicle: Vehicle) {
    var combinations by remember { mutableStateOf(false) }
    var insights by remember { mutableStateOf(false) }
    var reception by remember { mutableStateOf(false) }
    var compatibility by remember { mutableStateOf(false) }
    Text("Driving dashboards", style = MaterialTheme.typography.titleMedium)
    TextButton(onClick = { combinations = true }) { Text("Saved towing combinations") }
    TextButton(onClick = { insights = true }) { Text("Imbalance warnings and cold / hot analysis") }
    TextButton(onClick = { reception = true }) { Text("Sensor reception quality") }
    TextButton(onClick = { compatibility = true }) { Text("Sensor compatibility toolkit") }
    if (combinations) TowingCombinations { combinations = false }
    if (insights) TyreInsights(vehicle) { insights = false }
    if (reception) ReceptionDashboard(vehicle) { reception = false }
    if (compatibility) SensorDiscoveryDialog(vehicle) { compatibility = false }
}

@Composable
private fun TowingCombinations(close: () -> Unit) {
    val deps = remember { appGraph as VehicleToolsDependencies }
    val data by deps.rideTools.values.collectAsState()
    val vehicles by remember { deps.toolsVehicles.selectAll().asFlow() }.collectAsState(emptyList())
    val kinds = vehicles.associate { it.uuid.toString() to it.kind }
    var name by remember { mutableStateOf("") }
    var tow by remember { mutableStateOf<String?>(null) }
    var trailer by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    RideToolsDialog("Towing combinations", close) {
        Text("Link a car, ute or van to a trailer. Each retains its own sensors and alert limits.")
        data.insights.combinations.forEach { combo ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text(combo.name, style = MaterialTheme.typography.titleMedium)
                Text(listOf(combo.vehicle, combo.trailer).joinToString(" + ") { id ->
                    vehicles.firstOrNull { it.uuid.toString() == id }?.name ?: "Deleted vehicle"
                })
                FlowRow {
                    TextButton(enabled = combo.isValidFor(kinds), onClick = { scope.launch {
                        RideRuntime.manualSelection.value = true
                        RideRuntime.selectionMutex.withLock {
                            deps.toolsVehicles.setIsCurrent(UUID.fromString(combo.vehicle), true)
                            deps.rideTools.update { it.copy(insights = it.insights.copy(activeCombination = combo.id)) }
                        }
                        close()
                    } }) { Text("Select") }
                    TextButton(onClick = { editing = combo.id; name = combo.name; tow = combo.vehicle;
                        trailer = combo.trailer }) { Text("Edit") }
                    TextButton(onClick = { deps.rideTools.update { it.copy(insights = it.insights.copy(
                        combinations = it.insights.combinations.filterNot { c -> c.id == combo.id },
                        activeCombination = it.insights.activeCombination?.takeUnless { id -> id == combo.id })) }
                    }) { Text("Delete") }
                }
            } }
        }
        Text(if (editing == null) "New combination" else "Edit combination",
            style = MaterialTheme.typography.titleMedium)
        ToolsField("Combination name", name) { name = it }
        Text("Tow vehicle")
        FlowRow { vehicles.filter { it.kind in TowingCombination.TOWING_KINDS }.forEach { vehicle ->
            FilterChip(tow == vehicle.uuid.toString(), { tow = vehicle.uuid.toString() }, { Text(vehicle.name) })
        } }
        Text("Trailer")
        FlowRow { vehicles.filter { it.kind in TowingCombination.TRAILER_KINDS }.forEach { vehicle ->
            FilterChip(trailer == vehicle.uuid.toString(), { trailer = vehicle.uuid.toString() },
                { Text(vehicle.name) })
        } }
        Text(message)
        Button(onClick = { runCatching {
            val combo = TowingCombination(editing ?: UUID.randomUUID().toString(), name.trim(),
                requireNotNull(tow), requireNotNull(trailer))
            require(combo.isValidFor(kinds)); combo.validate()
            deps.rideTools.update { it.copy(insights = it.insights.copy(
                combinations = it.insights.combinations.filterNot { c -> c.id == combo.id } + combo)) }
            name = ""; editing = null; message = "Saved. Select the combination above to display both vehicles."
        }.onFailure { message = "Choose both vehicles and a unique name (up to 60 characters)." } }) { Text("Save") }
        if (editing != null) TextButton(onClick = { editing = null; name = "" }) { Text("Cancel edit") }
    }
}
