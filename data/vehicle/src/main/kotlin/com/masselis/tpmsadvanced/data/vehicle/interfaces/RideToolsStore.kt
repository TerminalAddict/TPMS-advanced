package com.masselis.tpmsadvanced.data.vehicle.interfaces

import android.content.Context
import com.masselis.tpmsadvanced.data.vehicle.model.RideToolsData
import com.masselis.tpmsadvanced.data.vehicle.model.VehicleRideTools
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/** One versioned document, committed atomically. Shared by UI, service, widget and backup. */
public class RideToolsStore(context: Context) {
    private val prefs = context.getSharedPreferences("RIDE_TOOLS", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(read())
    public val values: StateFlow<RideToolsData> = mutable
    // SharedPreferences retains listeners weakly; keep this reference for the store's lifetime.
    @Suppress("UnusedPrivateProperty")
    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        mutable.value = read()
    }.also { prefs.registerOnSharedPreferenceChangeListener(it) }

    public fun read(): RideToolsData = prefs.getString("data", null)?.let {
        Json.decodeFromString<RideToolsData>(it).also(RideToolsData::validate)
    } ?: RideToolsData()

    public fun update(change: (RideToolsData) -> RideToolsData) {
        synchronized(lock) {
            val value = change(read()).also(RideToolsData::validate)
            check(prefs.edit().putString("data",
                Json.encodeToString(value)).commit()) { "Could not save ride settings" }
            mutable.value = value
        }
    }

    public fun updateVehicle(id: UUID, change: (VehicleRideTools) -> VehicleRideTools): Unit = update { data ->
        val previous = data.vehicles[id.toString()] ?: VehicleRideTools()
        data.copy(vehicles = data.vehicles + (id.toString() to change(previous)))
    }

    public companion object { private val lock = Any() }
}
