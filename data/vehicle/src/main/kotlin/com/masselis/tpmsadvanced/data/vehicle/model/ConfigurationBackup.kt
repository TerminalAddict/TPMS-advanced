@file:Suppress("MagicNumber") // Validation bounds and versioned format defaults.

package com.masselis.tpmsadvanced.data.vehicle.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
public data class ConfigurationBackup(
    val format: String = "TyrePing configuration",
    val version: Int = 1,
    val vehicles: List<BackupVehicle>,
    val settings: BackupSettings,
) {
    public fun validate() {
        require(format == "TyrePing configuration" && version == 1) { "Unsupported backup format or version" }
        require(vehicles.size in 1..100 && vehicles.map { it.uuid }.distinct().size == vehicles.size) {
            "Invalid vehicle list"
        }
        require(vehicles.count { it.current } == 1) { "Backup must have one current vehicle" }
        vehicles.forEach { it.validate() }
        val sensors = vehicles.flatMap { it.sensors }.map { it.id }
        require(sensors.distinct().size == sensors.size) { "A sensor is assigned to multiple wheels" }
        settings.validate(vehicles.map { it.uuid }.toSet())
        val kinds = vehicles.associate { it.uuid to Vehicle.Kind.valueOf(it.kind) }
        require(settings.rideTools.insights.combinations.all { it.isValidFor(kinds) })
        require(settings.rideTools.insights.imbalanceKpa.keys.all { it in kinds })
        require(settings.rideTools.insights.cold.all { (id, baseline) ->
            kinds[id]?.let { kind -> baseline.samples.keys.all { backupLocation(it) in kind.locations } } == true
        })
        vehicles.forEach { vehicle -> settings.rideTools.vehicles[vehicle.uuid]?.let { tools ->
            require(tools.records.all { backupLocation(it.wheel) in Vehicle.Kind.valueOf(vehicle.kind).locations })
            require(vehicle.kind != Vehicle.Kind.SINGLE_AXLE_TRAILER.name || tools.profiles.none { it.separateRear })
        } }
    }

    public fun encode(): String {
        validate()
        return codec.encodeToString(this).also {
            require(it.length <= MAX_CHARACTERS) { "Backup exceeds the 2 MB limit" }
        }
    }

    public companion object {
        public const val MAX_CHARACTERS: Int = 2_000_000
        private val codec = Json { prettyPrint = true; encodeDefaults = true }
        public fun decode(text: String): ConfigurationBackup {
            require(text.length <= MAX_CHARACTERS) { "Backup exceeds the 2 MB limit" }
            return codec.decodeFromString<ConfigurationBackup>(text).also { it.validate() }
        }
    }
}

@Serializable
public data class BackupVehicle(
    val uuid: String, val kind: String, val name: String, val current: Boolean,
    val lowPressure: Float, val highPressure: Float, val separateRear: Boolean,
    val rearLow: Float, val rearHigh: Float,
    val lowTemp: Float, val normalTemp: Float, val highTemp: Float,
    val sensors: List<BackupSensor>,
) {
    public fun validate() {
        require(UUID.fromString(uuid).toString() == uuid) { "Vehicle ID must be a canonical UUID" }
        val type = Vehicle.Kind.valueOf(kind)
        require(name.isNotBlank() && name.length <= 120) { "Invalid vehicle name" }
        require(lowPressure in 0f..2_000f && highPressure in 0f..2_000f && lowPressure < highPressure)
        require(rearLow in 0f..2_000f && rearHigh in 0f..2_000f && rearLow < rearHigh)
        require(lowTemp in -100f..200f && normalTemp in lowTemp..200f && highTemp in normalTemp..200f)
        require(!separateRear || type != Vehicle.Kind.SINGLE_AXLE_TRAILER)
        require(sensors.size <= type.locations.size && sensors.map { it.location }.distinct().size == sensors.size)
        sensors.forEach {
            require(backupLocation(it.location) in type.locations) { "Wheel does not belong to vehicle" }
        }
    }
}

@Serializable
public data class BackupSensor(val id: Int, val location: String)

@Serializable
public data class BackupCalibration(val offset: Float, val multiplier: Float, val temperatureOffset: Float,
    val temperatureMultiplier: Float) {
    public fun calibration(): SensorCalibration = SensorCalibration(offset, multiplier, temperatureOffset,
        temperatureMultiplier)
}

@Serializable
public data class BackupRideTrigger(val address: String, val beacon: Boolean, val rssi: Int)

@Serializable
public data class BackupSettings(
    val pressureUnit: String = "BAR", val temperatureUnit: String = "CELSIUS", val detectLeaks: Boolean = false,
    val alertSound: String = "NONE", val vibrate: Boolean = false, val repeatSeconds: Int = 120,
    val criticalSeconds: Int = 20, val saveWaitingPower: Boolean = true,
    val calibrations: Map<Int, BackupCalibration> = emptyMap(),
    val triggers: Map<String, BackupRideTrigger> = emptyMap(),
    val discoveredAddresses: Map<Int, String> = emptyMap(),
    val rideTools: RideToolsData = RideToolsData(),
) {
    public fun validate(vehicles: Set<String>) {
        rideTools.validate()
        require(rideTools.vehicles.keys.all { it in vehicles })
        com.masselis.tpmsadvanced.data.unit.model.PressureUnit.valueOf(pressureUnit)
        com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit.valueOf(temperatureUnit)
        require(alertSound in listOf("NONE", "TONES", "SPEECH"))
        require(repeatSeconds in 30..600 && criticalSeconds in 10..120)
        require(calibrations.size <= 1_000 && calibrations.values.all { it.calibration().isValid() })
        require(triggers.keys.all { it in vehicles })
        require(discoveredAddresses.size <= 1_000 && discoveredAddresses.values.all {
            it.matches(Regex("([0-9A-F]{2}:){5}[0-9A-F]{2}"))
        }) { "Invalid discovered sensor addresses" }
        triggers.values.forEach {
            require(it.address.matches(Regex("([0-9A-F]{2}:){5}[0-9A-F]{2}")) && it.rssi in -100..-30)
        }
    }
}

public fun backupLocation(location: Location): String = when (location) {
    is Location.Wheel -> "wheel:${location.name}"
    is Location.Axle -> "axle:${location.name}"
    is Location.Side -> "side:${location.name}"
    is Location.Auxiliary -> "aux:${location.name}"
}

public fun backupLocation(text: String): Location = when (text.substringBefore(':')) {
    "wheel" -> Location.Wheel.valueOf(text.substringAfter(':'))
    "axle" -> Location.Axle.valueOf(text.substringAfter(':'))
    "side" -> Location.Side.valueOf(text.substringAfter(':'))
    "aux" -> Location.Auxiliary.valueOf(text.substringAfter(':'))
    else -> throw IllegalArgumentException("Unknown wheel location")
}
