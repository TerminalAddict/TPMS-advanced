@file:Suppress("MagicNumber") // Versioned persistence defaults and validation bounds, in stated units.

package com.masselis.tpmsadvanced.data.vehicle.model

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

/** User presets contain limits, never guessed manufacturer-recommended pressures. */
@Serializable
public data class PressureProfile(
    val id: String = UUID.randomUUID().toString(), val name: String,
    val low: Float, val high: Float, val separateRear: Boolean = false,
    val rearLow: Float = low, val rearHigh: Float = high,
) {
    public fun validate() {
        require(UUID.fromString(id).toString() == id)
        require(name.isNotBlank() && name.length <= 60)
        require(low in 0f..2000f && high in 0f..2000f && low < high)
        require(rearLow in 0f..2000f && rearHigh in 0f..2000f && rearLow < rearHigh)
    }
    public fun matches(vehicle: Vehicle): Boolean = low == vehicle.lowPressure.kpa &&
        high == vehicle.highPressure.kpa && separateRear == vehicle.separateFrontRearPressure &&
        (!separateRear || rearLow == vehicle.rearLowPressure.kpa && rearHigh == vehicle.rearHighPressure.kpa)
}

@Serializable
public data class MaintenanceRecord(
    val id: String = UUID.randomUUID().toString(), val wheel: String, val date: String,
    val kind: String, val brandModel: String = "", val odometerKm: Float? = null,
    val treadMm: Float? = null, val pressureKpa: Float? = null, val notes: String = "",
) {
    public fun validate() {
        require(UUID.fromString(id).toString() == id)
        backupLocation(wheel)
        LocalDate.parse(date)
        require(kind in KINDS && brandModel.length <= 120 && notes.length <= 1000)
        require(odometerKm == null || odometerKm in 0f..10_000_000f)
        require(treadMm == null || treadMm in 0f..100f)
        require(pressureKpa == null || pressureKpa in 0f..2000f)
    }
    public companion object {
        public val KINDS: List<String> = listOf("Installation", "Pressure check", "Tread check", "Replacement", "Other")
    }
}

@Serializable
public data class MaintenanceReminder(
    val label: String = "Check tyres", val dueDate: String? = null, val dueKm: Float? = null,
    val currentKm: Float = 0f, val enabled: Boolean = false,
) {
    public fun validate() {
        require(label.isNotBlank() && label.length <= 120)
        dueDate?.let { LocalDate.parse(it) }
        require(dueKm == null || dueKm in 0f..10_000_000f)
        require(currentKm in 0f..10_000_000f)
        require(!enabled || dueDate != null || dueKm != null)
    }
    public fun isDue(today: LocalDate): Boolean = enabled &&
        (dueDate?.let { !today.isBefore(LocalDate.parse(it)) } == true || dueKm?.let { currentKm >= it } == true)
}

@Serializable
public data class VehicleRideTools(
    val profiles: List<PressureProfile> = emptyList(),
    val records: List<MaintenanceRecord> = emptyList(),
    val reminder: MaintenanceReminder = MaintenanceReminder(),
    val accessoryAddress: String = "",
) {
    public fun validate() {
        require(profiles.size <= 30 && profiles.distinctBy { it.id }.size == profiles.size)
        require(profiles.distinctBy { it.name.lowercase() }.size == profiles.size)
        require(records.size <= 1000 && records.distinctBy { it.id }.size == records.size)
        profiles.forEach { it.validate() }; records.forEach { it.validate() }; reminder.validate()
        require(accessoryAddress.isEmpty() || accessoryAddress.matches(Regex("([0-9A-F]{2}:){5}[0-9A-F]{2}")))
    }
}

@Serializable
public data class RideToolsData(
    val vehicles: Map<String, VehicleRideTools> = emptyMap(),
    val missingEnabled: Boolean = true, val missingMinutes: Int = 10,
    val automaticSelection: Boolean = false, val spokenDiagnostics: Boolean = false,
) {
    public fun validate() {
        require(vehicles.size <= 100 && missingMinutes in 1..60)
        require(vehicles.values.sumOf { it.records.size } <= 1000)
        vehicles.forEach { (id, value) -> require(UUID.fromString(id).toString() == id); value.validate() }
        val addresses = vehicles.values.map { it.accessoryAddress }.filter { it.isNotEmpty() }
        require(addresses.distinct().size == addresses.size) { "An accessory can select only one vehicle" }
    }
}

/** Never guess between two connected vehicles, and never fight a manual/NFC choice. */
public fun automaticVehicleCandidate(connected: List<String>, manualOverride: Boolean): String? =
    connected.distinct().singleOrNull().takeUnless { manualOverride }

public fun mergeRideTools(previous: RideToolsData, imported: RideToolsData, replaced: List<String>): RideToolsData =
    imported.copy(vehicles = previous.vehicles.filterKeys { it !in replaced } + imported.vehicles)
        .also(RideToolsData::validate)

/** Parse only the supported vehicle link; arbitrary or malformed external intents cannot select a vehicle. */
public fun vehicleLinkId(text: String?): UUID? = runCatching {
    val uri = java.net.URI(text ?: return null)
    require(uri.scheme == "tpmsadvanced" && uri.rawAuthority == "vehicle" && uri.query == null && uri.fragment == null)
    val id = uri.path.removePrefix("/")
    UUID.fromString(id).also { require(it.toString() == id) }
}.getOrNull()
