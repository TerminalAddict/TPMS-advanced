package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.masselis.tpmsadvanced.core.test.MainDispatcherRule
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.SensorCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow.State
import org.junit.Rule
import org.junit.Test

internal class FeatureBatchLayoutTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig(screenWidth = 360, screenHeight = 740, density = Density.MEDIUM,
            softButtons = false), theme = "android:Theme.Material3.DayNight.NoActionBar"
    )
    private val reading = State.Normal(0.0, 123, Pressure(230f), PressureUnit.KILO_PASCAL,
        Temperature(25f), TemperatureUnit.CELSIUS)

    @Test fun spareCarPortrait() = vehicle(Vehicle.Kind.CAR_WITH_SPARE)
    @Test fun monowheelPortrait() = vehicle(Vehicle.Kind.MONOWHEEL)
    private fun vehicle(kind: Vehicle.Kind) = paparazzi.snapshot {
        MaterialTheme { Surface(Modifier.fillMaxSize()) {
            LandscapeReadings(kind.locations) { ReadingValues(it, reading, size = ReadingSize.CARD) }
        } }
    }
    @Test fun calibration() = paparazzi.snapshot {
        MaterialTheme { CalibrationForm(123, SensorCalibration(), {}, {}) }
    }
    @Test fun historyWithBriefPressureLoss() = paparazzi.snapshot {
        val readings = (0..100).map { index ->
            Tyre.Located(Tyre.Unlocated(index.toDouble(), -60, 123,
                Pressure(if (index == 50) 180f else 230f), Temperature(25f), 0u, false), Location.Axle.FRONT)
        }
        MaterialTheme { Surface { Column {
            HistoryPlot(readings, pressure = true)
            HistoryPlot(readings, pressure = false)
        } } }
    }
}
