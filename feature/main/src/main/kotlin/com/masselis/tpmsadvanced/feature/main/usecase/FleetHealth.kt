package com.masselis.tpmsadvanced.feature.main.usecase

import android.os.SystemClock
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.data.vehicle.ioc.VehicleToolsDependencies
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.MissingSensorTracker
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.SensorCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.TyreAtmosphere
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.WheelHealth
import com.masselis.tpmsadvanced.data.vehicle.model.WheelReadiness
import com.masselis.tpmsadvanced.data.vehicle.model.wheelHealth
import com.masselis.tpmsadvanced.data.vehicle.model.backupLocation
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.flow
import java.util.UUID

public data class VehicleHealth(val vehicle: Vehicle, val wheels: List<WheelHealth>, val maintenance: String?,
    val imbalances: List<com.masselis.tpmsadvanced.data.vehicle.model.AxleImbalance> = emptyList()) {
    public val warning: Boolean get() = wheels.any { it.state == WheelReadiness.WARNING ||
        it.state == WheelReadiness.MISSING } || maintenance != null || imbalances.isNotEmpty()
    public val ready: Boolean get() = wheels.all { it.state == WheelReadiness.HEALTHY } && imbalances.isEmpty()
}

/** The combined dashboard must not call the rig healthy while its trailer is stale or warning. */
public fun groupedVehicleHealth(members: List<VehicleHealth>): VehicleHealth? = members.firstOrNull()?.copy(
    wheels = members.flatMap { it.wheels }, maintenance = members.firstNotNullOfOrNull { it.maintenance },
    imbalances = members.flatMap { it.imbalances })

/** Public service state lets dashboard and widget accurately distinguish waiting, stopped and riding. */
public object RideRuntime {
    public val status: MutableStateFlow<String> = MutableStateFlow("Monitoring stopped")
    public val started: MutableStateFlow<Double?> = MutableStateFlow(null)
    public val parked: MutableStateFlow<Boolean> = MutableStateFlow(false)
    public val manualSelection: MutableStateFlow<Boolean> = MutableStateFlow(false)
    public val selectionMutex: kotlinx.coroutines.sync.Mutex = kotlinx.coroutines.sync.Mutex()
}

@OptIn(DelicateCoroutinesApi::class)
public object FleetHealth {
    private const val REFRESH_MS = 5_000L
    private const val MINUTE_MS = 60_000L
    private val deps get() = appGraph as VehicleToolsDependencies
    private val tracker = MissingSensorTracker()
    private val liveLeaks = java.util.concurrent.ConcurrentHashMap<String, Tyre.Located>()
    internal fun recordLeak(vehicle: UUID, reading: Tyre.Located) {
        val key = "$vehicle:${reading.location}"
        if (reading.pressureLoss == null) liveLeaks.remove(key) else liveLeaks[key] = reading
    }
    public val values: MutableStateFlow<List<VehicleHealth>> = MutableStateFlow(emptyList())
    public val scanNotice: MutableStateFlow<String?> = MutableStateFlow(null)

    /** Collection only while visible or actively monitoring; uses the existing shared BLE radio. */
    private val capture by lazy {
        deps.toolsScanner.normalScan().onEach { record ->
            if (record is Tyre) deps.toolsTyres.insertBound(record)
        }.retryWhen { _, _ -> delay(REFRESH_MS); true }
            .shareIn(GlobalScope, SharingStarted.WhileSubscribed(), replay = 0)
    }

    public suspend fun captureReadings(): Unit = capture.collect { }

