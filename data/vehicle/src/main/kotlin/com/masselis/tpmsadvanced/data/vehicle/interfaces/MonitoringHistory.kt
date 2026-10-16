@file:Suppress("MagicNumber", "TooManyFunctions") // SQL paging and explicit retention units.

package com.masselis.tpmsadvanced.data.vehicle.interfaces

import android.content.Context
import com.masselis.tpmsadvanced.data.vehicle.AlertEvent
import com.masselis.tpmsadvanced.data.vehicle.Database
import com.masselis.tpmsadvanced.data.vehicle.RideReport
import com.masselis.tpmsadvanced.data.vehicle.model.AlertObservation
import com.masselis.tpmsadvanced.data.vehicle.model.MonitoringTools
import com.masselis.tpmsadvanced.data.vehicle.model.RideSummaryBuilder
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.backupLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.Writer
import java.util.UUID

public data class StorageCounts(val readings: Long, val events: Long, val reports: Long)
public data class RetentionPlan(val readingsBefore: Double, val eventsBefore: Double, val reportsBefore: Double,
    val counts: StorageCounts)

/** Append-only identities; binding changes never rewrite old readings, event labels or ride summaries. */
public class MonitoringHistory(private val database: Database, private val context: Context) {
    private val queries = database.monitorHistoryQueries
    private val tyres = TyreDatabase(database)
    private val mutex = Mutex()
    private var recovered = false
    private val completed = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    public val completedReport: kotlinx.coroutines.flow.StateFlow<String?> = completed

    private fun recover() {
        if (recovered) return
        database.transaction {
            queries.openEvents().executeAsList().forEach { queries.endEvent(it.lastSeen,
                "Monitoring interrupted", it.id) }
            queries.openRides().executeAsList().forEach { finish(it, it.lastSeen, "Monitoring interrupted") }
        }
        recovered = true
    }

    public suspend fun recordEvents(observations: List<AlertObservation>, time: Double,
        unavailable: Set<String>, maintenance: Set<String>): Unit = withContext(Dispatchers.IO) { mutex.withLock {
        recover()
        database.transaction {
            val existing = queries.openEvents().executeAsList().associateBy { it.eventKey }
            val keys = observations.map { it.key }.toSet()
            existing.values.filter { it.eventKey !in keys }.forEach { event ->
                val reason = when {
                    event.vehicleId in maintenance -> "Maintenance mode"
                    "${event.vehicleId}:${event.wheel}" in unavailable -> "Reading no longer current"
                    else -> "Condition cleared"
                }
                queries.endEvent(time, reason, event.id)
            }
            observations.distinctBy { it.key }.forEach { event ->
                val old = existing[event.key]
                if (old != null) queries.touchEvent(time, old.id)
                else queries.insertEvent(UUID.randomUUID().toString(), event.key, event.vehicle, event.vehicleName,
                    event.wheel, event.sensor?.toUInt()?.toLong(), event.kind,
                    if (event.critical) "Critical" else "Warning",
                    time, time)
            }
        }
    } }

    public suspend fun beginRides(vehicles: List<Vehicle>, time: Double, gapSeconds: Double): List<String> =
        withContext(Dispatchers.IO) { mutex.withLock {
            recover()
            database.transactionWithResult {
                queries.openRides().executeAsList().forEach { finish(it, time, "Monitoring restarted") }
                vehicles.map { vehicle ->
                    val id = UUID.randomUUID().toString()
                    queries.insertRide(id, vehicle.uuid.toString(), vehicle.name, time, time,
                        Json.encodeToString(vehicle.kind.locations.map(::backupLocation)), gapSeconds)
                    id
                }
            }
        } }
    public suspend fun heartbeat(time: Double, ids: List<String>): Unit = withContext(Dispatchers.IO) { mutex.withLock {
        recover(); ids.forEach { queries.touchRides(time, it) }
    } }
    public suspend fun endRides(time: Double, reason: String, ids: List<String>): Unit =
        withContext(Dispatchers.IO) { mutex.withLock {
        recover()
        database.transaction {
            val ending = queries.openRides().executeAsList().filter { it.id in ids }
            ending.forEach { finish(it, time, reason) }
            if (ending.isNotEmpty() && queries.openRides().executeAsList().isEmpty())
                queries.openEvents().executeAsList().forEach { queries.endEvent(time, "Monitoring stopped", it.id) }
        }
    } }
    private fun finish(ride: RideReport, end: Double, reason: String) {
        val until = maxOf(ride.started, end)
        val builder = RideSummaryBuilder(Json.decodeFromString<List<String>>(ride.expected).toSet(), ride.gapSeconds)
        var offset = 0L
        do {
            val rows = tyres.historyPage(UUID.fromString(ride.vehicleId), ride.started, until, PAGE, offset)
            rows.forEach(builder::add)
            offset += rows.size
        } while (rows.size == PAGE.toInt())
        val summary = builder.finish(queries.countRideAlerts(ride.vehicleId, ride.started, until).executeAsOne(),
            ride.started, until)
        queries.endRide(until, reason, Json.encodeToString(summary), ride.id)
        completed.value = ride.id
    }
    public suspend fun events(search: String, page: Int): List<AlertEvent> = withContext(Dispatchers.IO) {
        mutex.withLock { recover(); queries.searchEvents("%${search.take(100)}%", DISPLAY_PAGE,
            page.coerceAtLeast(0) * DISPLAY_PAGE).executeAsList() }
    }
    public suspend fun dismiss(id: String, time: Double): Unit = withContext(Dispatchers.IO) {
        mutex.withLock { recover(); queries.dismissEvent(time, id) }
    }
    public suspend fun rides(page: Int): List<RideReport> = withContext(Dispatchers.IO) { mutex.withLock {
        recover(); queries.ridesPage(DISPLAY_PAGE, page.coerceAtLeast(0) * DISPLAY_PAGE).executeAsList()
    } }
    public suspend fun counts(): StorageCounts = withContext(Dispatchers.IO) { mutex.withLock {
        recover(); StorageCounts(database.tyreQueries.countReadings().executeAsOne(),
            queries.countEvents().executeAsOne(), queries.countRides().executeAsOne())
    } }
    public fun bytes(): Long = listOf("car.db", "car.db-wal", "car.db-shm").sumOf {
        context.getDatabasePath(it).length() }

