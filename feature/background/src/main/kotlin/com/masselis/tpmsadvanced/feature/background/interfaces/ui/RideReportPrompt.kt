package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.data.vehicle.RideReport
import com.masselis.tpmsadvanced.data.vehicle.ioc.VehicleToolsDependencies
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.RideReportsDialog

@Composable
internal fun RideReportPrompt() {
    val history = remember { (appGraph as VehicleToolsDependencies).monitoringHistory }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("RIDE_REPORT_PROMPT", Context.MODE_PRIVATE) }
    val completed by history.completedReport.collectAsState()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var latest by remember { mutableStateOf<RideReport?>(null) }
    var reports by remember { mutableStateOf(false) }
    LaunchedEffect(completed, lifecycle) {
        if (lifecycle.isAtLeast(Lifecycle.State.RESUMED)) latest = history.rides(0).firstOrNull { it.ended != null }
            ?.takeUnless { it.id == prefs.getString("seen", null) }
    }
    fun acknowledge() { latest?.let { prefs.edit().putString("seen", it.id).apply() }; latest = null }
    latest?.let { ride -> AlertDialog(onDismissRequest = ::acknowledge,
        title = { Text("Ride report ready") },
        text = { Text("${ride.vehicleName}: ${ride.reason}. " +
            "Review pressures, temperatures, missing readings and alerts.") },
        confirmButton = { TextButton(onClick = { acknowledge(); reports = true }) { Text("View reports") } },
        dismissButton = { TextButton(onClick = ::acknowledge) { Text("Later") } }) }
    if (reports) RideReportsDialog { reports = false }
}
