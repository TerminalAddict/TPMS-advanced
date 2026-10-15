package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus
import com.masselis.tpmsadvanced.data.vehicle.model.BleAdvertisement
import com.masselis.tpmsadvanced.data.vehicle.model.BleDiscoverySession
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import java.util.Locale

@Suppress("LongParameterList", "LongMethod") // Page with supplied scan state, also used for layout verification.
@Composable
internal fun DiscoveryContent(
    vehicle: String, wheels: Collection<Location>, wheel: Location, onWheel: (Location) -> Unit,
    devices: List<BleDiscoverySession.Device>, scanning: Boolean, realScanner: Boolean, status: String,
    now: Double, received: Long, marked: Boolean, expanded: String?, message: String,
    onStart: () -> Unit, onStop: () -> Unit, onMark: () -> Unit, onClear: () -> Unit,
    onExpand: (String) -> Unit, onAssign: (BleAdvertisement) -> Unit, onExport: (String?) -> Unit,
    onClose: () -> Unit,
) {
    var help by remember { mutableStateOf(false) }
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Discover sensors", style = MaterialTheme.typography.headlineSmall)
        TextButton(onClick = onClose) { Text("Close") }
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("$vehicle · choose a wheel", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                wheels.forEach { item -> FilterChip(item == wheel, { onWheel(item) }, { Text(wheelLabel(item)) }) }
            }
            Text("Start scanning, mark installation, then fit one sensor while parked. " +
                "Check its pressure and temperature before assigning.")
            TextButton(onClick = { help = !help }) {
                Text(if (help) "Hide guidance" else "T05-04 / discovery guidance")
            }
            if (help) {
                Text("Start with the sensor off the valve. After marking, inspect new or changed devices. " +
                    "Nearby devices may also appear. Close HRTPMS for an independent check.")
                Text("T05 / T05-04: F.W / R.W markings are wheel labels, not unique IDs. " +
                    "HRTPMS compatibility alone does not confirm the packet format.")
                Text("Retains 200 devices and the latest 1,000 packets. Older entries rotate out. " +
                    "Exports include nearby Bluetooth addresses. Capture must be exported before closing.")
            }
            FlowRow {
                TextButton(onClick = if (scanning) onStop else onStart, enabled = realScanner) {
                    Text(if (scanning) "Stop scan" else "Start scan (2 min)")
                }
                TextButton(onClick = onMark, enabled = scanning) { Text("Mark, then fit sensor") }
                TextButton(onClick = { onExport(null) }, enabled = received > 0) { Text("Export all packets") }
                TextButton(onClick = onClear, enabled = !scanning && received > 0) { Text("Clear session") }
            }
            Text(if (!realScanner) "Discovery needs real Bluetooth. Exit demo mode first."
                else if (scanning) status else "Scan stopped · start to receive new packets")
            if (marked) Text("Installation marked · inspect new/changed devices")
            if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodyMedium)
            Text("${devices.size} devices · $received packets received. Scan stops after 2 min or when hidden. " +
                "Export before closing; session capture is temporary.",
                style = MaterialTheme.typography.bodySmall)
            if (devices.isEmpty()) Text("No broadcasts yet. Check Bluetooth and permissions, keep the phone nearby, " +
                "and fit one sensor. On Android 9–11, enable Location. Close HRTPMS for an independent check.")
        }
        // Stable order while inspecting; RSSI changes must not move the button under a finger.
        items(devices.sortedWith(compareBy({ it.firstSeen }, { it.latest.address })),
            key = { it.latest.address }) { device ->
            DiscoveryDeviceCard(device, now, expanded == device.latest.address,
                { onExpand(device.latest.address) }, { device.decoded?.let(onAssign) },
                { onExport(device.latest.address) })
        }
    }
}

@Suppress("LongMethod")
@Composable
internal fun DiscoveryDeviceCard(device: BleDiscoverySession.Device, now: Double, expanded: Boolean,
    onExpand: () -> Unit, onAssign: () -> Unit, onExport: () -> Unit) {
    val packet = device.latest
    val decoded = device.decoded
    val tyre = decoded?.reading as? Tyre
    val fresh = decoded != null && now - decoded.timestamp in 0.0..60.0
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(packet.name?.takeIf { it.isNotBlank() } ?: "Unnamed BLE device",
                style = MaterialTheme.typography.titleMedium)
            Text(packet.address, fontFamily = FontFamily.Monospace)
            Text("${if (packet.rssi == 127) "RSSI unavailable" else "${packet.rssi} dBm"} · " +
                "last seen ${age(now, packet.timestamp)}s ago · ${device.packets} packets")
            if (device.newSinceMark) Text("New since installation mark")
            if (device.pressureChanged) Text("Pressure changed since mark")
            else if (device.payloadChanged) Text("Packet changed since mark")
            if (tyre != null) {
                Text("Recognised · ${decoded.decoder}", color = MaterialTheme.colorScheme.primary)
                Text("ID ${tyre.sensorId.toUInt()} · ${number(tyre.pressure.kpa)} kPa / " +
                    "${number(tyre.pressure.asPsi())} psi · ${number(tyre.temperature.celsius)} °C")
                Text("Raw decoded values · reading ${age(now, decoded.timestamp)}s ago" +
                    if (fresh) "" else " · scan again before assigning")
            } else Text("Unknown protocol · capture packets to investigate. This may be another kind of BLE device.")
            FlowRow {
                TextButton(onClick = onExpand) { Text(if (expanded) "Hide packet" else "Inspect packet") }
                if (tyre != null) TextButton(onClick = onAssign, enabled = fresh) { Text("Assign to selected wheel…") }
                TextButton(onClick = onExport) { Text("Export device") }
            }
            if (expanded) SelectionContainer { Column {
                Text("Latest advertisement", style = MaterialTheme.typography.titleSmall)
                Text("${packet.bytes.ifEmpty { "No advertisement bytes supplied by Android" }}",
                    fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                Text("Manufacturer data (company ID → bytes):")
                Text(packet.manufacturerData.entries.joinToString("\n") { "${it.key}: ${it.value}" }.ifEmpty { "None" },
                    fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                Text("Service UUIDs:\n${packet.serviceUuids.joinToString("\n").ifEmpty { "None" }}")
                Text("Service data:\n${packet.serviceData.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                    .ifEmpty { "None" }}", style = MaterialTheme.typography.bodySmall)
                Text("Connectable: ${packet.connectable} · data status: ${packet.dataStatus} (0 = complete)")
                Text("Latest packet decoder: ${packet.decoder ?: "none"}")
                packet.status?.let { Text(it) }
            } }
        }
    }
}

private fun number(value: Float): String = String.format(Locale.ROOT, "%.1f", value)
private fun age(now: Double, then: Double): Long = (now - then).coerceAtLeast(0.0).toLong()

internal fun discoveryStatus(status: ScanStatus): String = when (status) {
    ScanStatus.Idle -> "Preparing discovery…"
    ScanStatus.Starting -> "Starting scan…"
    ScanStatus.Scanning -> "Scanning all BLE advertisements"
    ScanStatus.WaitingForBluetooth -> "Bluetooth is off · enable it to resume"
    is ScanStatus.Retrying -> "Android refused a scan (code ${status.reason ?: "Bluetooth unavailable"}) · retrying…"
    is ScanStatus.Failed -> "Scan failed · check Bluetooth and permissions, then restart scanning"
}
