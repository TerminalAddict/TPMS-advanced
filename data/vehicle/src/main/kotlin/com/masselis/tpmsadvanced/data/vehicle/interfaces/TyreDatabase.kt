package com.masselis.tpmsadvanced.data.vehicle.interfaces

import com.masselis.tpmsadvanced.core.database.QueryOneOrNull
import com.masselis.tpmsadvanced.core.database.QueryOneOrNull.Companion.asOneOrNull
import com.masselis.tpmsadvanced.data.vehicle.Database
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBattery
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import java.util.UUID

public class TyreDatabase internal constructor(
    private val database: Database
) {
    private val queries = database.tyreQueries

    public suspend fun history(vehicleId: UUID, from: Double, until: Double): List<Tyre.Located> = withContext(IO) {
        historyPage(vehicleId, from, until, HISTORY_LIMIT + 1L, 0)
    }

    internal fun historyPage(vehicleId: UUID, from: Double, until: Double, limit: Long,
        offset: Long): List<Tyre.Located> =
        queries.historyPage(vehicleId, from, until, limit, offset) {
                id, timestamp, rssi, location, pressure, temperature, battery, isAlarm, voltage, percent ->
            Tyre.Located(timestamp, rssi, id, pressure, temperature, battery, isAlarm, location,
                SensorBattery.fromStored(voltage, percent))
        }.executeAsList()

    public suspend fun exportHistory(
        vehicle: com.masselis.tpmsadvanced.data.vehicle.model.Vehicle,
        from: Double, until: Double, writer: java.io.Writer,
    ): Unit = withContext(IO) {
        writer.write("timestamp_utc,vehicle,wheel,sensor_id,pressure_kpa_raw,temperature_c_raw," +
            "battery_volts,battery_percent,rssi,alarm\n")
        database.transaction {
            var offset = 0L
            do {
                val page = historyPage(vehicle.uuid, from, until, CSV_PAGE_SIZE, offset)
                page.forEach { reading ->
                    val date = java.time.Instant
                        .ofEpochMilli((reading.timestamp * MILLIS_PER_SECOND).toLong()).toString()
                    writer.write(listOf(date, com.masselis.tpmsadvanced.data.vehicle.model.csvCell(vehicle.name),
                        reading.location.toString(), reading.sensorId.toUInt(), reading.pressure.kpa,
                        reading.temperature.celsius, (reading.sensorBattery as? SensorBattery.Voltage)?.volts ?: "",
                        (reading.sensorBattery as? SensorBattery.Percentage)?.percent ?: "", reading.rssi,
                        reading.isAlarm).joinToString(",") + "\n")
                }
                offset += page.size
            } while (page.size == CSV_PAGE_SIZE.toInt())
        }
    }

    public companion object {
        public const val HISTORY_LIMIT: Int = 20_000
        private const val CSV_PAGE_SIZE = 1_000L
        private const val MILLIS_PER_SECOND = 1_000L
    }

    public suspend fun insert(tyre: Tyre.Located, vehicleId: UUID): Unit = withContext(IO) {
        database.transaction { insertSync(tyre, vehicleId) }
    }

    /** Resolve ownership and store the packet in the same transaction as a concurrent rebind. */
    public suspend fun insertBound(tyre: Tyre): Unit = withContext(IO) {
        database.transaction {
            database.sensorQueries.selectById(tyre.sensorId).executeAsOneOrNull()?.let { binding ->
                insertSync(Tyre.Located(tyre, binding.location), binding.vehicleId)
            }
        }
    }

    private fun insertSync(tyre: Tyre.Located, vehicleId: UUID) {
        // Foreground screens and fleet monitoring share packets; store each broadcast once per vehicle.
        val last = queries.latestBySensorByVehicle(tyre.sensorId, vehicleId).executeAsOneOrNull()
        if (last?.timestamp == tyre.timestamp) return
        queries.insert(
            tyre.sensorId,
            tyre.timestamp,
            tyre.rssi,
            tyre.location,
            tyre.pressure,
            tyre.temperature,
            tyre.battery,
            tyre.isAlarm,
            vehicleId,
            (tyre.sensorBattery as? SensorBattery.Voltage)?.volts?.toDouble(),
            (tyre.sensorBattery as? SensorBattery.Percentage)?.percent?.toLong(),
        )
    }

    public fun latestByTyreLocationByVehicle(
        location: Location,
        vehicleId: UUID
    ): QueryOneOrNull<Tyre.Located> = queries
        .latestByTyreLocationByVehicle(
            location,
            vehicleId
        ) { id, timestamp, rssi, _, pressure, temperature, battery, isAlarm, voltage, percent ->
            Tyre.Located(
                timestamp, rssi, id, pressure, temperature, battery, isAlarm, location,
                SensorBattery.fromStored(voltage, percent)
            )
        }
        .asOneOrNull()

    public fun latestBySensorByTyreLocationByVehicle(
        sensorId: Int,
        location: Location,
        vehicleId: UUID,
    ): QueryOneOrNull<Tyre.Located> = queries
        .latestBySensorByTyreLocationByVehicle(sensorId, location, vehicleId) {
                id, timestamp, rssi, _, pressure, temperature, battery, isAlarm, voltage, percent ->
            Tyre.Located(
                timestamp, rssi, id, pressure, temperature, battery, isAlarm, location,
                SensorBattery.fromStored(voltage, percent)
            )
        }.asOneOrNull()

    /** Historical rows keep their original wheel; a moved sensor's cached value uses its new wheel. */
    public fun latestBySensorByVehicle(sensorId: Int, location: Location,
        vehicleId: UUID): QueryOneOrNull<Tyre.Located> =
        queries.latestBySensorByVehicle(sensorId, vehicleId) {
                id, timestamp, rssi, _, pressure, temperature, battery, isAlarm, voltage, percent ->
            Tyre.Located(timestamp, rssi, id, pressure, temperature, battery, isAlarm, location,
                SensorBattery.fromStored(voltage, percent))
        }.asOneOrNull()
}
