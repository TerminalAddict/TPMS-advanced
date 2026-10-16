package com.masselis.tpmsadvanced.data.vehicle.interfaces

import android.content.Context
import android.content.SharedPreferences
import android.util.AtomicFile
import com.masselis.tpmsadvanced.data.vehicle.Database
import com.masselis.tpmsadvanced.data.vehicle.model.BackupCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.BackupRideTrigger
import com.masselis.tpmsadvanced.data.vehicle.model.BackupSensor
import com.masselis.tpmsadvanced.data.vehicle.model.BackupSettings
import com.masselis.tpmsadvanced.data.vehicle.model.BackupVehicle
import com.masselis.tpmsadvanced.data.vehicle.model.ConfigurationBackup
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.backupLocation
import com.masselis.tpmsadvanced.data.vehicle.model.mergeRideTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

public class ConfigurationBackups internal constructor(private val database: Database, private val context: Context) {
    private fun preferences(name: String): SharedPreferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    public suspend fun create(): ConfigurationBackup = withContext(Dispatchers.IO) { snapshot() }

    @Suppress("MagicNumber") // Must match the persisted preferences defaults.
    private fun snapshot(): ConfigurationBackup {
        val vehicles = database.transactionWithResult {
            database.vehicleQueries.selectAll().executeAsList().map { vehicle ->
                BackupVehicle(vehicle.uuid.toString(), vehicle.kind.name, vehicle.name, vehicle.isFavourite,
                    vehicle.lowPressure.kpa, vehicle.highPressure.kpa, vehicle.separateFrontRearPressure,
                    vehicle.rearLowPressure.kpa, vehicle.rearHighPressure.kpa, vehicle.lowTemp.celsius,
                    vehicle.normalTemp.celsius, vehicle.highTemp.celsius,
                    database.sensorQueries.selectListByVehicleId(vehicle.uuid).executeAsList().map {
                        BackupSensor(it.id, backupLocation(it.location))
                    })
            }
        }
        val units = preferences("UNITS")
        val alerts = preferences("ALERTS")
        val triggers = preferences("AUTOMATIC_RIDES")
        val calibration = CalibrationRepository(context).values.value.mapValues { (_, value) ->
            BackupCalibration(value.pressureOffset, value.pressureMultiplier, value.temperatureOffset,
                value.temperatureMultiplier)
        }
        val country = java.util.Locale.getDefault().isO3Country
        val pressureDefault = if (country in listOf("USA", "GBR", "CAN", "AUS")) "PSI" else "BAR"
        val temperatureDefault = if (country == "USA") "FAHRENHEIT" else "CELSIUS"
        return ConfigurationBackup(vehicles = vehicles, settings = BackupSettings(
            units.getString("PRESSURE", pressureDefault)!!, units.getString("TEMPERATURE", temperatureDefault)!!,
            preferences("APP").getBoolean("DETECT_LEAKS", false), alerts.getString("sound", "NONE")!!,
            alerts.getBoolean("vibrate", false), alerts.getInt("repeat", 120), alerts.getInt("critical", 20),
            preferences("WAITING_POWER").getBoolean("saving", true), calibration,
            vehicles.mapNotNull { vehicle ->
                triggers.getString("${vehicle.uuid}.address", null)?.let { address ->
                    vehicle.uuid to BackupRideTrigger(address, triggers.getBoolean("${vehicle.uuid}.beacon", false),
                        triggers.getInt("${vehicle.uuid}.rssi", -80))
                }
            }.toMap(),
            preferences("DISCOVERED_SENSORS").all.mapNotNull { (key, value) ->
                key.toIntOrNull()?.let { id -> (value as? String)?.let { id to it } }
            }.toMap(),
            RideToolsStore(context).read().let { data -> data.copy(
                monitoring = data.monitoring.copy(parkedRoutes = data.monitoring.parkedRoutes.filterKeys {
                    id -> vehicles.any { it.uuid == id }
                }), insights = data.insights.forVehicles(
                vehicles.associate { it.uuid to Vehicle.Kind.valueOf(it.kind) }),
                vehicles = data.vehicles.filterKeys { id ->
                vehicles.any { it.uuid == id }
            }) }
        )).also { it.validate() }
    }

