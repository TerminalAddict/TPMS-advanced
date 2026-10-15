package com.masselis.tpmsadvanced.feature.qrcode.usecase

import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.feature.main.usecase.CurrentVehicleUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.UUID
import kotlin.test.assertFailsWith

internal class BoundSensorMapUseCaseTest {
    @Test
    fun `conflicting assignments make no database changes`() = runTest {
        val database = mockk<SensorDatabase>(relaxed = true)
        val current = mockk<CurrentVehicleUseCase> {
            every { value } returns mockk {
                every { vehicle } returns mockk {
                    every { uuid } returns UUID.randomUUID()
                    every { kind } returns Vehicle.Kind.MOTORCYCLE
                }
            }
        }
        val binding = BoundSensorMapUseCase.Impl(database, current)
        assertFailsWith<ConflictingLocations> { binding.bind(QrSensorParser.parse("11AD8B&21563D")) }
        coVerify(exactly = 0) { database.upsert(any<List<Sensor>>(), any()) }
        coVerify(exactly = 0) { database.upsert(any<Sensor>(), any()) }
    }
}
