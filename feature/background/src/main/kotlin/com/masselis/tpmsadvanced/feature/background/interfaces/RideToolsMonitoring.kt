@file:Suppress("MatchingDeclarationName") // Shared monitoring functions and their accessory coordinator.

package com.masselis.tpmsadvanced.feature.background.interfaces

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.data.vehicle.ioc.VehicleToolsDependencies
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus
import com.masselis.tpmsadvanced.data.vehicle.model.WheelReadiness
import com.masselis.tpmsadvanced.data.vehicle.model.automaticVehicleCandidate
import com.masselis.tpmsadvanced.feature.background.R
import com.masselis.tpmsadvanced.feature.main.usecase.FleetHealth
import com.masselis.tpmsadvanced.feature.main.usecase.RideRuntime
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import java.util.UUID
import kotlinx.coroutines.sync.withLock

/** Shared between foreground and service: one accessory watcher regardless of UI ownership. */
@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
internal object AutomaticVehicleSelection {
    private data class Observation(val vehicle: String? = null, val problem: String? = null)
    val message = MutableStateFlow("Automatic selection is off")
    private val selections by lazy {
        val deps = appGraph as VehicleToolsDependencies
        deps.rideTools.values.flatMapLatest { settings ->
            val mappings = settings.vehicles.filterValues { it.accessoryAddress.isNotEmpty() }
            if (!settings.automaticSelection || mappings.isEmpty()) {
                message.value = if (!settings.automaticSelection) "Automatic selection is off"
                    else "Choose a vehicle accessory"
                flowOf(emptyList<Observation>())
            } else combine(mappings.map { (id, tools) ->
                flow { emitAll(appContext.ridePresence(RideTrigger(tools.accessoryAddress, false))) }
                    .map { Observation(vehicle = id.takeIf { _ -> it.present }) }.onStart { emit(Observation()) }
                    .retryWhen { _, _ ->
                        emit(Observation(problem = "Check Bluetooth and Nearby devices permission"))
                        delay(ACCESSORY_RETRY_MS); true
                    }
            }) { observations -> observations.toList() }
        }.combine(RideRuntime.manualSelection) { observations, manual -> observations to manual }
            .onEach { (observations, manual) ->
                val matches = observations.mapNotNull { it.vehicle }
                when {
                    manual -> message.value = "Manual selection is holding this vehicle"
                    observations.any { it.problem != null } ->
                        message.value = "Check Bluetooth and Nearby devices permission"
                    matches.size > 1 -> message.value = "Several vehicle accessories connected; select manually"
                    matches.size == 1 -> withContext(Dispatchers.IO) {
                        val id = UUID.fromString(checkNotNull(automaticVehicleCandidate(matches, manual)))
                        deps.toolsVehicles.selectAll().execute().firstOrNull { it.uuid == id }?.let { vehicle ->
                            RideRuntime.selectionMutex.withLock {
                                if (!RideRuntime.manualSelection.value) {
                                    deps.toolsVehicles.setIsCurrent(id, true)
                                    message.value = "Selected ${vehicle.name} from its connected accessory"
                                }
                            }
                        }
                    }
                    deps.rideTools.values.value.automaticSelection ->
                        message.value = "Waiting for a connected vehicle accessory"
                }
            }.shareIn(GlobalScope, SharingStarted.WhileSubscribed(), 1)
    }
    suspend fun watch(): Unit = selections.collect { }
}

/** Short optional explanations; tyre alerts retain priority and the silence control also mutes diagnostics. */
internal suspend fun spokenMonitoringDiagnostics(): Unit = coroutineScope {
    val deps = appGraph as VehicleToolsDependencies
    val player = AlertPlayer(appContext)
    try {
        combine(RideRuntime.status, deps.toolsScanner.scanStatus, deps.rideTools.values) { status, scan, settings ->
            diagnosticMessage(status, scan, settings.spokenDiagnostics)
        }.combine(AlertControls.active) { message, active -> message.takeUnless { active } }
            .combine(AlertControls.fleetActive) { message, active -> message.takeUnless { active } }
            .combine(AlertControls.silencedUntil) { message, until ->
                message.takeIf { SystemClock.elapsedRealtime() >= until }
            }.distinctUntilChanged().collectLatest { message ->
            if (message != null && !AlertControls.active.value &&
                SystemClock.elapsedRealtime() >= AlertControls.silencedUntil.value)
                player.play(listOf(AlertCue("diagnostic", message, false, now())),
                    AlertOptions(sound = AlertSound.SPEECH))
        }
    } finally { player.close() }
}

