package com.masselis.tpmsadvanced.data.vehicle.interfaces

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.data.vehicle.model.MaintenanceWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/** Temporary suppression survives process restarts, but never survives a reboot or extends on clock rollback. */
public class MaintenanceMode(private val context: Context) {
    private val prefs = context.getSharedPreferences("MAINTENANCE_MODE", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(runCatching {
        Json.decodeFromString<List<MaintenanceWindow>>(prefs.getString("windows", "[]")!!)
    }.getOrDefault(emptyList()))
    public val windows: StateFlow<List<MaintenanceWindow>> = mutable
    public fun observe(vehicle: UUID): kotlinx.coroutines.flow.Flow<Boolean> =
        kotlinx.coroutines.flow.flow {
            while (true) { emit(active(vehicle)); kotlinx.coroutines.delay(REFRESH_MS) }
        }
    private fun boot(): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    public fun active(vehicle: UUID): Boolean = mutable.value.any {
        it.vehicle == vehicle.toString() && it.active(SystemClock.elapsedRealtime(), now(), boot())
    }
    public fun remainingMinutes(vehicle: UUID): Long = mutable.value.firstOrNull {
        it.vehicle == vehicle.toString() && it.active(SystemClock.elapsedRealtime(), now(), boot())
    }?.let { (it.elapsedDeadline - SystemClock.elapsedRealtime() + MINUTE_MS - 1) / MINUTE_MS } ?: 0
    @Synchronized
    public fun start(vehicle: UUID, minutes: Int) {
        require(minutes in 1..MAX_MINUTES)
        val boot = boot()
        check(boot >= 0) { "Cannot safely start timed maintenance on this device" }
        save(mutable.value.filterNot { it.vehicle == vehicle.toString() } +
            MaintenanceWindow(vehicle.toString(), SystemClock.elapsedRealtime() + minutes * MINUTE_MS,
                now() + minutes * SECONDS_PER_MINUTE, boot))
    }
    @Synchronized
    public fun stop(vehicle: UUID): Unit = save(mutable.value.filterNot { it.vehicle == vehicle.toString() })
    @Synchronized
    public fun expire() {
        val kept = mutable.value.filter { it.active(SystemClock.elapsedRealtime(), now(), boot()) }
        if (kept != mutable.value) save(kept)
    }
    private fun save(windows: List<MaintenanceWindow>) {
        check(prefs.edit().putString("windows", Json.encodeToString(windows)).commit())
        mutable.value = windows
    }
    private companion object {
        const val REFRESH_MS = 1000L
        const val MINUTE_MS = 60_000L
        const val SECONDS_PER_MINUTE = 60.0
        const val MAX_MINUTES = 60
    }
}
