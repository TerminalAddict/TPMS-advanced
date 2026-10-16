package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.data.vehicle.ioc.VehicleToolsDependencies
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.VehicleRideTools
import com.masselis.tpmsadvanced.feature.background.interfaces.MonitorService
import com.masselis.tpmsadvanced.feature.background.interfaces.AutomaticVehicleSelection
import com.masselis.tpmsadvanced.feature.background.interfaces.spokenMonitoringDiagnostics
import com.masselis.tpmsadvanced.feature.main.usecase.FleetHealth
import com.masselis.tpmsadvanced.feature.main.usecase.RideRuntime
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
public fun RideToolsHost() {
    RideReportPrompt()
    val running by MonitorService.isRunning.collectAsState()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    LaunchedEffect(running, lifecycle) {
        if (!running && lifecycle.isAtLeast(Lifecycle.State.RESUMED)) coroutineScope {
            launch { FleetHealth.captureReadings() }
            launch { FleetHealth.observe() }
            launch { AutomaticVehicleSelection.watch() }
            launch { spokenMonitoringDiagnostics() }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
public fun AutomaticSelectionSettings(vehicle: Vehicle) {
    val deps = remember { appGraph as VehicleToolsDependencies }
    val data by deps.rideTools.values.collectAsState()
    val configured = data.vehicles[vehicle.uuid.toString()] ?: VehicleRideTools()
    val message by AutomaticVehicleSelection.message.collectAsState()
    val manual by RideRuntime.manualSelection.collectAsState()
    var address by remember(vehicle.uuid, configured.accessoryAddress) { mutableStateOf(configured.accessoryAddress) }
    var error by remember { mutableStateOf("") }
    var devices by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    Text("Automatic vehicle selection", style = MaterialTheme.typography.titleMedium)
    Row { Text("Select from connected accessories", Modifier.weight(1f)); Switch(data.automaticSelection, {
        deps.rideTools.update { data -> data.copy(automaticSelection = it) }
        if (it) RideRuntime.manualSelection.value = false
    }) }
    Text("${vehicle.name}: choose a paired helmet, car stereo or other Bluetooth accessory. " +
        "Selection runs while TyrePing is open or its monitoring service is running. " +
        "Selecting manually or by NFC holds that choice until you resume automatic selection.")
    OutlinedTextField(address, { address = it.uppercase() }, label = { Text("Accessory Bluetooth address") },
        singleLine = true, modifier = Modifier.fillMaxWidth())
    FlowRow {
        TextButton(onClick = { devices = runCatching {
            appContext.getSystemService<BluetoothManager>()?.adapter?.bondedDevices?.map {
                (it.name ?: "Unnamed accessory") to it.address
            }.orEmpty()
        }.getOrElse { error = "Grant Nearby devices permission and enable Bluetooth.";
            emptyList() } }) { Text("Choose paired accessory") }
        TextButton(onClick = { runCatching {
            deps.rideTools.updateVehicle(vehicle.uuid) { it.copy(accessoryAddress = address.trim()) }
            error = "Saved"
        }.onFailure { error = "Use a Bluetooth address (AA:BB:CC:DD:EE:FF) assigned to only one vehicle." }
        }) { Text("Save") }
        TextButton(onClick = { deps.rideTools.updateVehicle(vehicle.uuid) { it.copy(accessoryAddress = "") };
            address = "" }) { Text("Clear") }
    }
    devices.forEach { (name, mac) -> TextButton(onClick = { address = mac;
        devices = emptyList() }) { Text("$name · $mac") } }
    Text(error)
    Text(message)
    if (manual) TextButton(onClick = { RideRuntime.manualSelection.value = false }) {
        Text("Resume automatic vehicle selection")
    }
}
