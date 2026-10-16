@file:Suppress("LongMethod", "TooGenericExceptionCaught")

package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.data.vehicle.ioc.VehicleToolsDependencies
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.feature.background.R
import com.masselis.tpmsadvanced.feature.background.interfaces.MonitorService
import com.masselis.tpmsadvanced.feature.background.interfaces.MonitoringControlsActivity
import com.masselis.tpmsadvanced.feature.background.interfaces.MonitoringTile
import com.masselis.tpmsadvanced.feature.background.interfaces.monitoringTileLabel
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.MaintenanceModeDialog
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.MonitoringToolDialog
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.RideReportsDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
public fun MonitoringControlsSettings() {
    val context = LocalContext.current
    val deps = remember { appGraph as VehicleToolsDependencies }
    val data by deps.rideTools.values.collectAsState()
    var message by remember { mutableStateOf("") }
    TextButton(onClick = { context.startActivity(Intent(context, MonitoringControlsActivity::class.java)) }) {
        Text("Monitoring controls")
    }
    Row {
        Text("Live tyre status notification", Modifier.weight(1f))
        Switch(data.monitoring.liveNotification, { enabled ->
            deps.rideTools.update { it.copy(monitoring = it.monitoring.copy(liveNotification = enabled)) }
        })
    }
    Text("Optional live readings while monitoring. Android's required monitoring notification remains enabled.")
    TextButton(onClick = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching {
            context.getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(context, MonitoringTile::class.java), "TyrePing",
                Icon.createWithResource(context, R.drawable.car_tire), context.mainExecutor
            ) { result -> message = if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) "TyrePing tile is available"
                else "Open Quick Settings, tap Edit, and drag TyrePing into the panel." }
        }.onFailure { message = "Open Quick Settings, tap Edit, and drag TyrePing into the panel." }
        else message = "Open Quick Settings, tap Edit, and drag TyrePing into the panel."
    }) { Text("Add Quick Settings tile") }
    if (message.isNotEmpty()) Text(message)
}

@Composable
@Suppress("CyclomaticComplexMethod") // API-specific permission and service branches stay at the UI boundary.
public fun MonitoringControls(dismiss: () -> Unit) {
    val context = LocalContext.current
    val deps = remember { appGraph as VehicleToolsDependencies }
    val running by MonitorService.isRunning.collectAsState()
    val active by MonitorService.activeVehicle.collectAsState()
    val status by MonitorService.status.collectAsState()
    val scan by deps.toolsScanner.scanStatus.collectAsState()
    val reading by MonitorService.readingsActive.collectAsState()
    var vehicles by remember { mutableStateOf(emptyList<Vehicle>()) }
    var selected by remember { mutableStateOf<Vehicle?>(null) }
    var maintenance by remember { mutableStateOf(false) }
    var reports by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            deps.toolsVehicles.selectAll().execute() to runCatching {
                deps.toolsVehicles.currentVehicle().execute() }.getOrNull()
        }.let { (all, current) -> vehicles = all; selected = all.firstOrNull { it.uuid == active } ?: current }
    }
    val permissions = remember { buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_CONNECT)
        } else add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    } }
    fun start() {
        selected?.let { vehicle -> runCatching { ContextCompat.startForegroundService(context,
            MonitorService.intent(vehicle)) }
            .onFailure { message = "Could not start monitoring. Check Bluetooth and app permissions." } }
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val radioGranted = permissions.filter { it != Manifest.permission.POST_NOTIFICATIONS }.all {
            results[it] == true || ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (radioGranted) start() else message = "Grant Nearby devices / location permission in Android app settings."
    }
    MonitoringToolDialog("TyrePing monitoring", dismiss) {
        Text(monitoringTileLabel(running, reading, scan), style = MaterialTheme.typography.titleLarge)
        Text(status)
        Text("Choose the vehicle to monitor:")
        vehicles.forEach { vehicle -> TextButton(enabled = !running, onClick = { selected = vehicle }) {
            Text("${if (selected?.uuid == vehicle.uuid) "✓ " else ""}${vehicle.name}")
        } }
        if (vehicles.isEmpty()) Text("Open TyrePing to add a vehicle first.")
        if (running) Button(onClick = { MonitorService.stop(context) }) { Text("Stop monitoring") }
        else Button(enabled = selected != null, onClick = {
            val missing = permissions.filter { ContextCompat.checkSelfPermission(context,
                it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isEmpty()) start() else request.launch(missing.toTypedArray())
        }) { Text("Start monitoring") }
        TextButton(enabled = selected != null, onClick = { maintenance = true }) {
            Text("Inflation / maintenance mode") }
        TextButton(onClick = { reports = true }) { Text("Ride reports") }
        TextButton(onClick = {
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let(context::startActivity) }) {
            Text("Open dashboard")
        }
        Text(message)
    }
    if (maintenance) selected?.let { MaintenanceModeDialog(it) { maintenance = false } }
    if (reports) RideReportsDialog { reports = false }
}
