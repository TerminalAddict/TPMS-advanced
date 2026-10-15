package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.masselis.tpmsadvanced.feature.background.interfaces.AlertControls
import com.masselis.tpmsadvanced.feature.background.interfaces.AlertSound
import com.masselis.tpmsadvanced.feature.background.interfaces.MonitorService
import com.masselis.tpmsadvanced.feature.background.interfaces.audibleAlerts
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import kotlinx.coroutines.delay

@Composable
public fun AlertsSettings() {
    val options by AlertControls.options.collectAsState()
    Column {
        Text("Audible and vibration alerts", style = MaterialTheme.typography.titleMedium)
        Text("Optional repeating alerts while monitoring. Alarm volume controls loudness. " +
            "Sound and vibration are off until enabled here.")
        Row {
            AlertSound.entries.forEach { sound ->
                TextButton(onClick = { AlertControls.options.value = options.copy(sound = sound) }) {
                    Text((if (sound == options.sound) "✓ " else "") + sound.name.lowercase())
                }
            }
        }
        Row {
            Text("Vibrate")
            Switch(options.vibrate, { AlertControls.options.value = options.copy(vibrate = it) })
        }
        Text("Repeat: ${options.repeatSeconds} seconds")
        FlowRow { listOf(30, 60, 120, 300, 600).forEach { seconds ->
            TextButton(onClick = { AlertControls.options.value = options.copy(repeatSeconds = seconds) }) {
                Text("${seconds}s")
            }
        } }
        Text("Critical repeat: ${options.criticalSeconds} seconds")
        FlowRow { listOf(10, 20, 30, 60, 120).forEach { seconds ->
            TextButton(onClick = { AlertControls.options.value = options.copy(criticalSeconds = seconds) }) {
                Text("${seconds}s")
            }
        } }
        Text("Critical: pressure 25% below the minimum or 20% above the maximum, " +
            "or temperature 20°C above its limit. Critical vibration uses a longer burst. " +
            "Repeats stop after 10 minutes without a fresh reading.")
        SilenceAlertsButton()
    }
}

@Composable
public fun AudibleAlertsHost(component: VehicleComponent) {
    val running by MonitorService.isRunning.collectAsState()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    LaunchedEffect(component, running, lifecycle) {
        if (!running && lifecycle.isAtLeast(Lifecycle.State.RESUMED)) audibleAlerts(component)
    }
}

@Composable
public fun SilenceAlertsButton() {
    val active by AlertControls.active.collectAsState()
    val fleetActive by AlertControls.fleetActive.collectAsState()
    val until by AlertControls.silencedUntil.collectAsState()
    var remaining by remember { mutableLongStateOf(0L) }
    LaunchedEffect(until) {
        do {
            remaining = (until - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            delay(1_000)
        } while (remaining > 0)
    }
    if (active || fleetActive || remaining > 0) TextButton(onClick = {
        if (remaining > 0) AlertControls.silencedUntil.value = 0 else AlertControls.silence()
    }) { Text(if (remaining > 0) "Resume alerts (${remaining / 1_000}s muted)" else "Silence all alerts for 10 min") }
}
