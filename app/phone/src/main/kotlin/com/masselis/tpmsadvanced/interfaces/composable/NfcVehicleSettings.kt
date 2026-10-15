package com.masselis.tpmsadvanced.interfaces.composable

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.masselis.tpmsadvanced.core.ui.findActivity
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import java.util.concurrent.atomic.AtomicBoolean

@Composable
internal fun NfcVehicleSettings(vehicle: Vehicle) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val adapter = remember { NfcAdapter.getDefaultAdapter(context) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var writing by remember(vehicle.uuid) { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val uri = "tpmsadvanced://vehicle/${vehicle.uuid}"
    Text("NFC vehicle switching", style = MaterialTheme.typography.titleMedium)
    Text("Write a tag for ${vehicle.name}, then tap it with your unlocked phone to open TyrePing " +
        "and select this vehicle. " +
        "This also works for a trailer. It selects the display; it does not start or stop monitoring.")
    Text(uri)
    if (adapter == null) Text("This phone does not support NFC. The vehicle link can still be opened as a shortcut.")
    else TextButton(onClick = {
        if (!adapter.isEnabled) message = "Enable NFC in Android settings first."
        else writing = true
    }) { Text("Write vehicle tag…") }
    Text(message)
    if (writing) AlertDialog(onDismissRequest = { writing = false },
        title = { Text("Hold a writable NFC tag near your phone") },
        text = { Text("Writes ${vehicle.name} and replaces the tag's existing NDEF contents. " +
            "Keep the tag still until confirmation.") },
        confirmButton = { TextButton(onClick = { writing = false }) { Text("Cancel") } })
    DisposableEffect(writing, lifecycle, vehicle.uuid) {
        val busy = AtomicBoolean(false)
        if (writing && lifecycle.isAtLeast(Lifecycle.State.RESUMED) && adapter != null) {
            adapter.enableReaderMode(activity, { tag ->
                if (busy.compareAndSet(false, true)) {
                    val result = runCatching {
                        val payload = NdefMessage(arrayOf(NdefRecord.createUri(uri),
                            NdefRecord.createApplicationRecord(context.packageName)))
                        writeVehicleTag(tag, payload)
                    }
                    activity.runOnUiThread {
                        message = result.fold({
                            "Tag written for ${vehicle.name}. Move it away, then tap again to test."
                        },
                            { "Could not write tag: ${it.message ?: "Tag moved away"}" })
                        writing = false
                    }
                }
            }, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V, null)
        }
        onDispose { if (adapter != null && writing) adapter.disableReaderMode(activity) }
    }
}

private fun writeVehicleTag(tag: Tag, payload: NdefMessage) {
    val ndef = Ndef.get(tag)
    if (ndef != null) {
        try {
            ndef.connect()
            check(ndef.isWritable) { "Tag is read-only" }
            check(ndef.maxSize >= payload.toByteArray().size) { "Tag is too small" }
            ndef.writeNdefMessage(payload)
            check(ndef.ndefMessage == payload) { "Could not verify tag; try again" }
        } finally { ndef.close() }
    } else {
        val formatter = checkNotNull(NdefFormatable.get(tag)) { "Tag does not support NDEF" }
        try { formatter.connect(); formatter.format(payload) } finally { formatter.close() }
    }
}
