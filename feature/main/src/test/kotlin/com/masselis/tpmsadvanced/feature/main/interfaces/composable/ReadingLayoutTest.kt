package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.masselis.tpmsadvanced.core.test.MainDispatcherRule
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.PressureLoss
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBattery
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow.State
import org.junit.Rule
import org.junit.Test

internal class ReadingLayoutTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig(screenWidth = 720, screenHeight = 1600,
            orientation = ScreenOrientation.LANDSCAPE, softButtons = false),
        theme = "android:Theme.Material3.DayNight.NoActionBar",
    )
    private val normal = State.Normal(0.0, 0x123456, 230f.kpa, PressureUnit.KILO_PASCAL, 25f.celsius,
        TemperatureUnit.CELSIUS, SensorBattery.Voltage(2.9f))

    @Test fun motorcycle() = paparazzi.snapshot {
        MaterialTheme { Surface(Modifier.fillMaxSize()) {
            LandscapeReadings(Vehicle.Kind.MOTORCYCLE.locations) {
                ReadingValues(it, normal, size = ReadingSize.CARD)
            }
        } }
    }

    @Test fun carWithWarnings() = paparazzi.snapshot {
        MaterialTheme { Surface(Modifier.fillMaxSize()) {
            LandscapeReadings(Vehicle.Kind.CAR.locations) {
                ReadingValues(it, if (it == Location.Wheel.FRONT_LEFT)
                    normal.copy(sensorBattery = SensorBattery.Percentage(5), pressureLoss = PressureLoss(10f.kpa, 0.0))
                else normal, size = ReadingSize.CARD)
            }
        } }
    }

    @Test fun doubleAxleTrailer() = paparazzi.snapshot {
        MaterialTheme { Surface(Modifier.fillMaxSize()) {
            LandscapeReadings(Vehicle.Kind.DOUBLE_AXLE_TRAILER.locations) {
                ReadingValues(it, normal, size = ReadingSize.CARD)
            }
        } }
    }

    @Test fun magnifiedWithLargeSystemFont() = paparazzi.snapshot {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.5f)) {
            MaterialTheme { Surface(Modifier.fillMaxSize()) {
                MagnifiedReading(Location.Axle.FRONT, normal.copy(pressureLoss = PressureLoss(10f.kpa, 0.0))) {}
            } }
        }
    }
}
