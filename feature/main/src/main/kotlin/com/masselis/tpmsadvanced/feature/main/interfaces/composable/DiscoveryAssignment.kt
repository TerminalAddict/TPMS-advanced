package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.data.vehicle.ioc.VehicleToolsDependencies
import com.masselis.tpmsadvanced.data.vehicle.model.BleAdvertisement
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Suppress("LongMethod", "LongParameterList", "TooGenericExceptionCaught")
@Composable
internal fun DiscoveryAssignmentDialog(vehicle: Vehicle, wheel: Location, packet: BleAdvertisement,
    tools: VehicleToolsDependencies, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val tyre = packet.reading as? Tyre ?: return
    var target by remember { mutableStateOf<Sensor?>(null) }
    var source by remember { mutableStateOf<Pair<UUID, Sensor>?>(null) }
    var message by remember { mutableStateOf("Loading current assignments…") }
    var ready by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(packet, vehicle.uuid, wheel) {
        try {
            val (destination, owner, binding) = withContext(Dispatchers.IO) {
                Triple(tools.toolsSensors.selectByVehicleAndLocation(vehicle.uuid, wheel).execute(),
                    tools.toolsVehicles.selectBySensorId(tyre.sensorId).execute(),
                    tools.toolsSensors.selectById(tyre.sensorId).execute())
            }
            target = destination
            source = if (owner != null && binding != null) owner.uuid to binding else null
            message = assignmentMessage(vehicle, wheel, packet, destination, owner)
            ready = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { message = "Could not load bindings. Close and try again." }
    }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("Confirm discovered sensor") },
        text = { Text(message) }, dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }, confirmButton = {
            TextButton(enabled = ready && !saving, onClick = {
                saving = true
                scope.launch {
                    try {
                        require(now() - packet.timestamp in 0.0..60.0) {
                            "Reading is stale. Scan and review again."
                        }
                        tools.toolsScanner.rememberDiscoveredSensor(tyre.sensorId, packet.address)
                        tools.toolsSensors.assign(Sensor(tyre.sensorId, wheel), vehicle.uuid, target, source)
                        onSaved()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) {
                        ready = false; message = failure.message ?: "Could not save assignment."
                    }
                    finally { saving = false }
                }
            }) { Text("Assign sensor") }
        })
}

private fun assignmentMessage(vehicle: Vehicle, wheel: Location, packet: BleAdvertisement,
    destination: Sensor?, owner: Vehicle?): String =
    "Assign sensor ${packet.reading?.sensorId?.toUInt()} (${packet.address}) to ${wheelLabel(wheel)} " +
        "on ${vehicle.name}?\n\nCheck that its pressure and temperature match the tyre you just fitted. " +
        "Discovery does not verify the sensor model.\n\n" +
        (destination?.let { "Replaces sensor ${it.id.toUInt()} at this wheel.\n" } ?: "") +
        (owner?.let { "Removes its current assignment on ${it.name}." } ?: "")
