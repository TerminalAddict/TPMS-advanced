package com.masselis.tpmsadvanced.feature.background.interfaces

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import co.touchlab.kermit.Logger
import java.util.UUID

/** Written only at session boundaries, including an automatic watch waiting for its accessory. */
internal data class MonitoringSession(
    val vehicleId: UUID,
    val vehicleName: String,
    val automatic: Boolean,
    val startedAt: Long,
    val processId: Int,
    val bootCount: Int,
    val appUpdatedAt: Long,
)

internal class MonitoringSessionStore(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("MONITORING_SESSION", Context.MODE_PRIVATE))

    fun read(): MonitoringSession? = preferences.getString("vehicle", null)?.let { vehicle ->
        runCatching {
            MonitoringSession(
                UUID.fromString(vehicle), preferences.getString("name", "Vehicle") ?: "Vehicle",
                preferences.getBoolean("automatic", false), preferences.getLong("started", 0),
                preferences.getInt("pid", 0), preferences.getInt("boot", -1), preferences.getLong("updated", 0)
            )
        }.getOrNull()
    }

    // Commit these small writes before returning: apply() can lose a stop just before a process kill.
    fun started(session: MonitoringSession) {
        val saved = preferences.edit()
            .putString("vehicle", session.vehicleId.toString()).putString("name", session.vehicleName)
            .putBoolean("automatic", session.automatic).putLong("started", session.startedAt)
            .putInt("pid", session.processId).putInt("boot", session.bootCount)
            .putLong("updated", session.appUpdatedAt).commit()
        if (!saved) Logger.withTag("MonitoringSession").w("Could not save monitoring recovery information")
    }

    fun clear() {
        preferences.edit(commit = true) { clear() }
    }
}
