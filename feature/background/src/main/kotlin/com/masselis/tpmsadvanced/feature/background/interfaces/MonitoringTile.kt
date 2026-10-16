package com.masselis.tpmsadvanced.feature.background.interfaces

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.data.vehicle.ioc.VehicleToolsDependencies
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

internal fun monitoringTileLabel(running: Boolean, reading: Boolean, scan: ScanStatus): String = when {
    !running -> "Stopped"
    !reading -> "Waiting for ride"
    scan != ScanStatus.Scanning -> "Suspended"
    else -> "Active"
}

/** Opening controls is deliberate: tapping a tile never silently starts a radio or ride. */
public class MonitoringTile : TileService() {
    private var listening: CoroutineScope? = null
    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        val scope = CoroutineScope(Dispatchers.Main.immediate + Job()).also { listening = it }
        scope.launch {
            combine(MonitorService.isRunning, MonitorService.readingsActive,
                (appGraph as VehicleToolsDependencies).toolsScanner.scanStatus) { running, reading, scan ->
                monitoringTileLabel(running, reading, scan)
            }.collect { label -> qsTile?.apply {
                this.label = "TyrePing"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = label
                contentDescription = "TyrePing: $label. Open monitoring controls"
                state = if (label == "Active") Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                updateTile()
            } }
        }
    }
    override fun onStopListening() { listening?.cancel(); listening = null; super.onStopListening() }
    override fun onDestroy() { listening?.cancel(); super.onDestroy() }
    @Suppress("DEPRECATION")
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MonitoringControlsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        unlockAndRun {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            else startActivityAndCollapse(intent)
        }
    }
}
