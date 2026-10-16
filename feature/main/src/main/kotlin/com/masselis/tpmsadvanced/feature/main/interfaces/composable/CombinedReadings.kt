@file:Suppress("MagicNumber", "LongMethod")

package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masselis.tpmsadvanced.core.ui.LocalReadability
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.WheelReadiness
import com.masselis.tpmsadvanced.feature.main.usecase.VehicleHealth

@Composable
internal fun CombinedReadings(fleet: List<VehicleHealth>, pressureUnit: PressureUnit, temperatureUnit: TemperatureUnit,
    time: Double, modifier: Modifier = Modifier, onWheel: (Vehicle, Location) -> Unit = { _, _ -> }) {
    val readability = LocalReadability.current
    val scale = LocalDensity.current.fontScale * readability.scale
    BoxWithConstraints(modifier) {
        val columns = if (maxWidth >= 320.dp * scale) 2 else 1
        LazyVerticalGrid(GridCells.Fixed(columns), contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            fleet.forEach { health ->
                item(key = health.vehicle.uuid.toString(), span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Text(health.vehicle.name, style = MaterialTheme.typography.titleLarge)
                        Text("Limits: ${health.vehicle.lowPressure.string(pressureUnit)}–" +
                            health.vehicle.highPressure.string(pressureUnit),
                            style = MaterialTheme.typography.bodySmall)
                        if (health.vehicle.separateFrontRearPressure) Text("Rear: " +
                            "${health.vehicle.rearLowPressure.string(pressureUnit)}–" +
                            health.vehicle.rearHighPressure.string(pressureUnit))
                        health.imbalances.forEach { Text("${it.axle} pressure imbalance",
                            color = MaterialTheme.colorScheme.error) }
                        health.maintenance?.let { Text("Maintenance: $it") }
                    }
                }
                items(health.wheels, key = { "${health.vehicle.uuid}:${it.location}" }) { wheel ->
                    Card(onClick = { onWheel(health.vehicle, wheel.location) }) {
                        Column(Modifier.fillMaxWidth().padding(10.dp).semantics(mergeDescendants = true) {}) {
                            Text(wheel.label, style = MaterialTheme.typography.titleSmall)
                            Text(wheel.reading?.pressure?.string(pressureUnit) ?: "No reading",
                                fontSize = 24.sp * readability.scale)
                            Text(wheel.reading?.temperature?.string(temperatureUnit) ?: "Assign or wake sensor")
                            Text(combinedReadinessLabel(wheel.state),
                                color = if (wheel.warnings.isEmpty()) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.error)
                            wheel.reading?.let {
                                Text("${((time - it.timestamp).coerceAtLeast(0.0)).toInt()}s ago",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            wheel.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
}

private fun combinedReadinessLabel(state: WheelReadiness): String = when (state) {
    WheelReadiness.HEALTHY -> "Recently healthy"
    WheelReadiness.UNSEEN -> "Not received"
    WheelReadiness.SLEEPING -> "STALE / sleeping"
    WheelReadiness.WARNING -> "WARNING"
    WheelReadiness.MISSING -> "MISSING during ride"
}
