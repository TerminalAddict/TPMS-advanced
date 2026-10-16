package com.masselis.tpmsadvanced.interfaces

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.masselis.tpmsadvanced.core.ui.LocalReadability
import com.masselis.tpmsadvanced.core.ui.Readability
import androidx.savedstate.serialization.saved
import com.masselis.tpmsadvanced.core.ui.LocalKeepScreenOnCounter
import com.masselis.tpmsadvanced.core.ui.ScreenOnCounter
import com.masselis.tpmsadvanced.interfaces.composable.Main
import com.masselis.tpmsadvanced.interfaces.composable.TpmsAdvancedTheme
import android.content.Intent
import com.masselis.tpmsadvanced.data.vehicle.model.vehicleLinkId
import androidx.lifecycle.lifecycleScope
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.data.vehicle.ioc.VehicleToolsDependencies
import com.masselis.tpmsadvanced.feature.main.usecase.RideRuntime
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock

public class RootActivity : AppCompatActivity() {

    private val counter = ScreenOnCounter.Activity()

    private var hasConsumedIntent by saved { false }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!hasConsumedIntent) {
            selectVehicle(intent)
            hasConsumedIntent = true
        }

        setContent {
            val tools by (appGraph as VehicleToolsDependencies).rideTools.values.collectAsState()
            CompositionLocalProvider(LocalKeepScreenOnCounter provides counter,
                LocalReadability provides Readability(tools.insights.highVisibility, tools.insights.readoutScale,
                    tools.insights.readingCards)) {
                TpmsAdvancedTheme {
                    Main(null)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        selectVehicle(intent)
    }

    private fun selectVehicle(intent: Intent?) {
        vehicleLinkId(intent?.dataString)?.let { id -> lifecycleScope.launch {
            val database = (appGraph as VehicleToolsDependencies).toolsVehicles
            val exists = withContext(Dispatchers.IO) { database.selectAll().execute().any { it.uuid == id } }
            if (exists) {
                RideRuntime.manualSelection.value = true
                RideRuntime.selectionMutex.withLock { database.setIsCurrent(id, true) }
            } else android.widget.Toast.makeText(this@RootActivity,
                "This vehicle is not in TyrePing. Restore its backup first.",
                android.widget.Toast.LENGTH_LONG).show()
        } }
    }
}
