package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.feature.main.R
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent

@Composable
internal fun BindSensorButton(
    location: Location,
    modifier: Modifier = Modifier,
    vehicleComponent: VehicleComponent = LocalVehicleComponent.current,
) {
    var manage by remember { mutableStateOf(false) }
    IconButton(onClick = { manage = true }, modifier = modifier.testTag(BindSensorTags.Button.tag(location))) {
        Icon(ImageVector.vectorResource(R.drawable.link_variant_plus), contentDescription = "Assign or manage sensor")
    }
    if (manage) SensorManagementDialog(vehicleComponent.vehicle, location) { manage = false }
}

@Suppress("ConstPropertyName")
internal object BindSensorTags {
    object Button { fun tag(location: Location): String = "bindSensorButton_${location}" }
    object Dialog {
        const val root = "SensorManagement"
        const val manualId = "SensorManagement_manualId"
        const val assign = "SensorManagement_assign"
        const val apply = "SensorManagement_apply"
        const val done = "SensorManagement_done"
    }
}
