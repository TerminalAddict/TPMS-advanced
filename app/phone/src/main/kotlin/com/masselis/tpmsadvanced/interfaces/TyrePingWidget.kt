package com.masselis.tpmsadvanced.interfaces

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.masselis.tpmsadvanced.R
import com.masselis.tpmsadvanced.core.common.AppGraphReadyInitializer
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.data.vehicle.ioc.VehicleToolsDependencies
import com.masselis.tpmsadvanced.data.unit.interfaces.UnitPreferences
import com.masselis.tpmsadvanced.feature.main.usecase.FleetHealth
import com.masselis.tpmsadvanced.feature.main.usecase.RideRuntime
import com.masselis.tpmsadvanced.feature.main.usecase.VehicleHealth
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@ContributesTo(AppScope::class)
public interface WidgetDependencies { public val widgetUnits: UnitPreferences }

/** Cached reads only: installing or refreshing a widget never starts background BLE scans. */
public class TyrePingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val result = goAsync()
        widgetScope.launch {
            try { renderWidgets(context, FleetHealth.snapshot()) }
            finally { result.finish() }
        }
    }
    public class Initializer : AppGraphReadyInitializer<Unit> {
        override fun create(context: Context) {
            val deps = appGraph as VehicleToolsDependencies
            val units = (appGraph as WidgetDependencies).widgetUnits
            val monitoring = combine(RideRuntime.status, FleetHealth.scanNotice, RideRuntime.parked) {
                    status, notice, parked -> (notice ?: status) + if (parked) " · Parked" else ""
            }
            widgetScope.launch {
                deps.rideTools.values.collect { MaintenanceReminders.checkAndSchedule(context) }
            }
            widgetScope.launch {
                if (AppWidgetManager.getInstance(context).getAppWidgetIds(
                        ComponentName(context, TyrePingWidget::class.java)).isNotEmpty()) FleetHealth.snapshot()
                combine(FleetHealth.values, deps.toolsVehicles.currentVehicle().asFlow(), monitoring,
                    units.pressure, units.temperature) { fleet, _, _, _, _ -> fleet }
                    .collect { renderWidgets(context, it) }
            }
        }
    }
}

private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private fun renderWidgets(context: Context, fleet: List<VehicleHealth>) {
    val manager = AppWidgetManager.getInstance(context)
    val ids = manager.getAppWidgetIds(ComponentName(context, TyrePingWidget::class.java))
    if (ids.isEmpty()) return
    val vehicle = (appGraph as VehicleToolsDependencies).toolsVehicles.currentVehicle().execute()
    val health = fleet.firstOrNull { it.vehicle.uuid == vehicle.uuid }
    val units = (appGraph as WidgetDependencies).widgetUnits
    val time = DateTimeFormatter.ofPattern("dd MMM HH:mm").withZone(ZoneId.systemDefault())
    val views = RemoteViews(context.packageName, R.layout.tyreping_widget)
    views.setTextViewText(R.id.widget_title, vehicle.name)
    views.setTextViewText(R.id.widget_status, (FleetHealth.scanNotice.value ?: RideRuntime.status.value) +
        (if (RideRuntime.parked.value) " · Parked" else "") + " · snapshot")
    views.setTextViewText(R.id.widget_readings, health?.wheels?.joinToString("\n") { wheel ->
        wheel.label + ": " + (wheel.reading?.let {
            "${it.pressure.string(units.pressure.value)} · ${it.temperature.string(units.temperature.value)}" +
                "\n  ${time.format(Instant.ofEpochMilli((it.timestamp * MILLIS_PER_SECOND).toLong()))}" +
                if (wheel.warnings.isNotEmpty()) " · ⚠" else ""
        } ?: "No reading")
    } ?: "Open TyrePing to receive tyre readings")
    views.setTextViewText(R.id.widget_warning, health?.maintenance?.let { "Maintenance due: $it" } ?: "")
    views.setViewVisibility(R.id.widget_warning, if (health?.maintenance != null) View.VISIBLE else View.GONE)
    // The launcher advances this timer even after our process exits; a snapshot never masquerades as live data.
    views.setChronometer(R.id.widget_age, SystemClock.elapsedRealtime(), "Snapshot age: %s", true)
    val latest = health?.wheels?.mapNotNull { it.reading?.timestamp }?.minOrNull()
    views.setChronometer(R.id.widget_reading_age, SystemClock.elapsedRealtime() -
        ((now() - (latest ?: now())).coerceAtLeast(0.0) * MILLIS_PER_SECOND).toLong(),
        "Oldest reading: %s ago", latest != null)
    views.setViewVisibility(R.id.widget_reading_age, if (latest != null) View.VISIBLE else View.GONE)
    views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, WIDGET_REQUEST,
        Intent(context, RootActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
    manager.updateAppWidget(ids, views)
}

private const val MILLIS_PER_SECOND = 1000L
private const val WIDGET_REQUEST = 6001
