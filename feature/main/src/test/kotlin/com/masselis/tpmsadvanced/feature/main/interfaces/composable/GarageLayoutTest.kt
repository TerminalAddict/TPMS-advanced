package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.masselis.tpmsadvanced.core.test.MainDispatcherRule
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBattery
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.data.vehicle.model.TyreAtmosphere
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.WheelHealth
import com.masselis.tpmsadvanced.data.vehicle.model.WheelReadiness
import com.masselis.tpmsadvanced.feature.main.usecase.VehicleHealth
import org.junit.Rule
import org.junit.Test
import java.util.UUID

internal class GarageLayoutTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig(screenWidth = 360, screenHeight = 920,
        density = com.android.resources.Density.MEDIUM, softButtons = false),
        theme = "android:Theme.Material3.DayNight.NoActionBar")
    @Test fun garageWarningsWithLargeFont() {
        val vehicle = Vehicle(UUID.randomUUID(), Vehicle.Kind.MOTORCYCLE, "Touring motorcycle", Pressure(180f),
            Pressure(350f), false, Pressure(180f), Pressure(350f), Temperature(-20f), Temperature(45f),
            Temperature(90f), null)
        val health = VehicleHealth(vehicle, listOf(
            WheelHealth(Location.Axle.FRONT, TyreAtmosphere(100.0, 1, Pressure(0f), Temperature(22f),
                SensorBattery.Voltage(2.4f)), WheelReadiness.WARNING,
                listOf("Pressure outside limits", "Low sensor battery")),
            WheelHealth(Location.Axle.REAR, null, WheelReadiness.UNSEEN, emptyList())), null)
        paparazzi.snapshot {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.4f)) {
                MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                        GarageVehicleCard(health, 130.0, PressureUnit.PSI, TemperatureUnit.CELSIUS, true, {}, {})
                    }
                } }
            }
        }
    }
}
