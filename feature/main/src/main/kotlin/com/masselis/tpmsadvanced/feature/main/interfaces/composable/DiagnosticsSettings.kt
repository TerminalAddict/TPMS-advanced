package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BleDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("TooGenericExceptionCaught") // Storage providers can fail with I/O or platform exceptions.
@Composable
public fun DiagnosticsSettings() {
    val enabled by BleDiagnostics.enabled.collectAsState()
    val status by BleDiagnostics.status.collectAsState()
    val latest by BleDiagnostics.latest.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    var clear by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-ndjson")
    ) { uri ->
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    checkNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use {
                        BleDiagnostics.export(it)
                    }
                }
                message = "Capture exported"
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message = "Could not export capture" }
        }
    }
    Column {
        Text("BLE diagnostics", style = MaterialTheme.typography.titleMedium)
        Text("Capture TPMS advertisements while monitoring, or all nearby BLE packets during sensor discovery. " +
            "Includes Bluetooth addresses, " +
            "timestamps, RSSI, full packet bytes and available status bits. " +
            "About 4 MB retained; oldest packets rotate out. " +
            "Capture starts off after every app restart. Unknown bits are not interpreted as alarms.")
        Row { Text("Capture packets"); Switch(enabled, { BleDiagnostics.enabled.value = it }) }
        Text(status)
        if (latest.isNotEmpty()) Text(latest.take(1_200), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { export.launch("TyrePing-BLE-capture.jsonl") }) { Text("Export saved capture") }
        TextButton(onClick = { clear = true }) { Text("Clear saved capture…") }
        if (message.isNotEmpty()) Text(message)
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("Clear saved BLE capture?") },
        text = { Text("This removes the packets saved on this phone. Export a copy first if you need them.") },
        dismissButton = { TextButton(onClick = { clear = false }) { Text("Cancel") } },
        confirmButton = { TextButton(onClick = {
            clear = false
            scope.launch {
                try { BleDiagnostics.clear() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { message = "Could not clear capture" }
            }
        }) { Text("Clear") } })
}
