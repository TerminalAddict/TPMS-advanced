@file:Suppress("LongMethod", "TooGenericExceptionCaught")

package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.data.vehicle.SensorIssue
import com.masselis.tpmsadvanced.data.vehicle.ioc.VehicleToolsDependencies
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
public fun SensorIssuesButton() {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text("Questionable sensor readings") }
    if (open) SensorIssuesDialog(null) { open = false }
}

@Composable
internal fun QualityWarnings(warnings: List<String>) {
    warnings.forEach { Text("Questionable packet: $it", color = MaterialTheme.colorScheme.error) }
}

@Composable
internal fun SensorIssuesDialog(id: Int?, close: () -> Unit) {
    val tools = remember { appGraph as VehicleToolsDependencies }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var issues by remember { mutableStateOf(emptyList<SensorIssue>()) }
    var frozen by remember { mutableStateOf(emptyList<SensorIssue>()) }
    var message by remember { mutableStateOf("") }
    fun refresh() { scope.launch {
        try { issues = tools.sensorLibrary.issues(id) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { message = "Could not load diagnostic history." }
    } }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-ndjson")) {
        uri -> val selected = frozen; frozen = emptyList()
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) { checkNotNull(context.contentResolver.openOutputStream(uri, "wt"))
                    .bufferedWriter().use { writer -> selected.forEach { writer.appendLine(it.packet) } } }
                message = "Diagnostic packets exported. Identifiers are included; review before sharing."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message = "Could not save diagnostic packets." }
        }
    }
    LaunchedEffect(id) { refresh() }
    RideToolsDialog("Questionable readings", close) {
        Text("Plausibility checks flag unusual values; they do not diagnose a faulty sensor. " +
            "Finite extreme readings still reach pressure and temperature alerts. Zero pressure remains a valid " +
            "emergency reading. Undecodable packets cannot update tyre readings and are shown here explicitly.")
        Text("Latest 200 issue groups shown; 1000 retained. Repeated issues update their count and latest raw packet.")
        TextButton(onClick = ::refresh) { Text("Refresh") }
        TextButton(enabled = issues.isNotEmpty(), onClick = {
            frozen = issues; export.launch("TyrePing-questionable-packets.jsonl")
        }) { Text("Export shown diagnostic packets…") }
        if (issues.isEmpty()) Text("No questionable packets recorded.")
        issues.forEach { issue -> Card { Column(Modifier.padding(12.dp)) {
            Text(issue.reason, style = MaterialTheme.typography.titleMedium)
            Text("Sensor ${issue.sensorId?.toInt()?.toUInt() ?: "unknown"} · ${issue.address}")
            Text("${reportTime(issue.firstSeen)} → ${reportTime(issue.lastSeen)} · ${issue.occurrences} occurrences")
        } } }
        Text(message)
    }
}
