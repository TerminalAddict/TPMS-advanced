package com.masselis.tpmsadvanced.feature.background.interfaces

import android.annotation.SuppressLint
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.getBroadcast
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.PRIORITY_LOW
import androidx.core.app.NotificationCompat.PRIORITY_MAX
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_LOW
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_MAX
import androidx.core.app.ServiceCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.data.unit.interfaces.UnitPreferences
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner
import com.masselis.tpmsadvanced.data.vehicle.model.TyreAtmosphere
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.feature.background.R
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.NoAlert
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.BatteryAlert
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.PressureAlert
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.PressureLossAlert
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.ScanFailure
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.TemperatureAlert
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.TyreComponent
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.usecase.VehicleRangesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlin.time.Duration.Companion.milliseconds

@Suppress("OPT_IN_USAGE", "LongParameterList")
@OptIn(FlowPreview::class)
@SuppressLint("MissingPermission")
internal class ServiceNotifier(
    vehicle: Vehicle,
    vehicleComponent: VehicleComponent,
    scope: CoroutineScope,
    vehicleRangesUseCase: VehicleRangesUseCase,
    unitPreferences: UnitPreferences,
    service: Service,
    scanner: BluetoothLeScanner,
) {
    private val notificationManager = NotificationManagerCompat.from(appContext)
    private val logger = Logger.withTag("ServiceNotifier")

    init {
        scope.launch { audibleAlerts(vehicleComponent) }
        notificationManager.createNotificationChannel(
            NotificationChannelCompat
                .Builder(channelNameWhenOk, IMPORTANCE_LOW)
                .setName("Monitor service when tyres are OK")
                .build()
        )
        notificationManager.createNotificationChannel(
            NotificationChannelCompat
                .Builder(channelNameForAlerts, IMPORTANCE_MAX)
                .setName("Monitor service when alerting")
                .build()
        )

        vehicle
            .kind
            .locations
            .map { vehicleComponent.TyreComponent(it) }
            .map { comp -> comp.tyreAtmosphereUseCase.listen().map { comp.location to it } }
            .partialReadings()
            .debounce(100.milliseconds)
            .alertStates(vehicleRangesUseCase)
            .catch { logger.e("Failed to listen for atmospheres", it); emit(ScanFailure) }
            .combine(scanner.scanStatus.notices()) { state, notice -> state to notice }
            .distinctUntilChanged()
            .map { (state, notice) ->
                val hasAlert = state is PressureAlert || state is TemperatureAlert ||
                    state is PressureLossAlert || state is BatteryAlert
                val urgent = hasAlert || state == ScanFailure || notice?.urgent == true
                NotificationCompat
                    .Builder(
                        appContext,
                        if (urgent) channelNameForAlerts else channelNameWhenOk
                    )
                    .setSmallIcon(
                        if (urgent || notice != null) R.drawable.car_tire_alert else R.drawable.car_tire
                    )
                    .setPriority(
                        if (urgent) PRIORITY_MAX else PRIORITY_LOW
                    )
                    .setSubText(vehicle.name)
                    .setContentText(
                        when (state) {
                            NoAlert -> "Monitoring tyre readings"
                            is PressureAlert -> "⚠️ A tyre reached the pressure of ${
                                state.atmosphere.pressure.string(unitPreferences.pressure.value)
                            } !!!"

                            is TemperatureAlert -> "⚠️ A tyre reached the temperature of ${
                                state.atmosphere.temperature.string(unitPreferences.temperature.value)
                            } !!!"

                            is PressureLossAlert -> "Possible leak (experimental): pressure fell ${
                                state.atmosphere.pressureLoss?.drop?.string(unitPreferences.pressure.value)
                            }"

                            is BatteryAlert -> "Low sensor battery: ${state.atmosphere.sensorBattery?.label()}"

                            ScanFailure -> "Monitoring stopped after an unexpected error. Restart the app to try again."
                        }.let { readingText ->
                            if (notice == null) readingText
                            else notice.message + if (hasAlert) " Last received: $readingText" else ""
                        }
                    )
                    .apply {
                        when {
                            state != ScanFailure || notice?.canRestart == false ->
                                Intent(
                                    Intent.ACTION_VIEW,
                                    "tpmsadvanced://vehicle/${vehicle.uuid}".toUri(),
                                ).let {
                                    TaskStackBuilder
                                        .create(appContext)
                                        .addNextIntentWithParentStack(it)
                                        .getPendingIntent(
                                            vehicle.uuid.hashCode(),
                                            FLAG_IMMUTABLE
                                        )
                                }.also(::setContentIntent)

                            else -> {
                                // Nothing to do, the intent does nothing when clicked
                            }
                        }

                    }
                    .addAction(
                        when {
                            state != ScanFailure || notice?.canRestart == false ->
                                NotificationCompat.Action.Builder(
                                    null,
                                    "Stop",
                                    getBroadcast(
                                        appContext,
                                        vehicle.uuid.hashCode(),
                                        DisableMonitorBroadcastReceiver.intent(),
                                        FLAG_IMMUTABLE
                                    )
                                ).build()

                            else ->
                                NotificationCompat.Action.Builder(
                                    null,
                                    "Restart app",
                                    getBroadcast(
                                        appContext,
                                        vehicle.uuid.hashCode(),
                                        RestartAppBroadcastReceiver.intent(),
                                        FLAG_IMMUTABLE
                                    )
                                ).build()
                        }

                    )
                    .setAutoCancel(false)
                    .setSilent(true)
                    .setOnlyAlertOnce(true)
                    .apply {
                        if (hasAlert) addAction(0, "Silence alerts for 10 min", getBroadcast(
                            appContext, vehicle.uuid.hashCode(),
                            Intent(appContext, SilenceAlertsReceiver::class.java), FLAG_IMMUTABLE
                        ))
                    }
                    .setOngoing(true)
                    .build()
            }
            .onEach {
                ServiceCompat.startForeground(
                    service,
                    vehicle.uuid.hashCode(),
                    it,
                    // https://developer.android.com/about/versions/14/changes/fgs-types-required#connected-device
                    if (SDK_INT >= Q) FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
                )
                notificationManager.cancel(MonitorService.WAITING_ID)
            }
            .launchIn(scope)

        callbackFlow<Nothing> {
            awaitClose {
                notificationManager.cancel(vehicle.uuid.hashCode())
            }
        }.launchIn(scope)
    }

    sealed interface State {
        data object NoAlert : State

        @JvmInline
        value class PressureAlert(val atmosphere: TyreAtmosphere) : State

        @JvmInline
        value class TemperatureAlert(val atmosphere: TyreAtmosphere) : State

        @JvmInline
        value class PressureLossAlert(val atmosphere: TyreAtmosphere) : State

        @JvmInline
        value class BatteryAlert(val atmosphere: TyreAtmosphere) : State

        data object ScanFailure : State
    }

    @Suppress("ConstPropertyName")
    internal companion object {
        private const val channelNameWhenOk = "MONITOR_SERVICE_WHEN_OK"
        private const val channelNameForAlerts = "MONITOR_SERVICE_FOR_ALERT"
    }
}
