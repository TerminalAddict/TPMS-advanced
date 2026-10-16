@file:Suppress("MagicNumber", "LongMethod")

package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.data.vehicle.ioc.VehicleToolsDependencies
import com.masselis.tpmsadvanced.data.vehicle.model.ParkedAlertRoute
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import kotlinx.coroutines.delay

@Composable
public fun MonitoringToolDialog(title: String, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.padding(16.dp).widthIn(max = 600.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                content()
                TextButton(onClick = dismiss) { Text("Done") }
            }
        }
    }
}

@Composable
public fun MonitoringToolsSettings(vehicle: Vehicle) {
    var page by remember { mutableStateOf("") }
    Text("Monitoring tools", style = MaterialTheme.typography.titleMedium)
    TextButton(onClick = { page = "maintenance" }) { Text("Inflation / maintenance mode") }
    TextButton(onClick = { page = "routing" }) { Text("Alert routing for ${vehicle.name}") }
    TextButton(onClick = { page = "rides" }) { Text("Ride summary reports") }
    TextButton(onClick = { page = "events" }) { Text("Alert event history") }
    TextButton(onClick = { page = "storage" }) { Text("Storage and retention") }
    when (page) {
        "maintenance" -> MaintenanceModeDialog(vehicle) { page = "" }
        "routing" -> AlertRoutingDialog(vehicle) { page = "" }
        "rides" -> RideReportsDialog { page = "" }
        "events" -> AlertHistoryDialog { page = "" }
        "storage" -> StorageRetentionDialog { page = "" }
    }
}

@Composable
public fun MaintenanceModeDialog(vehicle: Vehicle, dismiss: () -> Unit) {
    val mode = remember { (appGraph as VehicleToolsDependencies).maintenanceMode }
    val windows by mode.windows.collectAsState()
    var remaining by remember { mutableStateOf(mode.remainingMinutes(vehicle.uuid)) }
    var minutes by remember { mutableStateOf("15") }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(windows) { while (true) { remaining = mode.remainingMinutes(vehicle.uuid); delay(1000) } }
    MonitoringToolDialog("${vehicle.name} · Maintenance", dismiss) {
        Text(if (remaining > 0) "Maintenance active · $remaining min remaining" else "Maintenance is off")
        Text("While parked, temporarily suppress pressure, leak, imbalance and missing-sensor alerts for this " +
            "vehicle. Temperature and battery warnings remain on. Readings continue to be recorded.")
        OutlinedTextField(minutes, { minutes = it }, label = { Text("Minutes (1–60)") }, singleLine = true)
        Button(enabled = minutes.toIntOrNull() in 1..60, onClick = {
            runCatching { mode.start(vehicle.uuid, minutes.toInt()) }
                .onFailure { message = it.message ?: "Could not start maintenance" }
        }) { Text(if (remaining > 0) "Restart timer" else "Start maintenance") }
        if (remaining > 0) TextButton(onClick = { mode.stop(vehicle.uuid) }) { Text("End maintenance now") }
        Text("Automatically ends when the timer expires or the phone restarts. End it before riding.")
        if (message.isNotBlank()) Text(message)
    }
}

@Composable
private fun AlertRoutingDialog(vehicle: Vehicle, dismiss: () -> Unit) {
    val tools = remember { (appGraph as VehicleToolsDependencies).rideTools }
    val data by tools.values.collectAsState()
    val route = data.monitoring.parkedRoutes[vehicle.uuid.toString()] ?: ParkedAlertRoute.ALL
    MonitoringToolDialog("${vehicle.name} · Alert routing", dismiss) {
        Text("The monitored vehicle and its selected trailer always receive full alerts. Choose how this " +
            "vehicle alerts when it is a secondary vehicle. Event history continues for every route.")
        ParkedAlertRoute.entries.forEach { option ->
            TextButton(onClick = { tools.update { it.copy(monitoring = it.monitoring.copy(
                parkedRoutes = it.monitoring.parkedRoutes + (vehicle.uuid.toString() to option))) } }) {
                Text("${if (route == option) "✓ " else ""}" + when (option) {
                    ParkedAlertRoute.ALL -> "All alerts: notifications, sound and vibration"
                    ParkedAlertRoute.NOTIFICATION_ONLY -> "Notifications only"
                    ParkedAlertRoute.CRITICAL_ONLY -> "Critical pressure / temperature only"
                    ParkedAlertRoute.OFF -> "History only"
                })
            }
        }
        Text("Sound and vibration still follow your alert settings and silence controls. Critical means " +
            "at least 25% below the low pressure limit, 20% above the high limit, or 20°C above the temperature limit.")
    }
}
