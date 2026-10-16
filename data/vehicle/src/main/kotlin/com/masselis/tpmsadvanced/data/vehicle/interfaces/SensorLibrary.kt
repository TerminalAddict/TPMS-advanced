@file:Suppress("MagicNumber", "TooManyFunctions") // Persistence units and bounded diagnostic history.

package com.masselis.tpmsadvanced.data.vehicle.interfaces

import com.masselis.tpmsadvanced.data.vehicle.Database
import com.masselis.tpmsadvanced.data.vehicle.SensorInventory
import com.masselis.tpmsadvanced.data.vehicle.SensorInstallation
import com.masselis.tpmsadvanced.data.vehicle.SensorIssue
import com.masselis.tpmsadvanced.data.vehicle.model.BleAdvertisement
import com.masselis.tpmsadvanced.data.vehicle.model.HealthReceptionTracker
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBattery
import com.masselis.tpmsadvanced.data.vehicle.model.SensorHealthDay
import com.masselis.tpmsadvanced.data.vehicle.model.SensorMetadata
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.suspiciousReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shared by the radio, inventory and backup. Inventory has no binding FK: spares and old identities survive. */
public class SensorLibrary(private val database: Database, private val rideTools: RideToolsStore) {
    private val queries = database.sensorLibraryQueries
    private val mutex = Mutex()
    private val reception = HealthReceptionTracker()
    public fun scanStatus(status: BluetoothLeScanner.ScanStatus) {
        reception.available(status == BluetoothLeScanner.ScanStatus.Scanning)
    }
    public suspend fun inventory(): List<SensorInventory> = withContext(Dispatchers.IO) {
        queries.inventory().executeAsList()
    }
    public suspend fun metadata(value: SensorMetadata): Unit = withContext(Dispatchers.IO) {
        value.validate()
        database.transaction {
            queries.ensureSensor(value.id.toLong())
            queries.saveMetadata(value.label.trim(), value.manufacturer.trim(), value.notes.trim(), value.id.toLong())
        }
    }
    public suspend fun installations(id: Int): List<SensorInstallation> = withContext(Dispatchers.IO) {
        queries.installations(id.toLong()).executeAsList()
    }
    public suspend fun issues(id: Int? = null): List<SensorIssue> = withContext(Dispatchers.IO) {
        queries.issues(id?.toLong()).executeAsList()
    }
    public suspend fun days(id: Int, fromDay: Long): List<SensorHealthDay> = withContext(Dispatchers.IO) {
        queries.daily(id.toLong(), fromDay).executeAsList().map {
            SensorHealthDay(it.day, it.packets,
                if (it.rssiCount > 0) it.rssiTotal.toDouble() / it.rssiCount else null,
                if (it.voltageCount > 0) it.voltageTotal / it.voltageCount else null,
                if (it.percentCount > 0) it.percentTotal / it.percentCount else null,
                it.observedSeconds, it.missed, it.longestGap, it.questionable)
        }
    }

    /** A persistence failure must never stop radio readings or their existing emergency alerts. */
    @Suppress("CyclomaticComplexMethod") // One transaction preserves optional fields, identity warnings and counters.
    public suspend fun record(packet: BleAdvertisement, expectedId: Int? = null): Unit =
        withContext(Dispatchers.IO) { mutex.withLock {
        database.transaction {
            val reading = packet.reading
            val previousId = expectedId?.toLong() ?: queries.radioIdentity(packet.address).executeAsOneOrNull()
            val identity = reading?.sensorId?.toLong() ?: previousId
            val problems = packet.qualityWarnings.toMutableList()
            if (reading != null && previousId != null && previousId != identity) {
                val message = "Sensor ID changed at the same Bluetooth address: ${previousId.toInt().toUInt()} → " +
                    "${reading.sensorId.toUInt()}. Assignments were not changed."
                problems += message
                queries.markWarning(message, packet.timestamp, previousId)
            }
            if (reading is Tyre) problems += suspiciousReading(reading.pressure.kpa,
                reading.temperature.celsius, reading.sensorBattery)
            val warning = problems.distinct().takeIf { it.isNotEmpty() }?.joinToString("; ")
            if (reading != null) {
                val id = reading.sensorId.toLong()
                queries.ensureSensor(id)
                val tyre = reading as? Tyre
                val volts = (tyre?.sensorBattery as? SensorBattery.Voltage)?.volts?.toDouble()
                    ?.takeIf { it.isFinite() && it in 0.0..5.0 }
                val percent = (tyre?.sensorBattery as? SensorBattery.Percentage)?.percent?.toLong()
                    ?.takeIf { it in 0..100 }
                queries.seen(packet.decoder.orEmpty(), packet.address, packet.timestamp, volts, percent,
                    tyre?.pressure?.kpa?.toDouble()?.takeIf { it.isFinite() },
                    tyre?.temperature?.celsius?.toDouble()?.takeIf { it.isFinite() }, warning, id)
                val gap = reception.record(reading.sensorId, packet.elapsedNanos,
                    rideTools.values.value.insights.broadcastSeconds[reading.sensorId])
                val rssi = packet.rssi.takeIf { it in -127..20 }
                queries.addDaily(id, (packet.timestamp / 86400).toLong(), rssi?.toLong() ?: 0,
                    if (rssi != null) 1 else 0, volts ?: 0.0, if (volts != null) 1 else 0,
                    percent?.toDouble() ?: 0.0, if (percent != null) 1 else 0, gap.seconds, gap.missed,
                    gap.seconds, if (warning != null) 1 else 0)
                queries.rememberIdentity(packet.address, id)
            } else if (warning != null && identity != null) queries.markWarning(warning, packet.timestamp, identity)
            if (warning != null) {
                queries.issue("${packet.address}:$identity:$warning", identity, packet.address, warning,
                    packet.timestamp, packet.copy(qualityWarnings = problems.distinct()).jsonLine())
                queries.trimIssues()
            }
        }
    } }
}
