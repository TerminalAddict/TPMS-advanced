package com.masselis.tpmsadvanced.interfaces

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.content.edit
import androidx.core.net.toUri
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.data.vehicle.ioc.VehicleToolsDependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Inexact daily reminders tolerate Doze and never require an exact-alarm permission. */
public class MaintenanceReminders : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != ACTION_CHECK) return
        val pending = goAsync()
        scope.launch { try { checkAndSchedule(context) } finally { pending.finish() } }
    }

    public companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        internal fun checkAndSchedule(context: Context) {
            val deps = appGraph as VehicleToolsDependencies
            val manager = NotificationManagerCompat.from(context)
            manager.createNotificationChannel(NotificationChannelCompat.Builder("MAINTENANCE",
                NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Tyre maintenance reminders").build())
            val saved = context.getSharedPreferences("MAINTENANCE_NOTIFIED", Context.MODE_PRIVATE)
            val tools = deps.rideTools.read()
            val vehicles = deps.toolsVehicles.selectAll().execute()
            val today = LocalDate.now()
            val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            vehicles.forEach { vehicle ->
                val reminder = tools.vehicles[vehicle.uuid.toString()]?.reminder
                val id = vehicle.uuid.hashCode() xor NOTIFICATION_MASK
                if (reminder?.isDue(today) == true && canNotify) {
                    val token = "${reminder.label}:${reminder.dueDate}:${reminder.dueKm}"
                    if (saved.getString(vehicle.uuid.toString(), null) != token) {
                        manager.notify(id, NotificationCompat.Builder(context, "MAINTENANCE")
                            .setSmallIcon(com.masselis.tpmsadvanced.feature.background.R.drawable.car_tire)
                            .setContentTitle("${vehicle.name}: ${reminder.label}")
                            .setContentText("Tyre maintenance is due. Open Settings → Tyre maintenance and reminders.")
                            .setAutoCancel(true).setContentIntent(PendingIntent.getActivity(context, id,
                                Intent(context,
                                    RootActivity::class.java).setData("tpmsadvanced://vehicle/${vehicle.uuid}".toUri()),
                                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).build())
                        saved.edit { putString(vehicle.uuid.toString(), token) }
                    }
                } else if (reminder?.isDue(today) != true) {
                    manager.cancel(id)
                    saved.edit { remove(vehicle.uuid.toString()) }
                }
            }
            val alarm = context.getSystemService<AlarmManager>() ?: return
            val intent = PendingIntent.getBroadcast(context, REMINDER_REQUEST, Intent(context,
                MaintenanceReminders::class.java).setAction(ACTION_CHECK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            alarm.cancel(intent)
            if (vehicles.any { tools.vehicles[it.uuid.toString()]?.reminder?.enabled == true }) {
                alarm.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis() + AlarmManager.INTERVAL_DAY,
                    AlarmManager.INTERVAL_DAY, intent)
            }
        }
        private const val NOTIFICATION_MASK = 0x50400000
        private const val REMINDER_REQUEST = 6040
        private const val ACTION_CHECK = "com.masselis.tpmsadvanced.MAINTENANCE_CHECK"
    }
}
