package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.feature.main.ioc.ReadingSettingsDependencies

@Composable
public fun ReadingSettings() {
    val preferences = (appGraph as ReadingSettingsDependencies).readingPreferences
    val enabled by preferences.detectLeaks.collectAsState()
    Column {
        val tools = (appGraph as com.masselis.tpmsadvanced.data.vehicle.ioc.VehicleToolsDependencies).rideTools
        val data by tools.values.collectAsState()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("High-visibility mode", Modifier.weight(1f))
            Switch(data.insights.highVisibility, { value -> tools.update {
                it.copy(insights = it.insights.copy(highVisibility = value))
            } })
        }
        Text("High contrast and steady warning text. System font sizing is respected.")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Use reading cards in portrait", Modifier.weight(1f))
            Switch(data.insights.readingCards, { value -> tools.update {
                it.copy(insights = it.insights.copy(readingCards = value))
            } })
        }
        Text("Replace the vehicle diagram with large, scrollable cards. Useful with very large text. " +
            "Landscape, combined towing, car with spare and monowheel views always use cards.")
        Text("Readout size")
        androidx.compose.foundation.layout.FlowRow {
            listOf(1f, 1.25f, 1.5f, 2f).forEach { scale ->
                androidx.compose.material3.FilterChip(data.insights.readoutScale == scale, {
                    tools.update { it.copy(insights = it.insights.copy(readoutScale = scale)) }
                }, { Text("${(scale * 100).toInt()}%") })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Experimental leak detection", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { preferences.detectLeaks.value = it })
        }
        Text("Warn about repeated pressure loss while readings are live. Cooling and long gaps reset the trend. " +
            "This experiment can miss leaks or warn unnecessarily; keep your normal pressure alerts enabled.")
    }
}
