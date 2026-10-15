package com.masselis.tpmsadvanced.feature.background.interfaces

import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.content.getSystemService
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.core.common.observableStateFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

internal object WaitingScanPower {
    val saving by lazy {
        val preferences = appContext.getSharedPreferences("WAITING_POWER", Context.MODE_PRIVATE)
        observableStateFlow(preferences.getBoolean("saving", true)) { _, value ->
            preferences.edit { putBoolean("saving", value) }
        }
    }
}

internal fun waitingScanMode(ride: Boolean, saving: Boolean, moving: Boolean?, inUse: Boolean): Int = when {
    ride || !saving || inUse || moving == true -> ScanSettings.SCAN_MODE_LOW_LATENCY
    moving == null -> ScanSettings.SCAN_MODE_BALANCED
    else -> ScanSettings.SCAN_MODE_LOW_POWER
}

/** Never pauses a scan. One motion listener and one power receiver exist only while a beacon watch runs. */
internal fun Context.waitingScanModes(): Flow<Int> {
    val motion = callbackFlow<Long?> {
        val manager = getSystemService<SensorManager>()
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
        @Suppress("TooGenericExceptionCaught") // Unusable optional motion hardware falls back to balanced scanning.
        fun arm(listener: TriggerEventListener): Boolean = try {
            sensor != null && manager?.requestTriggerSensor(listener, sensor) == true
        } catch (_: RuntimeException) {
            false // Some devices expose an unusable sensor. Keep scanning in balanced mode.
        }
        val listener = object : TriggerEventListener() {
            override fun onTrigger(event: TriggerEvent?) {
                trySend(SystemClock.elapsedRealtime())
                if (!arm(this)) trySend(null)
            }
        }
        if (arm(listener)) send(SystemClock.elapsedRealtime())
        else send(null)
        awaitClose { if (sensor != null) manager?.cancelTriggerSensor(listener, sensor) }
    }
    val inUse = callbackFlow {
        fun active(): Boolean = getSystemService<PowerManager>()?.isInteractive == true ||
            getSystemService<BatteryManager>()?.isCharging == true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { trySend(active()) }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_POWER_CONNECTED); addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        ContextCompat.registerReceiver(this@waitingScanModes, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        send(active())
        awaitClose { unregisterReceiver(receiver) }
    }
    val clock = flow { while (true) { emit(SystemClock.elapsedRealtime()); delay(POWER_CHECK_MS) } }
    val recentMotion = combine(motion, clock) { last, now -> last?.let { now - it < MOTION_GRACE_MS } }
    return combine(recentMotion, inUse, WaitingScanPower.saving, MonitorService.readingsActive) { moving, active,
        saving, ride ->
        waitingScanMode(ride, saving, moving, active)
    }.distinctUntilChanged()
}

private const val POWER_CHECK_MS = 30_000L
private const val MOTION_GRACE_MS = 300_000L
