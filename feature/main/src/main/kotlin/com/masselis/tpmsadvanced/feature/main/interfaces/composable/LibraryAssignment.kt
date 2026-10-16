@file:Suppress("LongMethod", "TooGenericExceptionCaught")

package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.data.vehicle.SensorInventory
import com.masselis.tpmsadvanced.data.vehicle.ioc.VehicleToolsDependencies
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
@Suppress("CyclomaticComplexMethod") // Explicit source/target review, cancellation and stale-binding protection.
internal fun LibraryAssignment(item: SensorInventory, close: () -> Unit) {
    val tools = remember { appGraph as VehicleToolsDependencies }
    val scope = rememberCoroutineScope()
    var vehicles by remember { mutableStateOf(emptyList<Vehicle>()) }
    var selected by remember { mutableStateOf<Vehicle?>(null) }
    var wheel by remember { mutableStateOf<Location?>(null) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var save by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    fun run(action: suspend () -> Unit) { scope.launch {
        busy = true
        try { action() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { message = failure.message ?: "Assignment failed" }
        finally { busy = false }
    } }
    LaunchedEffect(Unit) { run {
        vehicles = withContext(Dispatchers.IO) { tools.toolsVehicles.selectAll().execute() }
    } }
    RideToolsDialog("Assign sensor ${item.id.toInt().toUInt()}", close) {
        Text("Choose a vehicle and wheel. Review any existing assignments before confirming.")
        FlowRow { vehicles.forEach { vehicle -> FilterChip(selected?.uuid == vehicle.uuid,
            { selected = vehicle; wheel = null }, label = { Text(vehicle.name) }) } }
        selected?.let { vehicle -> FlowRow { vehicle.kind.locations.forEach { location ->
            FilterChip(wheel == location, { wheel = location }, label = { Text(wheelLabel(location)) })
        } } }
        TextButton(enabled = selected != null && wheel != null && !busy, onClick = {
            val vehicle = selected ?: return@TextButton
            val location = wheel ?: return@TextButton
            run {
                val id = item.id.toInt()
                val (target, source) = withContext(Dispatchers.IO) {
                    val owner = tools.toolsVehicles.selectBySensorId(id).execute()
                    tools.toolsSensors.selectByVehicleAndLocation(vehicle.uuid, location).execute() to
                        owner?.let { it to checkNotNull(tools.toolsSensors.selectById(id).execute()) }
                }
                confirmation = "Assign sensor ${id.toUInt()} to ${vehicle.name}, ${wheelLabel(location)}? " +
                    (target?.let { "Current sensor ${it.id.toUInt()} will become a spare. " } ?: "") +
                    (source?.let { "This sensor leaves ${it.first.name}, ${wheelLabel(it.second.location)}. " } ?: "") +
                    "Historical readings and calibration are preserved."
                save = {
                    if (item.address.isNotEmpty()) tools.toolsScanner.rememberDiscoveredSensor(id, item.address)
                    tools.toolsSensors.assign(Sensor(id, location), vehicle.uuid, target,
                        source?.let { it.first.uuid to it.second })
                    close()
                }
            }
        }) { Text("Review assignment…") }
        Text(message)
    }
    confirmation?.let { prompt -> AlertDialog(onDismissRequest = { confirmation = null },
        title = { Text("Confirm assignment") }, text = { Text(prompt) }, confirmButton = {
            TextButton(enabled = !busy, onClick = {
                confirmation = null
                save?.let { run(it) }
            }) { Text("Assign") }
        }, dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } }) }
}
