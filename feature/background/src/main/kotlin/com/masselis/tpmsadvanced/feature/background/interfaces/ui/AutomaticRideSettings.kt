package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat.startForegroundService
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.isGranted
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.feature.background.interfaces.MonitorService
import com.masselis.tpmsadvanced.feature.background.interfaces.RidePreferences
import com.masselis.tpmsadvanced.feature.background.interfaces.RideTrigger
import com.masselis.tpmsadvanced.feature.background.interfaces.WaitingScanPower
import java.util.Locale

@OptIn(ExperimentalPermissionsApi::class)
@SuppressLint("MissingPermission")
@Suppress("LongMethod", "MagicNumber")
@Composable
public fun AutomaticRideSettings(vehicle: Vehicle) {
    val context = LocalContext.current
    val preferences = remember { RidePreferences(context) }
    val saved = remember(vehicle.uuid) { preferences.read(vehicle.uuid) }
    var address by remember(vehicle.uuid) { mutableStateOf(saved.address) }
    var beacon by remember(vehicle.uuid) { mutableStateOf(saved.beacon) }
    var rssi by remember(vehicle.uuid) { mutableFloatStateOf(saved.minRssi.toFloat()) }
    var devices by remember { mutableStateOf(emptyList<BluetoothDevice>()) }
    var error by remember { mutableStateOf<String?>(null) }
    val running by MonitorService.isRunning.collectAsState()
    val active by MonitorService.activeVehicle.collectAsState()
    val status by MonitorService.status.collectAsState()
    val saving by WaitingScanPower.saving.collectAsState()
    val permissions = rememberMultiplePermissionsState(ridePermissions())
    val backgroundLocation = if (Build.VERSION.SDK_INT in Build.VERSION_CODES.Q..Build.VERSION_CODES.R)
        rememberPermissionState(Manifest.permission.ACCESS_BACKGROUND_LOCATION) else null
    Column {
        Text("Automatic ride monitoring", style = MaterialTheme.typography.titleMedium)
        Row {
            Text("Save battery while waiting")
            Switch(saving, { WaitingScanPower.saving.value = it })
        }
        Text("Beacon scanning uses less power after 5 minutes without significant motion while the screen is off " +
            "and the phone is not charging. Motion restores full rate. " +
            "Active tyre monitoring always stays at full rate. " +
            "Without a motion sensor, waiting uses balanced scanning. Scanning is never suspended by this setting.")
        Text("Watch for this vehicle while TyrePing is in the background. A notification stays visible until you stop.")
        if (!permissions.allPermissionsGranted) {
            Button(onClick = { permissions.launchMultiplePermissionRequest() }) {
                Text("Allow required permissions")
            }
        } else if (backgroundLocation != null && !backgroundLocation.status.isGranted) {
            BackgroundLocationSetup(backgroundLocation)
        } else {
            Row {
                Text("Use a BLE beacon")
                Switch(checked = beacon, onCheckedChange = { beacon = it; devices = emptyList() })
            }
            Text(if (beacon) "Use a beacon with a stable Bluetooth address. Weak signals are ignored."
                else "Choose a paired accessory. Connect it after starting the watch; " +
                    "connected headsets are also detected.")
            OutlinedTextField(value = address, onValueChange = { address = it.uppercase(Locale.ROOT).trim() },
                label = { Text("Bluetooth address") }, placeholder = { Text("AA:BB:CC:DD:EE:FF") }, singleLine = true)
            if (!beacon) {
                TextButton(onClick = {
                    devices = context.getSystemService<BluetoothManager>()?.adapter?.bondedDevices?.toList().orEmpty()
                    if (devices.isEmpty()) error = "Pair the accessory in Android Bluetooth settings first."
                }) { Text("Choose paired accessory") }
                devices.forEach { device ->
                    TextButton(onClick = { address = device.address; devices = emptyList() }) {
                        Text("${device.name ?: "Accessory"} (${device.address})")
                    }
                }
            } else {
                Text("Minimum signal: ${rssi.toInt()} dBm")
                Slider(value = rssi, onValueChange = { rssi = it }, valueRange = -100f..-30f)
            }
            val trigger = RideTrigger(address, beacon, rssi.toInt())
            Button(enabled = trigger.isValid(), onClick = {
                preferences.save(vehicle.uuid, trigger)
                error = startWatch(context, vehicle)
            }) { Text(if (running && active == vehicle.uuid) "Update watch" else "Start watching this vehicle") }
        }
        if (running) {
            Text(status)
            TextButton(onClick = {
                MonitorService.stop(context)
            }) { Text("Stop monitoring") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun BackgroundLocationSetup(permission: PermissionState) {
    val context = LocalContext.current
    Text("Android 10 and 11 require Location access set to ‘Allow all the time’ for background Bluetooth " +
        "scanning. TyrePing does not record GPS location. " +
        "In Android settings, select Permissions, then Location.")
    Button(onClick = {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) permission.launchPermissionRequest()
        else context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:${context.packageName}".toUri()))
    }) { Text("Allow background Bluetooth scanning") }
}

private fun startWatch(context: Context, vehicle: Vehicle): String? = try {
    startForegroundService(context, MonitorService.intent(vehicle, automatic = true))
    null
} catch (_: IllegalStateException) {
    "Android could not start monitoring. Keep TyrePing open and try again."
} catch (_: SecurityException) {
    "Bluetooth permission is missing. Grant it and try again."
}

internal fun ridePermissions(): List<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    } else add(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}
