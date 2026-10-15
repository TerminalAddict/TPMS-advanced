package com.masselis.tpmsadvanced.feature.background.interfaces

import android.content.Context
import androidx.core.content.edit
import java.util.UUID

internal data class RideTrigger(val address: String, val beacon: Boolean, val minRssi: Int = DEFAULT_RSSI) {
    fun isValid(): Boolean = ADDRESS.matches(address) && minRssi in MIN_RSSI..MAX_RSSI

    companion object {
        const val DEFAULT_RSSI = -80
        private val ADDRESS = "([0-9A-F]{2}:){5}[0-9A-F]{2}".toRegex()
        private const val MIN_RSSI = -100
        private const val MAX_RSSI = -30
    }
}

internal class RidePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("AUTOMATIC_RIDES", Context.MODE_PRIVATE)
    var armedVehicle: UUID?
        get() = preferences.getString("armed", null)?.let(UUID::fromString)
        set(value) { preferences.edit(commit = true) { putString("armed", value?.toString()) } }

    fun read(vehicle: UUID): RideTrigger = RideTrigger(
        preferences.getString("$vehicle.address", "") ?: "",
        preferences.getBoolean("$vehicle.beacon", false),
        preferences.getInt("$vehicle.rssi", RideTrigger.DEFAULT_RSSI),
    )

    fun save(vehicle: UUID, trigger: RideTrigger) {
        require(trigger.isValid())
        preferences.edit {
            putString("$vehicle.address", trigger.address)
            putBoolean("$vehicle.beacon", trigger.beacon)
            putInt("$vehicle.rssi", trigger.minRssi)
        }
    }

}

/** Require two nearby packets and tolerate short gaps; use elapsed time, never wall time. */
internal class BeaconPresence(private val minRssi: Int) {
    private var first: Long? = null
    private var last: Long? = null
    private var confirmed = false

    fun received(now: Long, rssi: Int) {
        if (rssi !in MIN_VALID_RSSI..MAX_VALID_RSSI || rssi < minRssi) return
        if (last == null || now - checkNotNull(last) > TIMEOUT_MS) {
            first = now
            confirmed = false
        }
        if (now - checkNotNull(first) >= CONFIRM_MS) confirmed = true
        last = now
    }

    fun isPresent(now: Long): Boolean = confirmed && last?.let { now - it <= TIMEOUT_MS } == true

    companion object {
        private const val MIN_VALID_RSSI = -127
        private const val MAX_VALID_RSSI = 126
        const val TIMEOUT_MS = 60_000L
        private const val CONFIRM_MS = 1_000L
    }
}
