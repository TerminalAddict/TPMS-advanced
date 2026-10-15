package com.masselis.tpmsadvanced.feature.main.usecase

import app.cash.turbine.test
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.core.test.mockkQueryOneOrNull
import com.masselis.tpmsadvanced.data.vehicle.interfaces.TyreDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.bar
import com.masselis.tpmsadvanced.data.vehicle.model.Location.Wheel.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class ListenTyreWithDatabaseUseCaseTest {

    private lateinit var vehicle: Vehicle
    private lateinit var location: Location
    private lateinit var tyreDatabase: TyreDatabase
    private lateinit var listenTyreUseCase: ListenTyreUseCase
    private var binding: SensorBindingUseCase = SensorBindingUseCase.NoOp

    private fun CoroutineScope.test(
        detectLeaks: MutableStateFlow<Boolean> = MutableStateFlow(false),
    ) = ListenTyreWithDatabaseUseCase.Impl(
        vehicle,
        location,
        tyreDatabase,
        listenTyreUseCase,
        this,
        binding,
        detectLeaks,
    )

    @Before
    fun setup() {
        vehicle = mockk {
            val uuid = UUID.randomUUID()
            every { this@mockk.uuid } returns uuid
        }
        location = FRONT_LEFT
        tyreDatabase = mockk {
            coEvery { insert(any(), any()) } returns Unit
            every { latestByTyreLocationByVehicle(any<Location.Wheel>(), any()) } returns
                    mockkQueryOneOrNull(null as Tyre.Located?)
        }
        listenTyreUseCase = mockk {
            every { listen() } returns MutableSharedFlow()
        }
    }

    @Test
    fun `disabling and reenabling detection clears warnings without persisting synthetic readings`() = runTest {
        val enabled = MutableStateFlow(true)
        val source = MutableSharedFlow<Tyre.Located>()
        every { listenTyreUseCase.listen() } returns source
        val initial = Tyre.Located(0.0, -60, 1, 2.4f.bar, 25f.celsius, 50u, false, FRONT_LEFT)
        test(enabled).listen().test {
            source.subscriptionCount.first { it > 0 }
            source.emit(initial)
            assertNull(awaitItem().pressureLoss)
            source.emit(initial.copy(timestamp = 10.0, pressure = 2.3f.bar))
            assertNull(awaitItem().pressureLoss)
            source.emit(initial.copy(timestamp = 15.0, pressure = 2.3f.bar))
            assertNotNull(awaitItem().pressureLoss)
            enabled.value = false
            assertNull(awaitItem().pressureLoss)
            enabled.value = true
            assertNull(awaitItem().pressureLoss)
            source.subscriptionCount.first { it > 0 }
            source.emit(initial.copy(timestamp = 20.0, pressure = 2.3f.bar))
            assertNull(awaitItem().pressureLoss)
            source.emit(initial.copy(timestamp = 30.0, pressure = 2.3f.bar))
            assertNull(awaitItem().pressureLoss)
        }
        coVerify(exactly = 5) { tyreDatabase.insert(any(), any()) }
        coroutineContext.cancelChildren()
    }

    @Test
    fun `2 tyres emit with same id at front left`() = runTest {
        val tyresToEmit = listOf(
            Tyre.Located(now(), -20, 1, 1f.bar, 1f.celsius, 50u, false, FRONT_LEFT),
            Tyre.Located(now(), -30, 1, 2f.bar, 2f.celsius, 25u, false, FRONT_LEFT)
        )
        every { listenTyreUseCase.listen() } returns tyresToEmit
            .asFlow()
            .onCompletion { awaitCancellation() }
        test().listen().test {
            assertEquals(tyresToEmit[0], awaitItem())
            assertEquals(tyresToEmit[1], awaitItem())
        }
        coVerify(exactly = 2) { tyreDatabase.insert(any(), any()) }
        coroutineContext.cancelChildren()
    }

    @Test
    fun `No tyre emit but a cache exists`() = runTest {
        val savedTyre =
            Tyre.Located(now(), -20, 1, 1f.bar, 1f.celsius, 1u, false, FRONT_LEFT)
        every { tyreDatabase.latestByTyreLocationByVehicle(location, any()) } returns
                mockkQueryOneOrNull(savedTyre)
        test().listen().test {
            assertEquals(savedTyre, awaitItem())
        }
        coVerify(exactly = 0) { tyreDatabase.insert(any(), any()) }
        coroutineContext.cancelChildren()
    }

    @Test
    fun `bound sensor loads its own history even when a foreign reading is newer`() = runTest {
        val owned = Tyre.Located(10.0, -60, 1, 2f.bar, 20f.celsius, 30u, false, FRONT_LEFT)
        val foreign = owned.copy(timestamp = 20.0, sensorId = 2)
        binding = mockk {
            every { boundSensor() } returns MutableStateFlow(Sensor(1, FRONT_LEFT))
        }
        every { tyreDatabase.latestByTyreLocationByVehicle(location, any()) } returns mockkQueryOneOrNull(foreign)
        every { tyreDatabase.latestBySensorByTyreLocationByVehicle(1, location, any()) } returns
            mockkQueryOneOrNull(owned)
        test().listen().test { assertEquals(owned, awaitItem()) }
        coVerify(exactly = 0) { tyreDatabase.insert(any(), any()) }
        coroutineContext.cancelChildren()
    }
}