    public suspend fun plan(settings: MonitoringTools, time: Double): RetentionPlan = withContext(Dispatchers.IO) {
        mutex.withLock {
            recover(); settings.validate()
            fun cutoff(days: Int) = if (days == 0) 0.0 else (time - days * 86400.0).coerceAtLeast(0.0)
            val earliestRide = queries.openRides().executeAsList().minOfOrNull { it.started } ?: time
            val readingCutoff = minOf(cutoff(settings.readingDays), earliestRide)
            val eventsCutoff = minOf(cutoff(settings.eventDays), earliestRide)
            val reportsCutoff = cutoff(settings.reportDays)
            RetentionPlan(readingCutoff, eventsCutoff, reportsCutoff, oldCounts(readingCutoff, eventsCutoff,
                reportsCutoff))
        }
    }
    private fun oldCounts(readings: Double, events: Double, reports: Double) = StorageCounts(
        database.tyreQueries.countOldReadings(readings).executeAsOne(), queries.countOldEvents(events).executeAsOne(),
        queries.countOldRides(reports).executeAsOne())

    public suspend fun cleanup(plan: RetentionPlan): Unit = withContext(Dispatchers.IO) { mutex.withLock {
        recover()
        database.transaction {
            check(queries.openRides().executeAsList().isEmpty()) { "Stop monitoring before cleaning up history." }
            require(oldCounts(plan.readingsBefore, plan.eventsBefore, plan.reportsBefore) == plan.counts) {
                "Stored data changed. Review a fresh cleanup preview."
            }
            database.tyreQueries.pruneReadings(plan.readingsBefore)
            queries.pruneEvents(plan.eventsBefore)
            queries.pruneRides(plan.reportsBefore)
        }
    } }

    /** Export never deletes. A provider error or cancelled picker leaves all original records intact. */
    public suspend fun archive(plan: RetentionPlan, writer: Writer): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
        recover()
        database.transaction {
            check(queries.openRides().executeAsList().isEmpty()) { "Stop monitoring before archiving history." }
            fun row(values: Map<String, String>) { writer.write(Json.encodeToString(values) + "\n") }
            row(mapOf("type" to "TyrePing archive", "version" to "1", "units" to "raw kPa and Celsius"))
            var offset = 0L
            do {
                val rows = database.tyreQueries.oldReadings(plan.readingsBefore, PAGE, offset).executeAsList()
                rows.forEach { row(mapOf("type" to "reading", "vehicle" to it.vehicleId.toString(),
                    "wheel" to backupLocation(it.location), "sensor" to it.id.toUInt().toString(),
                    "timestamp" to it.timestamp.toString(), "pressureKpa" to it.pressure.kpa.toString(),
                    "temperatureC" to it.temperature.celsius.toString(), "rssi" to it.rssi.toString(),
                    "batteryRaw" to it.battery.toString(), "batteryVoltage" to (it.batteryVoltage?.toString() ?: ""),
                    "batteryPercent" to (it.batteryPercent?.toString() ?: ""), "alarm" to it.isAlarm.toString())) }
                offset += rows.size
            } while (rows.size == PAGE.toInt())
            offset = 0L
            do {
                val rows = queries.oldEvents(plan.eventsBefore, PAGE, offset).executeAsList()
                rows.forEach { row(mapOf("type" to "alert", "id" to it.id, "key" to it.eventKey,
                    "vehicle" to it.vehicleId, "vehicleName" to it.vehicleName, "wheel" to it.wheel,
                    "sensor" to (it.sensorId?.toInt()?.toUInt()?.toString() ?: ""), "kind" to it.kind,
                    "severity" to it.severity, "started" to it.started.toString(), "lastSeen" to it.lastSeen.toString(),
                    "ended" to it.ended.toString(), "dismissed" to (it.dismissed?.toString() ?: ""),
                    "resolution" to (it.resolution ?: ""))) }
                offset += rows.size
            } while (rows.size == PAGE.toInt())
            offset = 0L
            do {
                val rows = queries.oldRides(plan.reportsBefore, PAGE, offset).executeAsList()
                rows.forEach { row(mapOf("type" to "ride", "id" to it.id, "vehicle" to it.vehicleId,
                    "vehicleName" to it.vehicleName, "started" to it.started.toString(),
                    "ended" to it.ended.toString(), "reason" to (it.reason ?: ""),
                    "expected" to it.expected, "gapSeconds" to it.gapSeconds.toString(),
                        "summary" to it.summary)) }
                offset += rows.size
            } while (rows.size == PAGE.toInt())
        }
    } }
    private companion object { const val PAGE = 1000L; const val DISPLAY_PAGE = 30L }
}
