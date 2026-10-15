package com.masselis.tpmsadvanced.feature.main.usecase

import app.cash.turbine.test
import com.masselis.tpmsadvanced.core.test.MainDispatcherRule
import com.masselis.tpmsadvanced.data.unit.interfaces.UnitPreferences
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit
import com.masselis.tpmsadvanced.data.vehicle.model.Location.Wheel.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBattery
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.bar
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.TyreAtmosphere
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow.State
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

internal class TyreStatsStateFlowTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    context(scope: TestScope)
    private fun test(
        timestamp: Double,
        sensorId: Int,
        battery: SensorBattery? = null,
    ): TyreStatsStateFlow {
        val atmosphereUseCase = mockk<TyreAtmosphereUseCase> {
            every { listen() } returns flowOf(
                TyreAtmosphere(
                    timestamp = timestamp,
                    sensorId = sensorId,
                    pressure = 2f.bar,
                    temperature = 25f.celsius,
                    sensorBattery = battery,
                )
            )
        }

        val rangeUseCase = mockk<VehicleRangesUseCase> {
            every { highTemp } returns MutableStateFlow(90f.celsius)
            every {
                resolvePressures(FRONT_LEFT)
            } returns MutableStateFlow(1f.bar to 3f.bar)
        }

        val unitPreferences = mockk<UnitPreferences> {
            every { pressure } returns MutableStateFlow(PressureUnit.BAR)
            every { temperature } returns MutableStateFlow(TemperatureUnit.CELSIUS)
        }

        return TyreStatsStateFlow(
            atmosphereUseCase = atmosphereUseCase,
            rangeUseCase = rangeUseCase,
            location = FRONT_LEFT,
            unitPreferences = unitPreferences,
            scope = scope.backgroundScope,
        )
    }

    @Test
    fun `low battery is retained without inventing a pressure alert`() = runTest {
        val battery = SensorBattery.Voltage(2f)
        test(1.0, 1, battery).test {
            assertIs<State.NotDetected>(awaitItem())
            val state = assertIs<State.Normal>(awaitItem())
            assertEquals(battery, state.sensorBattery)
            assertEquals(2f.bar, state.pressure)
        }
    }

    @Test
    fun `preserves sensor id and timestamp`() = runTest {
        val timestamp = 1_726_483_200.0
        val sensorId = 0x562D00

        test(timestamp, sensorId).test {
            assertIs<State.NotDetected>(awaitItem())

            val state = awaitItem()
            assertIs<State.Normal>(state)

            assertEquals(timestamp, state.timestamp)
            assertEquals(sensorId, state.sensorId)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
