package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.masselis.tpmsadvanced.core.test.MainDispatcherRule
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.PressureLoss
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBattery
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreIconViewModel
import com.masselis.tpmsadvanced.feature.main.usecase.TyreIconStateFlow
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow.State
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

internal class DoubleAxleTrailerLayoutTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig(screenWidth = 360, screenHeight = 640,
            density = com.android.resources.Density.MEDIUM, softButtons = false),
        theme = "android:Theme.Material3.DayNight.NoActionBar",
    )
    private val normal = State.Normal(0.0, 0x123456, 230f.kpa, PressureUnit.KILO_PASCAL, 25f.celsius,
        TemperatureUnit.CELSIUS, SensorBattery.Voltage(2.9f))
    private val icon = object : TyreIconViewModel {
        override val stateFlow = MutableStateFlow<TyreIconStateFlow.State>(TyreIconStateFlow.State.NotDetected)
    }

    @Test fun lightWithFourReadings() = render(dark = false)
    @Test fun darkWithFourReadings() = render(dark = true)
    @Test fun largeFontWithWarnings() = render(dark = false, fontScale = 1.5f)

    private fun render(dark: Boolean, fontScale: Float = 1f) {
        val rendered = mutableSetOf<Location>()
        paparazzi.snapshot {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                    Surface(Modifier.fillMaxSize()) {
                        DoubleAxleTrailerLayout(
                            modifier = Modifier.fillMaxSize(),
                            tyre = { location, modifier ->
                                Tyre(
                                    location, SnackbarHostState(), modifier,
                                    vehicleComponent = lazy { error("Render uses a supplied view model") },
                                    viewModel = icon,
                                )
                            },
                            reading = { location, modifier ->
                                rendered += location
                                ReadingValues(location, if (location == Location.Wheel.FRONT_LEFT)
                                    normal.copy(sensorBattery = SensorBattery.Percentage(5),
                                        pressureLoss = PressureLoss(10f.kpa, 0.0))
                                else normal, modifier)
                            },
                            binding = { _, modifier -> TextButton(onClick = {}, modifier = modifier) { Text("+") } },
                        )
                    }
                }
            }
        }
        assertEquals(Vehicle.Kind.DOUBLE_AXLE_TRAILER.locations, rendered)
    }
}