    /** Merges vehicles by UUID, replaces their bindings and preserves history and unrelated vehicles. */
    public suspend fun restore(backup: ConfigurationBackup): Unit = withContext(Dispatchers.IO) {
        backup.validate()
        // Reject conflicts with retained vehicles before committing the database/restore journal.
        mergeRideTools(RideToolsStore(context).read(), backup.settings.rideTools, backup.vehicles.map { it.uuid })
        val previous = snapshot()
        val recoveryFile = File(context.filesDir, "configuration-before-restore.json")
        val safetyCopy = AtomicFile(recoveryFile)
        val stream = safetyCopy.startWrite()
        try {
            stream.write(previous.encode().toByteArray(Charsets.UTF_8))
            safetyCopy.finishWrite(stream)
        } catch (failure: java.io.IOException) {
            safetyCopy.failWrite(stream)
            throw failure
        }
        database.transaction {
                backup.vehicles.forEach { vehicle ->
                    database.vehicleQueries.restoreConfiguration(UUID.fromString(vehicle.uuid),
                        Vehicle.Kind.valueOf(vehicle.kind),
                        vehicle.name, vehicle.current, Pressure(vehicle.lowPressure), Pressure(vehicle.highPressure),
                        vehicle.separateRear, Pressure(vehicle.rearLow), Pressure(vehicle.rearHigh),
                        Temperature(vehicle.lowTemp), Temperature(vehicle.normalTemp), Temperature(vehicle.highTemp))
                }
                backup.vehicles.forEach { database.sensorQueries.deleteByVehicle(UUID.fromString(it.uuid)) }
                backup.vehicles.forEach { vehicle -> vehicle.sensors.forEach {
                    database.sensorQueries.upsert(it.id, backupLocation(it.location), UUID.fromString(vehicle.uuid))
                } }
                // The settings journal commits atomically with bindings. A process kill cannot lose the next step.
                database.configurationRestoreQueries.stage(backup.encode())
                val current = backup.vehicles.single { it.current }
                database.vehicleQueries.setAsFavourite(true, UUID.fromString(current.uuid))
        }
        finishPending(database, context)
    }

    public suspend fun previousBackup(): String = withContext(Dispatchers.IO) {
        File(context.filesDir, "configuration-before-restore.json").readText()
    }

    private fun edit(name: String, block: SharedPreferences.Editor.() -> Unit) {
        check(preferences(name).edit().apply(block).commit()) { "Could not save $name settings" }
    }

    private fun applySettings(settings: BackupSettings, vehicles: List<String>, sensorIds: List<Int>) {
        RideToolsStore(context).update { previous ->
            mergeRideTools(previous, settings.rideTools, vehicles)
        }
        edit("UNITS") { putString("PRESSURE", settings.pressureUnit); putString("TEMPERATURE",
            settings.temperatureUnit) }
        edit("APP") { putBoolean("DETECT_LEAKS", settings.detectLeaks) }
        edit("ALERTS") {
            putString("sound", settings.alertSound); putBoolean("vibrate", settings.vibrate)
            putInt("repeat", settings.repeatSeconds); putInt("critical", settings.criticalSeconds)
        }
        edit("WAITING_POWER") { putBoolean("saving", settings.saveWaitingPower) }
        edit("DISCOVERED_SENSORS") {
            sensorIds.forEach { remove(it.toString()) }
            settings.discoveredAddresses.forEach { (id, address) -> putString(id.toString(), address) }
        }
        edit("SENSOR_CALIBRATION") {
            sensorIds.forEach { id -> remove("$id.po"); remove("$id.pm"); remove("$id.to"); remove("$id.tm") }
            settings.calibrations.forEach { (id, value) ->
            putFloat("$id.po", value.offset); putFloat("$id.pm", value.multiplier)
            putFloat("$id.to", value.temperatureOffset); putFloat("$id.tm", value.temperatureMultiplier)
        } }
        edit("AUTOMATIC_RIDES") {
            remove("armed")
            vehicles.forEach { id -> remove("$id.address"); remove("$id.beacon"); remove("$id.rssi") }
            settings.triggers.forEach { (id, value) ->
                putString("$id.address", value.address); putBoolean("$id.beacon", value.beacon); putInt("$id.rssi",
                    value.rssi)
            }
        }
        edit("MONITORING_SESSION") { clear() }
    }

    public companion object {
        /** Called before exposing the database on startup; idempotently finishes a committed restore. */
        public fun finishPending(database: Database, context: Context) {
            database.configurationRestoreQueries.pending().executeAsOneOrNull()?.let { payload ->
                val backup = ConfigurationBackup.decode(payload)
                ConfigurationBackups(database, context).applySettings(backup.settings, backup.vehicles.map { it.uuid },
                    backup.vehicles.flatMap { it.sensors }.map { it.id })
                database.configurationRestoreQueries.complete()
            }
        }
    }
}
