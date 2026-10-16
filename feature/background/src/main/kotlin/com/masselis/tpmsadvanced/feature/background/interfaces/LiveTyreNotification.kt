@file:Suppress("MagicNumber")

package com.masselis.tpmsadvanced.feature.background.interfaces

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.data.vehicle.ioc.VehicleToolsDependencies
import com.masselis.tpmsadvanced.feature.background.R
import com.masselis.tpmsadvanced.feature.main.usecase.FleetHealth
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@SuppressLint("MissingPermission")
internal suspend fun liveTyreNotification(): Unit = coroutineScope {
    val deps = appGraph as VehicleToolsDependencies
    val manager = NotificationManagerCompat.from(appContext)
    val channel = "LIVE_TYRE_STATUS"
    val id = 414110
    manager.createNotificationChannel(NotificationChannelCompat.Builder(channel,
        NotificationManagerCompat.IMPORTANCE_LOW)
        .setName("Live tyre status").build())
    try {
        while (isActive) {
            if (deps.rideTools.read().monitoring.liveNotification && MonitorService.isRunning.value) {
                val health = FleetHealth.values.value.firstOrNull {
                    it.vehicle.uuid == MonitorService.activeVehicle.value }
                val maintenance = health?.let { deps.maintenanceMode.remainingMinutes(it.vehicle.uuid) } ?: 0
                val rows = health?.wheels?.joinToString("\n") { wheel ->
                    val reading = wheel.reading
                    "${wheel.label}: " + if (reading == null) "waiting for sensor" else
                        "${reading.pressure.string(deps.toolsUnits.pressure.value)} · " +
                        "${reading.temperature.string(deps.toolsUnits.temperature.value)} · " +
                        readingAge(reading.timestamp, now())
                } ?: "Waiting for readings"
                val state = monitoringTileLabel(true, MonitorService.readingsActive.value,
                    deps.toolsScanner.scanStatus.value)
                val text = "$state · ${health?.vehicle?.name ?: "TyrePing"}" +
                    if (maintenance > 0) " · Maintenance $maintenance min" else ""
                val intent = Intent(appContext, MonitoringControlsActivity::class.java)
                manager.notify(id, NotificationCompat.Builder(appContext, channel).setSmallIcon(R.drawable.car_tire)
                    .setContentTitle(text).setContentText(rows.replace('\n', ' '))
                    .setStyle(NotificationCompat.BigTextStyle().bigText("${MonitorService.status.value}\n$rows"))
                    .setContentIntent(PendingIntent.getActivity(appContext, id, intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                    .setOngoing(true).setSilent(true).setOnlyAlertOnce(true).setShowWhen(false).build())
            } else manager.cancel(id)
            delay(5000)
        }
    } finally { manager.cancel(id) }
}

internal fun readingAge(timestamp: Double, time: Double): String {
    val age = time - timestamp
    return when {
        age < 0 -> "time unavailable"
        age < 60 -> "${age.toInt()}s ago"
        age <= 120 -> "${(age / 60).toInt()}m ago"
        else -> "stale · ${(age / 60).toInt()}m ago"
    }
}
