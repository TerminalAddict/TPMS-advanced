@file:Suppress("MagicNumber", "LongMethod", "TooGenericExceptionCaught")

package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.data.vehicle.AlertEvent
import com.masselis.tpmsadvanced.data.vehicle.RideReport
import com.masselis.tpmsadvanced.data.vehicle.ioc.VehicleToolsDependencies
import com.masselis.tpmsadvanced.data.vehicle.model.RideSummary
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.masselis.tpmsadvanced.data.vehicle.model.decodeRideSummary
import java.text.DateFormat
import java.util.Date

internal fun reportTime(value: Double): String = DateFormat.getDateTimeInstance(DateFormat.SHORT,
    DateFormat.SHORT).format(Date((value * 1000).toLong()))

@Composable
public fun AlertHistoryDialog(dismiss: () -> Unit) {
    val history = remember { (appGraph as VehicleToolsDependencies).monitoringHistory }
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var page by remember { mutableStateOf(0) }
    var rows by remember { mutableStateOf(emptyList<AlertEvent>()) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(search, page) {
        delay(250)
        try { rows = history.events(search, page); message = if (rows.isEmpty()) "No matching alerts" else "" }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { message = "Could not load alert history" }
    }
    MonitoringToolDialog("Alert event history", dismiss) {
        OutlinedTextField(search, { search = it; page = 0 }, label = {
            Text("Search vehicle, wheel, severity or alert") })
        Text("Dismiss acknowledges a history entry; it does not silence an active tyre warning.")
        rows.forEach { event ->
            HorizontalDivider()
            Text("${event.vehicleName} · ${event.wheel.replace(':', ' ').lowercase()}",
                style = MaterialTheme.typography.titleMedium)
            Text("${event.severity} · ${event.kind}")
            Text("Started ${reportTime(event.started)}\n" +
                (event.ended?.let { "Ended ${reportTime(it)} · ${event.resolution}" } ?: "Active at last observation"))
            event.sensorId?.let { Text("Sensor ${it.toInt().toUInt()}") }
            if (event.dismissed != null) Text("Dismissed ${reportTime(checkNotNull(event.dismissed))}")
            else TextButton(onClick = { scope.launch {
                try { history.dismiss(event.id, now()); rows = history.events(search, page) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { message = "Could not acknowledge event" }
            } }) { Text("Dismiss entry") }
        }
        Text(message)
        ReportPaging(page, rows.size, { page-- }, { page++ })
    }
}

@Composable
public fun RideReportsDialog(dismiss: () -> Unit) {
    val tools = remember { appGraph as VehicleToolsDependencies }
    var page by remember { mutableStateOf(0) }
    var rows by remember { mutableStateOf(emptyList<RideReport>()) }
    var message by remember { mutableStateOf("Loading reports…") }
    val pressure by tools.toolsUnits.pressure.collectAsState()
    val temperature by tools.toolsUnits.temperature.collectAsState()
    LaunchedEffect(page) {
        try { rows = tools.monitoringHistory.rides(page); message =
            if (rows.isEmpty()) "No rides recorded yet" else "" }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { message = "Could not load ride reports" }
    }
    MonitoringToolDialog("Ride summary reports", dismiss) {
        Text("A ride begins when background monitoring starts reading tyres and ends when it stops or the " +
            "ride trigger disconnects. Each vehicle in a towing combination has its own report. No GPS is used.")
        Text("Reports preserve raw readings and the sensor IDs used at the time. " +
            "Calibration changes do not rewrite them.")
        rows.forEach { ride ->
            HorizontalDivider()
            Text(ride.vehicleName, style = MaterialTheme.typography.titleLarge)
            Text("${reportTime(ride.started)} · ${((ride.ended ?: ride.lastSeen) - ride.started).toInt() / 60} min")
            Text(ride.reason ?: "Ride in progress — reopen after stopping for the summary")
            val summary = remember(ride.summary) { ride.summary?.let(::decodeRideSummary) }
            summary?.let { report ->
                Text("${report.alerts} alert events · ${report.wheels.sumOf { it.readings }} readings")
                if (report.neverReported.isNotEmpty()) Text("Never reported: ${report.neverReported.joinToString()}")
                report.wheels.forEach { wheel ->
                    Text(wheel.wheel.replace(':', ' ').lowercase(), style = MaterialTheme.typography.titleMedium)
                    Text("Sensor ${wheel.sensor.toUInt()} · ${wheel.readings} readings\n" +
                        "Start ${Pressure(wheel.startKpa).string(pressure)} → " +
                        "End ${Pressure(wheel.endKpa).string(pressure)}\n" +
                        "Change ${Pressure(wheel.endKpa - wheel.startKpa).string(pressure)} · " +
                        "Maximum ${Temperature(wheel.maxCelsius).string(temperature)}\n" +
                        "${wheel.longGaps} gaps over ${(ride.gapSeconds / 60).toInt()} min · " +
                        "Last seen ${reportTime(wheel.lastAt)}")
                }
            }
        }
        Text(message)
        ReportPaging(page, rows.size, { page-- }, { page++ })
    }
}

@Composable
private fun ReportPaging(page: Int, count: Int, previous: () -> Unit, next: () -> Unit) {
    FlowRow {
        TextButton(enabled = page > 0, onClick = previous) { Text("Newer") }
        Text("Page ${page + 1}")
        TextButton(enabled = count == 30, onClick = next) { Text("Older") }
    }
}
