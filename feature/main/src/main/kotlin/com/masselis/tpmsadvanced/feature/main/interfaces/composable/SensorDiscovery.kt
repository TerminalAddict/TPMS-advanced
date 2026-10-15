package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.data.vehicle.ioc.VehicleToolsDependencies
import com.masselis.tpmsadvanced.data.vehicle.model.BleAdvertisement
import com.masselis.tpmsadvanced.data.vehicle.model.BleDiscoverySession
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.minutes

@Composable
public fun SensorDiscoveryButton(vehicle: Vehicle) {
    var show by remember { mutableStateOf(false) }
    TextButton(onClick = { show = true }) { Text("Discover sensors") }
    if (show) SensorDiscoveryDialog(vehicle) { show = false }
}

@Suppress("LongMethod", "CyclomaticComplexMethod", "TooGenericExceptionCaught")
@Composable
internal fun SensorDiscoveryDialog(vehicle: Vehicle, dismiss: () -> Unit) {
    val tools = remember { appGraph as VehicleToolsDependencies }
    val scanner = tools.toolsScanner
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf(BleDiscoverySession()) }
    var devices by remember { mutableStateOf(session.devices()) }
    var requested by remember { mutableStateOf(false) }
    var time by remember { mutableStateOf(now()) }
    var message by remember { mutableStateOf("") }
    var wheel by remember(vehicle.uuid) { mutableStateOf(vehicle.kind.locations.first()) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var assigning by remember { mutableStateOf<BleAdvertisement?>(null) }
    var exportPackets by remember { mutableStateOf(emptyList<BleAdvertisement>()) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val scanStatus by scanner.scanStatus.collectAsState()
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        message = if (it.values.all { granted -> granted }) "Permissions granted. Start scanning."
            else "Bluetooth permission is needed. Allow Nearby devices in Android app settings."
    }
    val export = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-ndjson")
    ) { uri ->
        val packets = exportPackets
        exportPackets = emptyList()
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    checkNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use { writer ->
                        packets.forEach { writer.appendLine(it.jsonLine()) }
                    }
                }
                message = "Exported ${packets.size} packets. " +
                    "Attach the JSONL file for protocol investigation."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message = "Could not save the capture. Choose another destination." }
        }
    }
    fun exportCapture(address: String?) {
        // Freeze the capture before the document picker pauses this screen.
        exportPackets = session.capture(address)
        requested = false
        if (exportPackets.isEmpty()) message = "No retained packets for this device. Scan again before exporting."
        else export.launch("TyrePing-discovery-${address?.replace(":", "") ?: "all"}.jsonl")
    }
    LaunchedEffect(lifecycle, session) {
        if (!lifecycle.isAtLeast(Lifecycle.State.RESUMED)) { requested = false; return@LaunchedEffect }
        while (isActive) {
            devices = session.devices()
            time = now()
            delay(500)
        }
    }
    LaunchedEffect(requested, lifecycle, session) {
        if (requested && lifecycle.isAtLeast(Lifecycle.State.RESUMED)) {
            try {
                val finished = withTimeoutOrNull(2.minutes) {
                    scanner.discoverAdvertisements().collect { session.observe(it) }
                    true
                }
                message = if (finished == null) "Two-minute scan finished. Start again if needed; packets are retained."
                    else "Scanner stopped. Start again to continue."
                requested = false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                requested = false
                message = "Scan stopped. Check Bluetooth, Nearby devices permissions, and Location on Android 9–11."
            } finally { devices = session.devices() }
        }
    }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.padding(12.dp)) {
                DiscoveryContent(vehicle.name, vehicle.kind.locations, wheel, { wheel = it }, devices,
                    requested, scanner.isBluetoothRequired, discoveryStatus(scanStatus), time, session.received,
                    session.marked, expanded, message,
                    onStart = {
                        val missing = scanner.missingPermission()
                        if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
                        else { message = ""; requested = true }
                    },
                    onStop = { requested = false },
                    onMark = { session.markInstallation(); devices = session.devices() },
                    onClear = { session = BleDiscoverySession(); devices = emptyList(); expanded = null; message = "" },
                    onExpand = { expanded = if (expanded == it) null else it },
                    onAssign = { assigning = it }, onExport = ::exportCapture, onClose = dismiss)
            }
        }
    }
    assigning?.let { packet ->
        DiscoveryAssignmentDialog(vehicle, wheel, packet, tools, onDismiss = { assigning = null }, onSaved = {
            message = "Sensor assigned to ${wheelLabel(wheel)} on ${vehicle.name}."
            assigning = null
        })
    }
}
