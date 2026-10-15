package com.masselis.tpmsadvanced.interfaces.composable

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.core.ui.restartApp
import com.masselis.tpmsadvanced.data.vehicle.ioc.VehicleToolsDependencies
import com.masselis.tpmsadvanced.data.vehicle.model.ConfigurationBackup
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.stopMonitoringForRestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("LongMethod", "CyclomaticComplexMethod", "TooGenericExceptionCaught", "ThrowsCount")
// Each document-provider operation reports failures and rethrows cancellation.
@Composable
internal fun BackupSettings() {
    val context = LocalContext.current
    val repository = remember { (appGraph as VehicleToolsDependencies).toolsBackups }
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<ConfigurationBackup?>(null) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var exportPrevious by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val text = if (exportPrevious) repository.previousBackup() else repository.create().encode()
                withContext(Dispatchers.IO) {
                    checkNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(text) }
                }
                message = "Configuration backup exported"
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { message = "Export failed: ${failure.message}" }
            finally { busy = false }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                pending = withContext(Dispatchers.IO) {
                    checkNotNull(context.contentResolver.openInputStream(uri)).reader().use { reader ->
                        val text = StringBuilder()
                        val buffer = CharArray(4_096)
                        var count = reader.read(buffer)
                        while (count >= 0) {
                            text.append(buffer, 0, count)
                            require(text.length <= ConfigurationBackup.MAX_CHARACTERS) { "Backup is too large" }
                            count = reader.read(buffer)
                        }
                        ConfigurationBackup.decode(text.toString())
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { message = "Import rejected: ${failure.message}" }
            finally { busy = false }
        }
    }
    Column {
        Text("Backup and restore", style = MaterialTheme.typography.titleMedium)
        Text("Save vehicles, bindings, calibration, alert settings, units and ride triggers. " +
            "Reading history and BLE captures have separate exports. " +
            "Phone permissions and running sessions are not restored.")
        TextButton(enabled = !busy,
            onClick = { exportPrevious = false; export.launch("TyrePing-configuration.json") }) {
            Text("Export configuration")
        }
        TextButton(enabled = !busy, onClick = { import.launch(arrayOf("application/json", "text/plain")) }) {
            Text("Import configuration…")
        }
        TextButton(enabled = !busy,
            onClick = { exportPrevious = true; export.launch("TyrePing-before-restore.json") }) {
            Text("Export safety copy from last restore")
        }
        Text(if (busy) "Working…" else message)
    }
    pending?.let { backup ->
        AlertDialog(onDismissRequest = { if (!busy) pending = null }, title = { Text("Restore configuration?") },
            text = { Text("${backup.vehicles.size} vehicles and " +
                "${backup.vehicles.sumOf { it.sensors.size }} sensor bindings. " +
                "Matching vehicle IDs and their bindings will be replaced; other vehicles and recorded history stay. " +
                "Sensors may move from another vehicle. Global settings will change. A safety copy is saved first. " +
                "Monitoring stops and TyrePing restarts; automatic watches must be armed again.") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    try {
                        stopMonitoringForRestore(context)
                        repository.restore(backup)
                        context.restartApp()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { message = "Restore failed: ${failure.message}"; pending = null }
                    finally { busy = false }
                }
            }) { Text("Restore and restart") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { pending = null }) { Text("Cancel") } })
    }
}
