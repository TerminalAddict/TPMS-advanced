package com.masselis.tpmsadvanced.interfaces.composable

import androidx.compose.runtime.Composable
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.Preconditions
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.MonitoringRecoveryHost
import java.util.UUID

@Composable
internal fun Main(expectedVehicle: UUID?) {
    Preconditions {
        Home(expectedVehicle)
    }
    MonitoringRecoveryHost()
}
