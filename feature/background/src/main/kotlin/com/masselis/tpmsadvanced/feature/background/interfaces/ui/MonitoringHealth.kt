@file:Suppress("LongMethod", "MagicNumber", "MissingPermission")

package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.location.LocationManager
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.data.vehicle.ioc.VehicleToolsDependencies
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanStatus
import com.masselis.tpmsadvanced.data.vehicle.model.WheelReadiness
import com.masselis.tpmsadvanced.feature.background.R
import com.masselis.tpmsadvanced.feature.background.interfaces.AlertPlayer
import com.masselis.tpmsadvanced.feature.background.interfaces.AlertCue
import com.masselis.tpmsadvanced.feature.background.interfaces.AlertControls
import com.masselis.tpmsadvanced.feature.background.interfaces.MonitorService
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.RideToolsDialog
import com.masselis.tpmsadvanced.feature.main.usecase.FleetHealth
import com.masselis.tpmsadvanced.feature.main.usecase.RideRuntime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
public fun MonitoringHealthButton() {
    var show by remember { mutableStateOf(false) }
    TextButton(onClick = { show = true }) { Text("Monitoring health check / self-test") }
    if (show) MonitoringHealth { show = false }
}

@Composable
private fun MonitoringHealth(close: () -> Unit) {
    val context = LocalContext.current
    val deps = remember { appGraph as VehicleToolsDependencies }
    val manager = remember { NotificationManagerCompat.from(context) }
    val scope = rememberCoroutineScope()
    var checks by remember { mutableStateOf(emptyList<String>()) }
    var result by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var testJob by remember { mutableStateOf<Job?>(null) }
    var sent by remember { mutableStateOf(false) }
    val player = remember { AlertPlayer(context) }
    DisposableEffect(Unit) { onDispose { testJob?.cancel(); player.close(); manager.cancel(TEST_ID) } }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        result = "Permission request finished. Run checks again."
    }
    fun check() { scope.launch {
        running = true
        try {
            checks = monitoringChecks(context, deps, manager)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: RuntimeException) {
            result = "Could not finish checks. Reopen this screen and check app permissions."
        }
        finally { running = false }
    } }
    LaunchedEffect(Unit) { check() }
    RideToolsDialog("Monitoring health check", close) {
        Text("Checks report this moment. Only a ride with the screen off can confirm sustained background operation.")
        Button(onClick = ::check, enabled = !running) { Text(if (running) "Checking…" else "Run all checks") }
        checks.forEach { Text(it) }
        TextButton(onClick = {
            val requested = deps.toolsScanner.missingPermission().toMutableList()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                requested += Manifest.permission.POST_NOTIFICATIONS
            permissions.launch(requested.distinct().toTypedArray())
        }) { Text("Request required permissions") }
        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:${context.packageName}".toUri())) }) { Text("Open Android app settings") }
        Text("Test alert", style = MaterialTheme.typography.titleMedium)
        Text("Uses your current sound and vibration settings and alarm volume. Silence and active tyre alerts take " +
            "priority. A notification is also sent. Delivery needs your confirmation below.")
        Button(enabled = testJob?.isActive != true, onClick = {
            sent = false
            if (testShouldWait()) {
                result = "Test postponed: a tyre alert is active or alerts are silenced."
            } else {
                testJob = scope.launch {
                    val delivered = runAlertTest(context, manager, player)
                    sent = true
                    result = if (delivered) "Test requested. Confirm what you received; " +
                        "playback is not automatically verified."
                        else "Notifications blocked. Sound / vibration attempted with your settings; confirm delivery."
                }
            }
        }) { Text("Send test alert") }
        if (testJob?.isActive == true) TextButton(onClick = { testJob?.cancel(); result = "Test stopped." }) {
            Text("Stop test")
        }
        if (sent) {
            TextButton(onClick = { result = "Confirmed by you: test received." }) { Text("I received the test") }
            TextButton(onClick = { result = "Test not received. Check alert settings, alarm volume, Do Not Disturb, " +
                "notification channels and vibration support, then retry." }) { Text("I did not receive it") }
        }
        Text(result)
    }
}

private const val TEST_ID = 31836
private const val TEST_CHANNEL = "MONITORING_SELF_TEST"

