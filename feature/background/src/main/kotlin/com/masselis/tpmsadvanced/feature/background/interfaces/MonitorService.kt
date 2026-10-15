package com.masselis.tpmsadvanced.feature.background.interfaces

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat.getParcelableExtra
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.feature.background.R
import com.masselis.tpmsadvanced.feature.background.ioc.RideDependencies
import com.masselis.tpmsadvanced.feature.background.ioc.vehicle.ServiceComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import com.masselis.tpmsadvanced.feature.main.usecase.FleetHealth
import com.masselis.tpmsadvanced.feature.main.usecase.RideRuntime
import com.masselis.tpmsadvanced.core.common.now

/** One foreground service owns both waiting and monitoring, so detection needs no background launch. */
@SuppressLint("MissingPermission")
internal class MonitorService : LifecycleService() {
    private var watcher: Job? = null
    private var monitoring: Job? = null
    private var configuration: Job? = null
    private val preferences by lazy { RidePreferences(this) }

    override fun onCreate() {
        super.onCreate()
        lifecycleScope.launch { FleetHealth.observe() }
        lifecycleScope.launch { AutomaticVehicleSelection.watch() }
        lifecycleScope.launch { spokenMonitoringDiagnostics() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        stopRequested = false
        val automatic = intent?.getBooleanExtra(PARAM_AUTO, false) ?: (preferences.armedVehicle != null)
        if (!hasRidePermissions(automatic)) {
            preferences.armedVehicle = null
            stopSelf()
            return START_NOT_STICKY
        }
        isRunningMutableStateFlow.value = true
        showWaiting("Preparing monitoring")
        val previousConfiguration = configuration
        previousConfiguration?.cancel()
        configuration = lifecycleScope.launch {
            previousConfiguration?.join()
            watcher?.cancelAndJoin()
            stopReadings()
            val vehicle = resolveVehicle(intent)
            if (stopRequested) return@launch
            if (vehicle == null) {
                preferences.armedVehicle = null
                stopSelf()
                return@launch
            }
            activeVehicle.value = vehicle.uuid
            if (automatic) {
                val trigger = preferences.read(vehicle.uuid)
                if (!trigger.isValid()) {
                    preferences.armedVehicle = null
                    stopSelf()
                    return@launch
                }
                preferences.armedVehicle = vehicle.uuid
                MonitoringRecovery(this@MonitorService).started(vehicle, automatic = true)
                showWaiting("${vehicle.name}: waiting for ride trigger")
                watcher = watchRide(vehicle, trigger)
            } else {
                preferences.armedVehicle = null
                MonitoringRecovery(this@MonitorService).started(vehicle, automatic = false)
                startReadings(vehicle)
            }
        }
        return if (intent?.getBooleanExtra(PARAM_AUTO, false) == true || intent == null)
            START_STICKY else START_NOT_STICKY
    }

    private suspend fun resolveVehicle(intent: Intent?): Vehicle? =
        intent?.let { getParcelableExtra(it, PARAM_VEHICLE, Vehicle::class.java) }
            ?: preferences.armedVehicle?.let { uuid ->
                withContext(Dispatchers.IO) {
                    runCatching {
                        (appGraph as RideDependencies).rideVehicleDatabase.selectByUuid(uuid).execute()
                    }.getOrNull()
                }
            }

    // This service boundary reports unexpected platform failures instead of silently losing the watch.
    @Suppress("TooGenericExceptionCaught")
    private fun watchRide(vehicle: Vehicle, trigger: RideTrigger) = lifecycleScope.launch {
        try {
            ridePresence(trigger).withDisconnectGrace { monitoring != null }.collect { presence ->
                if (presence.present) {
                    if (monitoring == null) startReadings(vehicle, presence.message)
                } else {
                    showWaiting("${vehicle.name}: ${presence.message}")
                    stopReadings()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            showWaiting("Automatic monitoring paused. Check Bluetooth and permissions in TyrePing.")
            stopReadings()
            Logger.withTag("AutomaticRide").w("Ride trigger stopped", failure)
        }
    }

    private fun startReadings(vehicle: Vehicle, reason: String = "manual start") {
        val job = SupervisorJob(lifecycleScope.coroutineContext[Job])
        monitoring = job
        readingsActive.value = true
        RideRuntime.started.value = now()
        RideRuntime.parked.value = false
        val scope = CoroutineScope(lifecycleScope.coroutineContext + job)
        scope.launch { FleetHealth.captureReadings() }
        scope.launch { fleetWarnings() }
        ServiceComponent(vehicle, this, scope)
        status.value = "Monitoring ${vehicle.name}: $reason"
        RideRuntime.status.value = status.value
    }

    private suspend fun stopReadings() {
        monitoring?.cancelAndJoin()
        monitoring = null
        readingsActive.value = false
        RideRuntime.started.value = null
    }

    private fun hasRidePermissions(automatic: Boolean): Boolean {
        val permissions = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            automatic && Build.VERSION.SDK_INT in Build.VERSION_CODES.Q..Build.VERSION_CODES.R ->
                listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            else -> listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun showWaiting(message: String) {
        status.value = message
        RideRuntime.status.value = message
        val manager = NotificationManagerCompat.from(this)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Automatic ride monitoring").build()
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.car_tire)
            .setContentTitle("TyrePing")
            .setContentText(message)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply {
                activeVehicle.value?.let { uuid ->
                    val launchIntent = Intent(Intent.ACTION_VIEW, "tpmsadvanced://vehicle/$uuid".toUri())
                    setContentIntent(
                        TaskStackBuilder.create(this@MonitorService).addNextIntentWithParentStack(launchIntent)
                            .getPendingIntent(WAITING_ID,
                                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    )
                }
            }
            .addAction(0, "Stop", PendingIntent.getBroadcast(
                this, WAITING_ID, DisableMonitorBroadcastReceiver.intent(), PendingIntent.FLAG_IMMUTABLE
            ))
            .build()
        ServiceCompat.startForeground(
            this, WAITING_ID, notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        )
    }

    override fun onDestroy() {
        // Android may destroy the service without the user stopping it. Only explicit Stop clears the record.
        super.onDestroy()
        isRunningMutableStateFlow.value = false
        activeVehicle.value = null
        status.value = "Stopped"
        RideRuntime.status.value = "Monitoring stopped"
        RideRuntime.started.value = null
        readingsActive.value = false
    }

    companion object {
        private var stopRequested = false
        private val isRunningMutableStateFlow = MutableStateFlow(false)
        val isRunning = isRunningMutableStateFlow.asStateFlow()
        val activeVehicle = MutableStateFlow<UUID?>(null)
        val status = MutableStateFlow("Stopped")
        val readingsActive = MutableStateFlow(false)
        private const val PARAM_VEHICLE = "VEHICLE_PARAM"
        private const val PARAM_AUTO = "AUTOMATIC_RIDE"
        private const val CHANNEL = "AUTOMATIC_RIDES"
        const val WAITING_ID = 414100

        fun intent(vehicle: Vehicle, automatic: Boolean = false) = Intent(appContext, MonitorService::class.java)
            .putExtra(PARAM_VEHICLE, vehicle).putExtra(PARAM_AUTO, automatic)

        fun stop(context: Context) {
            stopRequested = true
            RidePreferences(context).armedVehicle = null
            MonitoringSessionStore(context).clear()
            context.stopService(Intent(context, MonitorService::class.java))
        }
    }
}
