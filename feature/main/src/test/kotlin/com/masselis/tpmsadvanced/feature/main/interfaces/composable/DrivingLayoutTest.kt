package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.masselis.tpmsadvanced.core.test.MainDispatcherRule
import com.masselis.tpmsadvanced.core.ui.LocalReadability
import com.masselis.tpmsadvanced.core.ui.Readability
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.data.vehicle.model.TyreAtmosphere
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.WheelHealth
import com.masselis.tpmsadvanced.data.vehicle.model.WheelReadiness
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow.State
import com.masselis.tpmsadvanced.feature.main.usecase.VehicleHealth
import org.junit.Rule
import org.junit.Test
import java.util.UUID

internal class DrivingLayoutTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig(screenWidth = 360, screenHeight = 740,
        density = com.android.resources.Density.MEDIUM, softButtons = false),
        theme = "android:Theme.Material3.DayNight.NoActionBar")
    private fun health(kind: Vehicle.Kind, name: String, pressure: Float) = VehicleHealth(
        Vehicle(UUID.randomUUID(), kind, name, Pressure(180f), Pressure(350f), false, Pressure(180f), Pressure(350f),
            Temperature(-20f), Temperature(45f), Temperature(90f), null),
        kind.locations.map { WheelHealth(it,
            TyreAtmosphere(1000.0, it.hashCode(), Pressure(pressure), Temperature(25f)),
            WheelReadiness.HEALTHY, emptyList()) }, null)

    @Test fun combinedUteAndTandem() = paparazzi.snapshot {
        MaterialTheme { Surface(Modifier.fillMaxSize()) {
            CombinedReadings(listOf(health(Vehicle.Kind.UTE, "Tow ute", 230f),
                health(Vehicle.Kind.DOUBLE_AXLE_TRAILER, "Tandem trailer", 300f)),
                PressureUnit.KILO_PASCAL, TemperatureUnit.CELSIUS, 1030.0, Modifier.fillMaxSize())
        } }
    }
    @Test fun largeFontCombined() = paparazzi.snapshot {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f),
            LocalReadability provides Readability(true, 1.5f)) {
            MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) {
                CombinedReadings(listOf(health(Vehicle.Kind.CAR_WITH_SPARE, "Touring car with spare", 230f),
                    health(Vehicle.Kind.SINGLE_AXLE_TRAILER, "Trailer", 300f)),
                    PressureUnit.PSI, TemperatureUnit.FAHRENHEIT, 1030.0, Modifier.fillMaxSize())
            } }
        }
    }
    @Test fun maximumReadoutWithSystemScalingAndWarning() = paparazzi.snapshot {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f),
            LocalReadability provides Readability(true, 2f)) {
            MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) {
                LandscapeReadings(setOf(Location.Wheel.FRONT_LEFT), Modifier.fillMaxSize()) {
                    ReadingValues(it, State.Alerting(0.0, 123, Pressure(999f), PressureUnit.KILO_PASCAL,
                        Temperature(105f), TemperatureUnit.CELSIUS), size = ReadingSize.CARD)
                }
            } }
        }
    }
}
