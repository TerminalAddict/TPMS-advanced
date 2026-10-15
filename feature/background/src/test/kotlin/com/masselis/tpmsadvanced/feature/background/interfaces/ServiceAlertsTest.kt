package com.masselis.tpmsadvanced.feature.background.interfaces

import app.cash.turbine.test
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.PressureLoss
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBattery
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.TyreAtmosphere
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State
import com.masselis.tpmsadvanced.feature.main.usecase.VehicleRangesUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

internal class ServiceAlertsTest {
    private val low = MutableStateFlow(200f.kpa)
    private val high = MutableStateFlow(300f.kpa)
    private val temp = MutableStateFlow(80f.celsius)
    private val rear = MutableStateFlow<Pair<Pressure, Pressure>?>(null)
    private val ranges = mockk<VehicleRangesUseCase> {
        every { lowPressure } returns low
        every { highPressure } returns high
        every { highTemp } returns temp
        every { rearPressuresIfSeparated() } returns rear
    }

    @Test
    fun `pressure and temperature edits correct an alert without another reading`() = runTest {
        val reading = TyreAtmosphere(1.0, 1, 190f.kpa, 70f.celsius)
        flowOf(listOf(Location.Wheel.FRONT_LEFT to reading)).alertStates(ranges).test {
            assertIs<State.PressureAlert>(awaitItem())
            low.value = 180f.kpa
            assertEquals(State.NoAlert, awaitItem())
            temp.value = 60f.celsius
            assertIs<State.TemperatureAlert>(awaitItem())
            temp.value = 90f.celsius
            assertEquals(State.NoAlert, awaitItem())
            high.value = 185f.kpa
            assertIs<State.PressureAlert>(awaitItem())
        }
    }

    @Test
    fun `rear threshold edits expose and clear the retained rear reading`() = runTest {
        val reading = TyreAtmosphere(1.0, 1, 230f.kpa, 20f.celsius)
        flowOf(listOf(Location.Axle.REAR to reading)).alertStates(ranges).test {
            assertEquals(State.NoAlert, awaitItem())
            rear.value = 250f.kpa to 300f.kpa
            assertIs<State.PressureAlert>(awaitItem())
            rear.value = null
            assertEquals(State.NoAlert, awaitItem())
        }
    }

    @Test
    fun `leak warning has lower priority than pressure and temperature and clears reactively`() = runTest {
        val reading = TyreAtmosphere(15.0, 1, 230f.kpa, 20f.celsius, SensorBattery.Percentage(5),
            PressureLoss(10f.kpa, 0.0))
        val readings = MutableStateFlow(listOf(Location.Axle.FRONT to reading))
        readings.alertStates(ranges).test {
            assertIs<State.PressureLossAlert>(awaitItem())
            temp.value = 15f.celsius
            assertIs<State.TemperatureAlert>(awaitItem())
            low.value = 250f.kpa
            assertIs<State.PressureAlert>(awaitItem())
            low.value = 200f.kpa
            assertIs<State.TemperatureAlert>(awaitItem())
            temp.value = 80f.celsius
            assertIs<State.PressureLossAlert>(awaitItem())
            readings.value = listOf(Location.Axle.FRONT to reading.copy(pressureLoss = null))
            assertIs<State.BatteryAlert>(awaitItem())
        }
    }

    @Test
    fun `low battery has its own warning and pressure alerts take priority`() = runTest {
        val reading = TyreAtmosphere(1.0, 1, 230f.kpa, 20f.celsius, SensorBattery.Percentage(5))
        flowOf(listOf(Location.Axle.FRONT to reading)).alertStates(ranges).test {
            assertIs<State.BatteryAlert>(awaitItem())
            low.value = 250f.kpa
            assertIs<State.PressureAlert>(awaitItem())
            low.value = 200f.kpa
            assertIs<State.BatteryAlert>(awaitItem())
        }
    }
}
