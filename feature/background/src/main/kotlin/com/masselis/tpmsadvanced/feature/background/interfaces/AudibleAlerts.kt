package com.masselis.tpmsadvanced.feature.background.interfaces

import android.os.SystemClock
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.data.vehicle.model.TyreAtmosphere
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.TyreComponent
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Both the visible screen and the service use this engine; only one owns it at a time. */
internal suspend fun audibleAlerts(component: VehicleComponent): Unit = coroutineScope {
    val openedAt = now()
    val maintenance = (com.masselis.tpmsadvanced.core.common.appGraph as
        com.masselis.tpmsadvanced.data.vehicle.ioc.VehicleToolsDependencies).maintenanceMode
    val cues = MutableStateFlow<List<AlertCue>>(emptyList())
    val ranges = component.vehicleRangesUseCase
    val sources = component.vehicle.kind.locations.map { location ->
        combine(component.TyreComponent(location).tyreAtmosphereUseCase.listen(),
            ranges.resolvePressures(location), ranges.highTemp) { reading, pressure, temperature ->
            reading.alertCues(location.toString().lowercase().replace('_', ' '),
                pressure.first.kpa, pressure.second.kpa, temperature.celsius)
        }.onStart { emit(emptyList()) }
    }
    combine(sources) { it.toList().flatten() }.catch { emit(emptyList()) }.onEach { cues.value = it }.launchIn(this)
    val player = AlertPlayer(appContext)
    val cadence = AlertCadence()
    var playback: Job? = null
    var playingKeys = emptySet<String>()
    try {
        while (isActive) {
            val options = AlertControls.options.value
            val live = cues.value.filter { it.receivedAt >= openedAt && now() - it.receivedAt in 0.0..STALE_SECONDS }
                .filterNot { maintenance.active(component.vehicle.uuid) &&
                    (":tyre pressure:" in it.key || ":possible tyre leak:" in it.key) }
            val muted = SystemClock.elapsedRealtime() < AlertControls.silencedUntil.value
            AlertControls.active.value = live.isNotEmpty() && options.enabled
            if (muted || !options.enabled || !live.map { it.key }.containsAll(playingKeys)) playback?.cancelAndJoin()
            if (playback?.isActive != true) {
                val due = cadence.due(live, options, SystemClock.elapsedRealtime(), AlertControls.silencedUntil.value)
                if (due.isNotEmpty()) {
                    playingKeys = due.map { it.key }.toSet()
                    playback = launch { player.play(due, options) }
                }
            }
            delay(ALERT_CHECK_MS)
        }
    } finally {
        withContext(NonCancellable) {
            playback?.cancelAndJoin()
            player.close()
            AlertControls.active.value = false
        }
    }
}

@Suppress("MagicNumber")
internal fun TyreAtmosphere.alertCues(wheel: String, low: Float, high: Float, hot: Float): List<AlertCue> = buildList {
    fun add(kind: String, critical: Boolean = false) {
        add(AlertCue("$sensorId:$kind:$critical", "$wheel: ${if (critical) "critical " else ""}$kind", critical,
            timestamp))
    }
    if (pressure.kpa < low || pressure.kpa > high)
        add("tyre pressure", pressure.kpa <= low * .75f || pressure.kpa >= high * 1.2f)
    if (temperature.celsius > hot) add("tyre temperature", temperature.celsius >= hot + 20)
    if (pressureLoss != null) add("possible tyre leak")
    if (sensorBattery?.isLow == true) add("sensor battery low")
}

private const val ALERT_CHECK_MS = 250L
private const val STALE_SECONDS = 600.0
