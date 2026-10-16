@file:Suppress("LongMethod", "TooGenericExceptionCaught")

package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.data.vehicle.ioc.VehicleToolsDependencies
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.PressureProfile
import com.masselis.tpmsadvanced.data.vehicle.model.TyreSpecification
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.specificationComparison
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
public fun ManufacturerSpecificationsButton(vehicle: Vehicle) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text("Manufacturer tyre specifications") }
    if (open) ManufacturerSpecifications(vehicle) { open = false }
}

@Composable
private fun ManufacturerSpecifications(vehicle: Vehicle, close: () -> Unit) {
    val tools = remember { appGraph as VehicleToolsDependencies }
    val data by tools.rideTools.values.collectAsState()
    val unit by tools.toolsUnits.pressure.collectAsState()
    val current by remember(vehicle.uuid) { tools.toolsVehicles.selectByUuid(vehicle.uuid).asFlow() }
        .collectAsState(vehicle)
    val records = data.vehicles[vehicle.uuid.toString()]?.specifications.orEmpty()
    var edit by remember { mutableStateOf<TyreSpecification?>(null) }
    var delete by remember { mutableStateOf<TyreSpecification?>(null) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    RideToolsDialog("${vehicle.name} · Handbook specifications", close) {
        Text("Copy the cold-pressure recommendations from this vehicle's handbook or placard. " +
            "Include the source and load condition. These reference targets never change your alert limits.")
        TextButton(onClick = { edit = TyreSpecification(load = "", frontSize = "", frontKpa = 0f,
            rearKpa = 0f, source = "") }) { Text("Add load-specific recommendation") }
        if (records.isEmpty()) Text("No handbook recommendations recorded.")
        val currentProfile = PressureProfile(name = "Current limits", low = current.lowPressure.kpa,
            high = current.highPressure.kpa, separateRear = current.separateFrontRearPressure,
            rearLow = current.rearLowPressure.kpa, rearHigh = current.rearHighPressure.kpa)
        records.forEach { spec -> Card { Column(Modifier.padding(12.dp)) {
            Text(spec.load, style = MaterialTheme.typography.titleLarge)
            Text("Front / single axle: ${spec.frontSize} · ${Pressure(spec.frontKpa).string(unit)} cold")
            Text("Rear: ${spec.rearSize.ifBlank { spec.frontSize }} · ${Pressure(spec.rearKpa).string(unit)} cold")
            Text("Source: ${spec.source}")
            if (spec.notes.isNotBlank()) Text(spec.notes)
            (listOf(currentProfile) + data.vehicles[vehicle.uuid.toString()]?.profiles.orEmpty()).forEach { profile ->
                Text("${profile.name}: front ${Pressure(profile.low).string(unit)}–" +
                    "${Pressure(profile.high).string(unit)}; " +
                    "rear ${Pressure(if (profile.separateRear) profile.rearLow else profile.low).string(unit)}–" +
                    Pressure(if (profile.separateRear) profile.rearHigh else profile.high).string(unit))
                specificationComparison(spec, profile).forEach { Text(it) }
            }
            TextButton(onClick = { edit = spec }) { Text("Edit") }
            TextButton(onClick = { delete = spec }) { Text("Remove…") }
        } } }
        Text(message)
    }
    edit?.let { spec -> SpecificationEditor(vehicle, spec) { edit = null } }
    delete?.let { spec -> AlertDialog(onDismissRequest = { delete = null }, title = { Text("Remove recommendation?") },
        text = { Text("Remove ${spec.load}? Your pressure profiles and alert limits will be retained.") },
        confirmButton = { TextButton(onClick = {
            delete = null
            scope.launch { try { withContext(Dispatchers.IO) { tools.rideTools.updateVehicle(vehicle.uuid) {
                it.copy(specifications = it.specifications.filterNot { entry -> entry.id == spec.id })
            } } } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message = "Could not remove recommendation." } }
        }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { delete = null }) { Text("Cancel") } }) }
}

@Composable
private fun SpecificationEditor(vehicle: Vehicle, initial: TyreSpecification, close: () -> Unit) {
    val tools = remember { appGraph as VehicleToolsDependencies }
    val unit = remember { tools.toolsUnits.pressure.value }
    val scope = rememberCoroutineScope()
    var load by remember { mutableStateOf(initial.load) }
    var front by remember { mutableStateOf(initial.frontSize) }
    var rear by remember { mutableStateOf(initial.rearSize) }
    var frontPressure by remember { mutableStateOf(initial.frontKpa.takeIf { it > 0 }
        ?.let { Pressure(it).convert(unit).toString() }.orEmpty()) }
    var rearPressure by remember { mutableStateOf(initial.rearKpa.takeIf { it > 0 }
        ?.let { Pressure(it).convert(unit).toString() }.orEmpty()) }
    var source by remember { mutableStateOf(initial.source) }
    var notes by remember { mutableStateOf(initial.notes) }
    var message by remember { mutableStateOf("") }
    RideToolsDialog("Handbook recommendation", close) {
        OutlinedTextField(load, { load = it }, label = { Text("Load condition (Solo, Towing, Fully loaded…)") })
        OutlinedTextField(front, { front = it }, label = { Text("Front / single-axle tyre size") })
        OutlinedTextField(rear, { rear = it }, label = { Text("Rear tyre size (blank = same)") })
        OutlinedTextField(frontPressure, { frontPressure = it }, label = { Text("Front cold pressure (${unit.name})") })
        OutlinedTextField(rearPressure, { rearPressure = it }, label = { Text("Rear cold pressure (blank = same)") })
        OutlinedTextField(source, { source = it }, label = { Text("Handbook / placard source and page") })
        OutlinedTextField(notes, { notes = it }, label = { Text("Additional load or tyre notes") })
        TextButton(onClick = { scope.launch {
            try {
                fun pressure(text: String) = checkNotNull(text.replace(',', '.').toFloatOrNull()) {
                    "Enter a valid cold pressure."
                } / Pressure(1f).convert(unit)
                val spec = initial.copy(load = load.trim(), frontSize = front.trim(), rearSize = rear.trim(),
                    frontKpa = pressure(frontPressure), rearKpa = pressure(rearPressure.ifBlank { frontPressure }),
                    source = source.trim(), notes = notes.trim()).also { it.validate() }
                withContext(Dispatchers.IO) { tools.rideTools.updateVehicle(vehicle.uuid) {
                    it.copy(specifications = it.specifications.filterNot { old -> old.id == spec.id } + spec)
                } }
                close()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                message = "Check the load condition, tyre size, source and positive pressure values."
            }
        } }) { Text("Save reference specification") }
        Text(message)
    }
}