    /** A widget refresh reads cached data without waking or starting the scanner. */
    public suspend fun snapshot(): List<VehicleHealth> = withContext(Dispatchers.IO) {
        deps.maintenanceMode.expire()
        scanNotice.value = scanWarning(deps.toolsScanner.scanStatus.value, RideRuntime.started.value != null)
        val settings = deps.rideTools.read()
        val raw = deps.toolsVehicles.selectAll().execute().associateWith { vehicle ->
            vehicle.kind.locations.associateWith { location ->
                val bound = deps.toolsSensors.selectByVehicleAndLocation(vehicle.uuid, location).execute()
                (if (bound == null) deps.toolsTyres.latestByTyreLocationByVehicle(location, vehicle.uuid)
                    else deps.toolsTyres.latestBySensorByVehicle(bound.id, location, vehicle.uuid)).execute()
                    ?.takeIf { bound != null || deps.toolsSensors.selectById(it.sensorId).execute() == null }
            }
        }
        val missing = synchronized(tracker) {
            tracker.evaluate(raw.filterKeys { !deps.maintenanceMode.active(it.uuid) }.flatMap { (vehicle,
                wheels) -> wheels.mapNotNull { (location, reading) ->
                reading?.let { key(vehicle.uuid, location, it.sensorId) to it.timestamp }
            } }.toMap(), RideRuntime.started.value.takeIf { !RideRuntime.parked.value && settings.missingEnabled },
                deps.toolsScanner.scanStatus.value == ScanStatus.Scanning, SystemClock.elapsedRealtime(),
                settings.missingMinutes * MINUTE_MS)
        }
        raw.map { (vehicle, wheels) ->
            VehicleHealth(vehicle, wheels.map { (location, tyre) ->
                val calibration = tyre?.let { deps.toolsCalibration.values.value[it.sensorId] } ?: SensorCalibration()
                val reading = tyre?.let { TyreAtmosphere(it.timestamp, it.sensorId,
                    if (it.isAlarm) Pressure(0f) else calibration.pressure(it.pressure),
                    calibration.temperature(it.temperature), it.sensorBattery,
                    liveLeaks["${vehicle.uuid}:$location"]?.takeIf { leak ->
                        leak.sensorId == it.sensorId && leak.timestamp == it.timestamp
                    }?.pressureLoss ?: it.pressureLoss) }
                wheelHealth(vehicle, location, reading, now(), tyre?.let {
                    key(vehicle.uuid, location, it.sensorId) in missing
                } == true)
            }, settings.vehicles[vehicle.uuid.toString()]?.reminder?.takeIf {
                it.isDue(java.time.LocalDate.now())
            }?.label)
        }.map { health -> health.copy(imbalances = com.masselis.tpmsadvanced.data.vehicle.model.axleImbalances(
            health.wheels, settings.insights.imbalanceKpa[health.vehicle.uuid.toString()], now())) }
            .also { values.value = it }
    }

    private val observations by lazy {
        flow { while (true) {
            val fleet = snapshot()
            val maintenance = fleet.filter { deps.maintenanceMode.active(it.vehicle.uuid) }
                .map { it.vehicle.uuid.toString() }.toSet()
            deps.monitoringHistory.recordEvents(fleet.flatMap {
                it.monitoringEvents(it.vehicle.uuid.toString() in maintenance)
            }, now(), fleet.flatMap { health -> health.wheels.filter {
                it.state == WheelReadiness.SLEEPING || it.state == WheelReadiness.UNSEEN
            }.map { "${health.vehicle.uuid}:${backupLocation(it.location)}" }
            }.toSet(), maintenance)
            emit(Unit); delay(REFRESH_MS)
        } }.retryWhen { failure, _ ->
            co.touchlab.kermit.Logger.withTag("MonitoringHistory").w("Could not record monitoring status", failure)
            delay(REFRESH_MS); true
        }
            .shareIn(GlobalScope, SharingStarted.WhileSubscribed(), replay = 0)
    }
    /** Phone, car host and service can observe together without duplicating the polling loop. */
    public suspend fun observe(): Unit = observations.collect { }
    private fun key(id: UUID, location: Location, sensor: Int) = "$id:$location:$sensor"
}

private fun scanWarning(status: ScanStatus, monitoring: Boolean): String? = when (status) {
    ScanStatus.WaitingForBluetooth -> "Bluetooth is off; monitoring paused"
    is ScanStatus.Retrying -> "Android refused the scan; retrying automatically"
    is ScanStatus.Failed -> "Scanning failed; check Bluetooth and permissions"
    ScanStatus.Starting -> "Starting Bluetooth scan"
    ScanStatus.Idle -> if (monitoring) "Scanner is inactive" else null
    ScanStatus.Scanning -> null
}