internal fun diagnosticMessage(status: String, scan: ScanStatus, enabled: Boolean): String? =
    if (!enabled) null else when (scan) {
        ScanStatus.WaitingForBluetooth -> "TyrePing monitoring paused. Bluetooth is disabled."
        is ScanStatus.Retrying -> "TyrePing scan interrupted. Android refused the scan. Retrying automatically."
        is ScanStatus.Failed -> "TyrePing scan failed. Open the app to check monitoring."
        else -> "TyrePing. $status"
    }

/** Fleet notifications include other bound vehicles and ride-specific missing sensors. */
@SuppressLint("MissingPermission")
internal suspend fun fleetWarnings(): Unit = coroutineScope {
    val manager = NotificationManagerCompat.from(appContext)
    val channel = "FLEET_WARNINGS"
    manager.createNotificationChannel(NotificationChannelCompat.Builder(channel,
        NotificationManagerCompat.IMPORTANCE_DEFAULT)
        .setName("Missing sensors and other vehicle warnings").build())
    val posted = mutableSetOf<Int>()
    val player = AlertPlayer(appContext)
    val cadence = AlertCadence()
    var playback: Job? = null
    var playingKeys = emptySet<String>()
    launch {
        combine(AlertControls.options, AlertControls.silencedUntil, AlertControls.active) { options, until, active ->
            !options.enabled || SystemClock.elapsedRealtime() < until || active
        }.collect { muted -> if (muted) playback?.cancelAndJoin() }
    }
    try {
        while (isActive) {
            val (activeIds, cues) = refreshFleetWarnings(manager, channel)
            (posted - activeIds).forEach(manager::cancel)
            posted.clear(); posted.addAll(activeIds)
            AlertControls.fleetActive.value = cues.isNotEmpty() && AlertControls.options.value.enabled
            if (!cues.map { it.key }.containsAll(playingKeys)) playback?.cancelAndJoin()
            if (!AlertControls.active.value) {
                if (playback?.isActive != true) {
                    val due = cadence.due(cues, AlertControls.options.value, SystemClock.elapsedRealtime(),
                        AlertControls.silencedUntil.value)
                    if (due.isNotEmpty()) {
                        playingKeys = due.map { it.key }.toSet()
                        playback = launch { player.play(due, AlertControls.options.value) }
                    }
                }
            }
            delay(FLEET_CHECK_MS)
        }
    } finally { withContext(NonCancellable) {
        playback?.cancelAndJoin()
        player.close(); posted.forEach(manager::cancel)
        AlertControls.fleetActive.value = false
    } }
}

private const val ACCESSORY_RETRY_MS = 30_000L
private const val FLEET_NOTIFICATION_MASK = 0x30400000
private const val FLEET_CHECK_MS = 5_000L

@SuppressLint("MissingPermission")
private fun refreshFleetWarnings(manager: NotificationManagerCompat, channel: String): Pair<Set<Int>, List<AlertCue>> {
    val fleet = FleetHealth.values.value
    val cues = mutableListOf<AlertCue>()
    val activeIds = mutableSetOf<Int>()
    fleet.forEach { health ->
        val missing = health.wheels.filter { it.state == WheelReadiness.MISSING }
        val secondary = health.vehicle.uuid != MonitorService.activeVehicle.value
        val alerts = health.wheels.filter { it.state == WheelReadiness.WARNING && secondary &&
            (it.reading?.timestamp ?: 0.0) >= (RideRuntime.started.value ?: Double.MAX_VALUE) }
        val text = (missing.map { "${it.label}: sensor missing" } +
            alerts.map { "${it.label}: ${it.warnings.joinToString()}" }).joinToString("; ")
        if (text.isNotEmpty()) {
            val id = health.vehicle.uuid.hashCode() xor FLEET_NOTIFICATION_MASK
            activeIds += id
            val intent = Intent(Intent.ACTION_VIEW, "tpmsadvanced://vehicle/${health.vehicle.uuid}".toUri())
                .setPackage(appContext.packageName)
            manager.notify(id, NotificationCompat.Builder(appContext,
                channel).setSmallIcon(R.drawable.car_tire_alert)
                .setContentTitle("${health.vehicle.name} · tyre warning").setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setOnlyAlertOnce(true).setSilent(true)
                .setContentIntent(PendingIntent.getActivity(appContext, id, intent,
                    PendingIntent.FLAG_IMMUTABLE))
                .addAction(0, "Silence alerts for 10 min", PendingIntent.getBroadcast(appContext, id,
                    Intent(appContext, SilenceAlertsReceiver::class.java),
                        PendingIntent.FLAG_IMMUTABLE)).build())
            (missing + alerts).forEach { wheel -> cues += AlertCue(
                "${health.vehicle.uuid}:${wheel.label}:${wheel.state}",
                "${health.vehicle.name}. ${wheel.label}. " + if (wheel.state == WheelReadiness.MISSING)
                    "Sensor missing" else wheel.warnings.joinToString(), false, now()) }
        }
    }
    return activeIds to cues
}