private fun testShouldWait(): Boolean = AlertControls.active.value || AlertControls.fleetActive.value ||
    SystemClock.elapsedRealtime() < AlertControls.silencedUntil.value

private suspend fun monitoringChecks(context: android.content.Context, deps: VehicleToolsDependencies,
    manager: NotificationManagerCompat): List<String> {
    val fleet = FleetHealth.snapshot()
    val missing = deps.toolsScanner.missingPermission()
    val enabled = runCatching { context.getSystemService<BluetoothManager>()?.adapter?.isEnabled == true }
        .getOrDefault(false)
    return buildList {
        add(if (missing.isEmpty()) "PASS · Bluetooth permissions granted" else
            "ACTION · Missing permissions: ${missing.joinToString { it.substringAfterLast('.') }}")
        add(if (enabled) "PASS · Bluetooth enabled" else "ACTION · Bluetooth unavailable or disabled")
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) add(
            if (context.getSystemService<LocationManager>()?.isLocationEnabled == true) "PASS · Location enabled"
            else "ACTION · Android 9–11 require Location enabled for BLE scanning")
        add(when (deps.toolsScanner.scanStatus.value) {
            ScanStatus.Scanning -> "PASS · Shared BLE scanner running"
            else -> "ACTION · Scanner: ${deps.toolsScanner.scanStatus.value.javaClass.simpleName}"
        })
        add(if (MonitorService.isRunning.value) "PASS · Background service running: ${RideRuntime.status.value}"
            else "ACTION · Background monitoring stopped; start it with the dashboard monitoring button")
        add(if (manager.areNotificationsEnabled()) "PASS · App notifications allowed"
            else "ACTION · App notifications disabled")
        manager.notificationChannels.filter { it.importance == 0 }.forEach {
            add("ACTION · Notification channel blocked: ${it.name}")
        }
        addAll(alertDeliveryChecks(context))
        fleet.forEach { health -> health.wheels.forEach { wheel ->
            add("${if (wheel.state == WheelReadiness.HEALTHY) "PASS" else "CHECK"} · " +
                "${health.vehicle.name}, ${wheel.label}: ${wheel.state.name.lowercase()}")
        } }
        if (fleet.isEmpty()) add("CHECK · No vehicle readings available")
    }
}

private fun alertDeliveryChecks(context: android.content.Context): List<String> = buildList {
        val audio = context.getSystemService<AudioManager>()
        add("INFO · Alarm volume ${audio?.getStreamVolume(AudioManager.STREAM_ALARM) ?: "unknown"}; " +
            "sound ${AlertControls.options.value.sound}, vibration ${AlertControls.options.value.vibrate}")
        val interruption = context.getSystemService<android.app.NotificationManager>()?.currentInterruptionFilter
        add("INFO · Do Not Disturb filter: $interruption")
        add(if (context.getSystemService<PowerManager>()?.isIgnoringBatteryOptimizations(context.packageName) == true)
            "PASS · Battery optimisation exemption enabled" else "INFO · Android battery optimisation applies")
}

private suspend fun runAlertTest(context: android.content.Context, manager: NotificationManagerCompat,
    player: AlertPlayer): Boolean = kotlinx.coroutines.coroutineScope {
    manager.createNotificationChannel(NotificationChannelCompat.Builder(TEST_CHANNEL,
        NotificationManagerCompat.IMPORTANCE_DEFAULT).setName("Monitoring self-test")
        .setSound(null, null).setVibrationEnabled(false).build())
    val notificationSent = manager.areNotificationsEnabled() &&
        manager.getNotificationChannel(TEST_CHANNEL)?.importance != 0
    val delivered = notificationSent && runCatching { manager.notify(TEST_ID,
        NotificationCompat.Builder(context, TEST_CHANNEL)
        .setSmallIcon(R.drawable.car_tire_alert).setContentTitle("TyrePing test alert")
        .setContentText("Monitoring self-test. Confirm delivery in TyrePing.")
        .setSilent(true).setAutoCancel(true).build()) }.isSuccess
    val playback = launch { player.play(listOf(AlertCue("self-test", "TyrePing test alert",
        true, now())), AlertControls.options.value) }
    while (playback.isActive) {
        if (testShouldWait()) playback.cancel()
        delay(100)
    }
    playback.join()
    delivered
}
