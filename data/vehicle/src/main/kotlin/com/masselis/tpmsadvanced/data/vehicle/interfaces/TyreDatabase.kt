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
    database: Database
) {
    private val queries = database.tyreQueries

    public suspend fun insert(tyre: Tyre.Located, vehicleId: UUID): Unit = withContext(IO) {
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
}
