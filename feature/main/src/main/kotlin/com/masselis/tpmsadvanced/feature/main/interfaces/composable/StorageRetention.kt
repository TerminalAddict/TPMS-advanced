@file:Suppress("MagicNumber", "LongMethod", "TooGenericExceptionCaught")

package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.data.vehicle.ioc.VehicleToolsDependencies
import com.masselis.tpmsadvanced.data.vehicle.interfaces.RetentionPlan
import com.masselis.tpmsadvanced.data.vehicle.interfaces.StorageCounts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
@Suppress("CyclomaticComplexMethod") // Picker outcomes, validated fields and destructive-action confirmation.
internal fun StorageRetentionDialog(dismiss: () -> Unit) {
    val tools = remember { appGraph as VehicleToolsDependencies }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val initial = remember { tools.rideTools.read().monitoring }
    var readings by remember { mutableStateOf(initial.readingDays.toString()) }
    var events by remember { mutableStateOf(initial.eventDays.toString()) }
    var reports by remember { mutableStateOf(initial.reportDays.toString()) }
    var healthDays by remember { mutableStateOf(initial.sensorHealthDays.toString()) }
    var counts by remember { mutableStateOf(StorageCounts(0, 0, 0)) }
    var bytes by remember { mutableStateOf(0L) }
    var plan by remember { mutableStateOf<RetentionPlan?>(null) }
    var exportPlan by remember { mutableStateOf<RetentionPlan?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    suspend fun refresh() { counts = tools.monitoringHistory.counts(); bytes = tools.monitoringHistory.bytes() }
    fun run(action: suspend () -> Unit) {
        scope.launch {
            busy = true
            try { action(); refresh() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { message = failure.message ?: "Storage operation failed" }
            finally { busy = false }
        }
    }
    val archive = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-ndjson")) { uri ->
        val selected = exportPlan
        if (uri != null && selected != null) run {
            withContext(Dispatchers.IO) {
                checkNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use {
                    tools.monitoringHistory.archive(selected, it)
                }
            }
            message = "Archive exported. Original data is still stored."
        }
        exportPlan = null
    }
    LaunchedEffect(Unit) { run { refresh() } }
    MonitoringToolDialog("Storage and retention", dismiss) {
        Text("Database: %.2f MB (including journal)".format(bytes / 1048576.0))
        Text("${counts.readings} readings · ${counts.events} alert events · ${counts.reports} ride reports")
        Text("${counts.healthDays} daily sensor-health summaries. Inventory and installation history are preserved; " +
            "diagnostic issue groups are capped at 1000.")
        Text("Keep records for this many days (0 = keep everything). Cleanup is manual after preview. " +
            "Active alerts, active rides and the latest readings of assigned sensors are protected.")
        OutlinedTextField(readings, { readings = it; plan = null }, label = { Text("Reading days (0–3650)") })
        OutlinedTextField(events, { events = it; plan = null }, label = { Text("Alert history days (0–3650)") })
        OutlinedTextField(reports, { reports = it; plan = null }, label = { Text("Ride report days (0–3650)") })
        OutlinedTextField(healthDays, { healthDays = it; plan = null },
            label = { Text("Sensor-health days (0–3650)") })
        Button(enabled = !busy && listOf(readings, events, reports, healthDays).all { it.toIntOrNull() in 0..3650 },
            onClick = {
            run {
                tools.rideTools.update { it.copy(monitoring = it.monitoring.copy(readingDays = readings.toInt(),
                    eventDays = events.toInt(), reportDays = reports.toInt(), sensorHealthDays = healthDays.toInt())) }
                plan = tools.monitoringHistory.plan(tools.rideTools.read().monitoring, now())
                message = "Limits saved. Review the records below before deleting."
            }
        }) { Text("Save limits and preview") }
        plan?.let { preview ->
            Text("Eligible: ${preview.counts.readings} readings, ${preview.counts.events} alerts, " +
                "${preview.counts.reports} reports, ${preview.counts.healthDays} daily sensor-health summaries")
            TextButton(enabled = !busy, onClick = {
                exportPlan = preview; archive.launch("TyrePing-history-archive.jsonl")
            }) { Text("Archive eligible records…") }
            TextButton(enabled = !busy && preview.counts != StorageCounts(0, 0, 0),
                onClick = { confirm = true }) { Text("Review cleanup…") }
        }
        Text("Stop monitoring before archiving or cleaning up. Archives contain raw readings, event details and " +
            "report summaries in JSON Lines, for offline analysis; they are not configuration restore files. " +
            "Deleted database pages are reused by future readings, so the file size may not shrink immediately.")
        Text(if (busy) "Working…" else message)
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Delete older history?") },
        text = { Text("This permanently deletes the previewed records. Export an archive first if you want to " +
            "keep them. Vehicles, bindings, calibration and settings are preserved.") },
        confirmButton = { TextButton(onClick = {
            confirm = false
            val selected = plan ?: return@TextButton
            run { tools.monitoringHistory.cleanup(selected); plan = null; message = "Cleanup complete" }
        }) { Text("Delete eligible records") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}
