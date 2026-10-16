package com.masselis.tpmsadvanced.feature.androidauto.endpoint.ui.screen

import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.feature.androidauto.endpoint.ui.viewmodel.TabScreenViewModel.State
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.appendLoc
import com.masselis.tpmsadvanced.feature.main.usecase.FleetHealth
import com.masselis.tpmsadvanced.feature.main.usecase.RideRuntime
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow
import com.masselis.tpmsadvanced.feature.main.usecase.VehicleHealth

/** Stable row titles/counts allow value refreshes without consuming the host's navigation quota. */
context(screen: Screen)
@Suppress("FunctionName", "FunctionNaming", "MagicNumber")
internal fun VehicleTemplate(tab: State.Tabs.Tab.Displayed, rowLimit: Int = carListLimit()): ListTemplate {
    val limit = rowLimit.coerceAtLeast(1)
    val health = FleetHealth.values.value.firstOrNull { it.vehicle.uuid == tab.vehicle.uuid }
    val hidden = (tab.vehicle.kind.locations.size - (limit - 1)).coerceAtLeast(0)
    val warning = autoHealth(health)
    val others = FleetHealth.values.value.count { it.vehicle.uuid != tab.vehicle.uuid && it.warning }
    val rows = ItemList.Builder().addItem(Row.Builder().setTitle("Vehicle status")
        .addText(warning + if (others > 0) " · $others other vehicles warning" else "")
        .addText((FleetHealth.scanNotice.value ?: RideRuntime.status.value) +
            if (hidden > 0) " · $hidden tyres available on phone" else "").build())
    tab.vehicle.kind.locations.take(limit - 1).forEach { location ->
        val state = tab.tyres[location]?.second ?: TyreStatsStateFlow.State.NotDetected
        val wheel = health?.wheels?.firstOrNull { it.location == location }
        rows.addItem(Row.Builder().setTitle(buildString { appendLoc(location, false, capitalized = true) })
            .addText(autoReading(state))
            .addText(autoFreshness(state, now()) + (wheel?.warnings?.firstOrNull()?.let { " · $it" } ?: "")).build())
    }
    // Header intentionally absent: the same list is valid inside TabContents.
    return ListTemplate.Builder().setSingleList(rows.build()).build()
}

context(screen: Screen)
@Suppress("MagicNumber")
private fun carListLimit(): Int = runCatching {
    screen.carContext.getCarService(ConstraintManager::class.java)
        .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
}.getOrDefault(6)

private fun autoHealth(health: VehicleHealth?): String = when {
    health == null -> "Waiting for sensor health"
    health.warning -> "WARNING · " + (health.imbalances.firstOrNull()?.let { "${it.axle} pressure imbalance" }
        ?: "Review tyre or maintenance warnings")
    health.ready -> "All sensors recently healthy"
    else -> "Sensors sleeping, stale or not received"
}

internal fun autoReading(state: TyreStatsStateFlow.State): String = when (state) {
    TyreStatsStateFlow.State.NotDetected -> "Sensor not detected"
    is TyreStatsStateFlow.State.Normal ->
        "${state.pressure.string(state.pressureUnit)} · ${state.temperature.string(state.temperatureUnit)}"
    is TyreStatsStateFlow.State.Alerting ->
        "WARNING · ${state.pressure.string(state.pressureUnit)} · ${state.temperature.string(state.temperatureUnit)}"
}

@Suppress("MagicNumber")
internal fun autoFreshness(state: TyreStatsStateFlow.State, currentTime: Double): String {
    val timestamp = when (state) {
        TyreStatsStateFlow.State.NotDetected -> return "Assign or wake sensor while parked"
        is TyreStatsStateFlow.State.Normal -> state.timestamp
        is TyreStatsStateFlow.State.Alerting -> state.timestamp
    }
    val age = currentTime - timestamp
    return when {
        timestamp <= 0 || age < 0 -> "Freshness unconfirmed"
        age > 120 -> "STALE · last seen ${(age / 60).toInt()} min ago"
        else -> "Last seen ${age.toInt()}s ago"
    }
}
