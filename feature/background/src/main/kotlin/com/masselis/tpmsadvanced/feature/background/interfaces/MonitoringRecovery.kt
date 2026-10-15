package com.masselis.tpmsadvanced.feature.background.interfaces

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat.startForegroundService
import androidx.core.content.getSystemService
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.feature.background.ioc.RideDependencies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class MonitoringRecovery(private val context: Context) {
    private val sessions = MonitoringSessionStore(context)

    fun started(vehicle: Vehicle, automatic: Boolean) = sessions.started(
        MonitoringSession(vehicle.uuid, vehicle.name, automatic, System.currentTimeMillis(),
            Process.myPid(), bootCount(), appUpdatedAt())
    )

    fun check(): MonitoringInterruption? = monitoringInterruption(
        sessions.read(), MonitorService.isRunning.value, context.packageName,
        bootCount(), appUpdatedAt(), processExits()
    )

    fun keepStopped(session: MonitoringSession) {
        if (!MonitorService.isRunning.value && sessions.read() == session) {
            RidePreferences(context).armedVehicle = null
            sessions.clear()
        }
    }

    suspend fun restore(session: MonitoringSession): String? {
        val vehicle = withContext(Dispatchers.IO) {
            (appGraph as RideDependencies).rideVehicleDatabase.selectAll().execute()
                .firstOrNull { it.uuid == session.vehicleId }
        }
        // The service may have restarted, or the user may have started another vehicle during the lookup.
        return when {
            MonitorService.isRunning.value || sessions.read() != session -> null
            vehicle == null -> "This vehicle has been deleted. Close this message and choose another vehicle."
            session.automatic && !RidePreferences(context).read(vehicle.uuid).isValid() ->
                "The saved ride trigger is no longer valid. Set it up again in this vehicle's settings."
            else -> start(vehicle, session.automatic)
        }
    }

    private fun start(vehicle: Vehicle, automatic: Boolean): String? =
        try {
            startForegroundService(context, MonitorService.intent(vehicle, automatic))
            null
        } catch (_: IllegalStateException) {
            "Android could not start monitoring. Keep TyrePing open and try again."
        } catch (_: SecurityException) {
            "A required permission is missing. Open App settings, allow the permissions, then try again."
        }

    private fun bootCount(): Int = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    }.getOrDefault(-1)

    @Suppress("DEPRECATION")
    private fun appUpdatedAt(): Long = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime

    private fun processExits(): List<MonitoringProcessExit> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // Some Android builds do not expose exit history; detection still works without a cause.
        runCatching {
            context.getSystemService<ActivityManager>()?.getHistoricalProcessExitReasons(null, 0, 0).orEmpty()
                .map { MonitoringProcessExit(it.pid, it.processName, it.timestamp, it.reason) }
        }.getOrDefault(emptyList())
    } else emptyList()
}
