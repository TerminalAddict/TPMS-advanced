package com.masselis.tpmsadvanced.data.vehicle.interfaces

import com.masselis.tpmsadvanced.core.database.QueryList
import com.masselis.tpmsadvanced.core.database.QueryList.Companion.asList
import com.masselis.tpmsadvanced.core.database.QueryOne
import com.masselis.tpmsadvanced.core.database.QueryOne.Companion.asOne
import com.masselis.tpmsadvanced.core.database.QueryOneOrNull
import com.masselis.tpmsadvanced.core.database.QueryOneOrNull.Companion.asOneOrNull
import com.masselis.tpmsadvanced.data.vehicle.Database
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.moveSensors
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import java.util.UUID

@Suppress("TooManyFunctions") // Typed operations for this database table.
public class SensorDatabase internal constructor(
    private val database: Database,
) {
    private val vehicleQueries = database.vehicleQueries
    private val queries = database.sensorQueries

    /**
     * This method insert a sensor for a vehicle. If the sensor is already saved for an other
     * vehicle, this method moves the sensor to the new vehicle. If a sensor already exists at the
     * same location, it replaces it. This method also check the vehicle kind when before inserting
     * the sensor.
     */
    public suspend fun upsert(
        sensor: Sensor,
        vehicleId: UUID,
    ): Unit = withContext(IO) { database.transaction { upsertSync(sensor, vehicleId) } }

    public suspend fun upsert(sensors: List<Sensor>, vehicleId: UUID): Unit = withContext(IO) {
        require(sensors.distinctBy { it.id }.size == sensors.size) { "Duplicate sensor IDs" }
        require(sensors.distinctBy { it.location }.size == sensors.size) { "Conflicting sensor locations" }
        database.transaction { sensors.forEach { upsertSync(it, vehicleId) } }
    }

    private fun upsertSync(
        sensor: Sensor,
        vehicleId: UUID,
    ) {
        val kind = vehicleQueries.selectByUuid(vehicleId).executeAsOne().kind
        require(kind.locations.any { it == sensor.location }) {
            @Suppress("MaxLineLength")
            "Filled sensor points to a location which is not handled by the vehicle kind. Kind: $kind, sensor: $sensor"
        }
        queries.deleteByVehicleAndLocation(vehicleId, sensor.location)
        queries.upsert(sensor.id, sensor.location, vehicleId)
    }

    public suspend fun deleteFromVehicle(vehicleId: UUID): Unit = withContext(IO) {
        queries.deleteByVehicle(vehicleId)
    }

    public suspend fun unbind(vehicleId: UUID, location: Location): Unit = withContext(IO) {
        queries.deleteByVehicleAndLocation(vehicleId, location)
    }

    public suspend fun unbindIfCurrent(vehicleId: UUID, expected: Sensor): Unit = withContext(IO) {
        database.transaction {
            require(queries.selectByVehicleAndLocation(vehicleId, expected.location, mapper).executeAsOneOrNull()
                == expected) { "Bindings changed. Review the assignment again." }
            queries.deleteByVehicleAndLocation(vehicleId, expected.location)
        }
    }

    /** Rejects a stale confirmation instead of replacing an assignment the user has not reviewed. */
    public suspend fun assign(
        sensor: Sensor,
        vehicleId: UUID,
        expectedTarget: Sensor?,
        expectedSource: Pair<UUID, Sensor>?,
    ): Unit = withContext(IO) {
        database.transaction {
            val target = queries.selectByVehicleAndLocation(vehicleId, sensor.location, mapper).executeAsOneOrNull()
            val source = queries.selectById(sensor.id).executeAsOneOrNull()?.let {
                it.vehicleId to Sensor(it.id, it.location)
            }
            require(target == expectedTarget && source == expectedSource) {
                "Bindings changed. Review the assignment again."
            }
            upsertSync(sensor, vehicleId)
        }
    }

    public suspend fun move(vehicleId: UUID, expected: List<Sensor>, moves: Map<Location, Location>): Unit =
        withContext(IO) {
            database.transaction {
                val current = queries.selectListByVehicleId(vehicleId, mapper).executeAsList()
                require(current.toSet() == expected.toSet()) { "Bindings changed. Review the move again." }
                val kind = vehicleQueries.selectByUuid(vehicleId).executeAsOne().kind
                val updated = moveSensors(current, kind.locations, moves)
                queries.deleteByVehicle(vehicleId)
                updated.forEach { upsertSync(it, vehicleId) }
            }
        }

    public suspend fun deleteFromVehicleAndUpsert(
        sensors: List<Sensor>,
        vehicleId: UUID
    ): Unit = withContext(IO) {
        database.transaction {
            queries.deleteByVehicle(vehicleId)
            sensors.forEach { upsertSync(it, vehicleId) }
        }
    }

    public fun selectByVehicleAndLocation(
        vehicleId: UUID,
        location: Location,
    ): QueryOneOrNull<Sensor> = queries
        .selectByVehicleAndLocation(vehicleId, location, mapper)
        .asOneOrNull()

    public fun countByVehicle(vehicleId: UUID): QueryOne<Long> = queries
        .countByVehicle(vehicleId)
        .asOne()

    public fun selectById(id: Int): QueryOneOrNull<Sensor> = queries
        .selectById(id, mapper)
        .asOneOrNull()

    public fun selectListByVehicleId(uuid: UUID): QueryList<Sensor> = queries
        .selectListByVehicleId(uuid, mapper)
        .asList()

    public fun selectListExcludingVehicleId(uuid: UUID): QueryList<Sensor> = queries
        .selectListExcludingVehicleId(uuid, mapper)
        .asList()

    private companion object {
        private val mapper: (
            id: Int,
            location: Location,
            vehicleId: UUID,
        ) -> Sensor = { id, location, _ ->
            Sensor(id, location)
        }
    }
}
