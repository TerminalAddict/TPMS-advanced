package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.masselis.tpmsadvanced.feature.background.interfaces.MonitorService
import com.masselis.tpmsadvanced.feature.background.interfaces.MonitoringInterruption
import com.masselis.tpmsadvanced.feature.background.interfaces.MonitoringRecovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Outside the main screen's permission gate, so revoked permissions cannot hide recovery. */
@Composable
public fun MonitoringRecoveryHost() {
    val context = LocalContext.current
    val recovery = remember { MonitoringRecovery(context.applicationContext) }
    val running by MonitorService.isRunning.collectAsState()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var interruption by remember { mutableStateOf<MonitoringInterruption?>(null) }
    LaunchedEffect(running, lifecycle) {
        interruption = null
        if (!running && lifecycle.isAtLeast(Lifecycle.State.RESUMED)) {
            // Give Android's sticky automatic-watch restart a chance to arrive before showing recovery.
            delay(RECOVERY_GRACE_MS)
            val candidate = withContext(Dispatchers.IO) { recovery.check() }
            if (!MonitorService.isRunning.value) interruption = candidate
        }
    }
    interruption?.let { stopped ->
        MonitoringRecoveryDialog(stopped, recovery) {
            recovery.keepStopped(stopped.session)
            interruption = null
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Suppress("LongMethod")
@Composable
private fun MonitoringRecoveryDialog(
    interruption: MonitoringInterruption,
    recovery: MonitoringRecovery,
    keepStopped: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var error by remember(interruption) { mutableStateOf<String?>(null) }
    var starting by remember(interruption) { mutableStateOf(false) }
    val permissions = rememberMultiplePermissionsState(ridePermissions())
    val background = if (interruption.session.automatic &&
        Build.VERSION.SDK_INT in Build.VERSION_CODES.Q..Build.VERSION_CODES.R)
        rememberPermissionState(Manifest.permission.ACCESS_BACKGROUND_LOCATION) else null
    val allowed = permissions.allPermissionsGranted && (background == null || background.status.isGranted)
    AlertDialog(
        onDismissRequest = { if (!starting) keepStopped() },
        title = { Text("Background monitoring stopped") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                RecoveryDetails(interruption, onSettingsError = { error = it })
                if (!permissions.allPermissionsGranted) {
                    TextButton(onClick = { permissions.launchMultiplePermissionRequest() }) {
                        Text("Allow required permissions")
                    }
                } else if (background != null && !background.status.isGranted) BackgroundLocationSetup(background)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = allowed && !starting, onClick = {
                scope.launch {
                    starting = true
                    error = recovery.restore(interruption.session)
                    if (error == null) {
                        delay(START_CONFIRM_MS)
                        if (!MonitorService.isRunning.value)
                            error = "Monitoring did not start. Check permissions in App settings, then try again."
                    }
                    starting = false
                }
            }) { Text(if (starting) "Starting…" else "Restart monitoring") }
        },
        dismissButton = {
            TextButton(enabled = !starting, onClick = keepStopped) { Text("Keep stopped") }
        }
    )
}

@Composable
private fun RecoveryDetails(interruption: MonitoringInterruption, onSettingsError: (String) -> Unit) {
    val context = LocalContext.current
    val mode = if (interruption.session.automatic) "Automatic ride watch" else "Background monitoring"
    Text("$mode for ${interruption.session.vehicleName} was left on, but is no longer running.")
    Spacer(Modifier.height(8.dp))
    Text(interruption.cause.explanation)
    interruption.stoppedAt?.let {
        Text("Stopped: ${DateFormat.getDateTimeInstance().format(Date(it))}")
    }
    Spacer(Modifier.height(8.dp))
    Text("Background tyre alerts are unavailable until you restart monitoring.")
    if (interruption.session.automatic)
        Text("Restart will resume watching for the saved accessory or beacon.")
    Text("If this happens repeatedly, check App settings and allow background activity or " +
        "set battery use to Unrestricted. The names vary by phone.")
    TextButton(onClick = {
        try {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:${context.packageName}".toUri()))
        } catch (_: ActivityNotFoundException) {
            onSettingsError("Open Android Settings, then Apps, then TyrePing to check battery use and permissions.")
        }
    }) { Text("App settings") }
}

private const val RECOVERY_GRACE_MS = 1_000L
private const val START_CONFIRM_MS = 2_000L

public fun stopMonitoringForRestore(context: android.content.Context): Unit = MonitorService.stop(context)
