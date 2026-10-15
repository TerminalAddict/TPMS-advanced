package com.masselis.tpmsadvanced.feature.main.usecase

import app.cash.turbine.test
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
internal class BoundReadingStateTest {
    @Test
    fun `returning keeps last reading while another binding clears it`() = runTest {
        val bound = MutableStateFlow<Sensor?>(Sensor(1, Location.Wheel.FRONT_LEFT))
        val binding = mockk<SensorBindingUseCase> { every { boundSensor() } returns bound }
        val readings = MutableSharedFlow<String>()
        val state = readings.forBinding(binding, "empty").stateIn(backgroundScope, WhileSubscribed(), "empty")
        state.test {
            assertEquals("empty", awaitItem())
            runCurrent()
            readings.emit("sensor 1")
            assertEquals("sensor 1", awaitItem())
        }
        runCurrent()
        state.test {
            assertEquals("sensor 1", awaitItem())
            runCurrent()
            expectNoEvents()
            bound.value = Sensor(2, Location.Wheel.FRONT_LEFT)
            assertEquals("empty", awaitItem())
            readings.emit("sensor 2")
            assertEquals("sensor 2", awaitItem())
        }
        runCurrent()
        bound.value = null
        state.test {
            assertEquals("sensor 2", awaitItem())
            assertEquals("empty", awaitItem())
        }
    }
}
