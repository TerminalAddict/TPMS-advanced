package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.bar
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

internal class LocatedTyreScannerUseCaseTest {
    private val bound = MutableStateFlow<Sensor?>(null)
    private var ownedElsewhere = setOf<Int>()
    private val binding = mockk<SensorBindingUseCase> {
        every { boundSensor() } returns bound
        every { isBound(any()) } answers { firstArg<Int>() in ownedElsewhere }
    }
    private val source = mockk<BluetoothLeScanner>()
    private fun tyre(id: Int, at: Location? = Location.Wheel.FRONT_LEFT): Tyre {
        val raw = Tyre.Unlocated(10.0, -60, id, 2f.bar, 20f.celsius, 30u, false)
        return at?.let { Tyre.Located(raw, it) } ?: raw
    }
    private suspend fun read(at: Location, vararg readings: Tyre): List<Tyre.Located> {
        every { source.normalScan() } returns readings.asFlow()
        every { source.highDutyScan() } returns readings.asFlow()
        val scanner = LocatedTyreScannerUseCase(source, at, binding)
        val normal = scanner.normalScan().toList()
        assertEquals(normal, scanner.highDutyScan().toList())
        return normal
    }

    @Test
    fun `double axle trailer keeps all four broadcast positions independent`() = runTest {
        val locations = Vehicle.Kind.DOUBLE_AXLE_TRAILER.locations.toList()
        val readings = locations.mapIndexed { index, location -> tyre(index + 1, location) }.toTypedArray()
        locations.forEachIndexed { index, location ->
            val result = read(location, *readings)
            assertEquals(listOf(index + 1), result.map { it.sensorId })
            assertEquals(listOf(location), result.map { it.location })
        }
    }

    @Test
    fun `spare and monowheel require explicit binding and ignore embedded wheel hints`() = runTest {
        Location.Auxiliary.entries.forEach { location ->
            bound.value = null
            assertEquals(emptyList(), read(location, tyre(1), tyre(2, null)))
            bound.value = Sensor(2, location)
            val readings = read(location, tyre(1), tyre(2))
            assertEquals(listOf(2), readings.map { it.sensorId })
            assertEquals(listOf(location), readings.map { it.location })
        }
    }

    @Test
    fun `explicit binding rejects another sensor advertising the same wheel`() = runTest {
        bound.value = Sensor(1, Location.Wheel.FRONT_LEFT)
        val result = read(Location.Wheel.FRONT_LEFT, tyre(2), tyre(1, null), tyre(3))
        assertEquals(listOf(1), result.map { it.sensorId })
    }

    @Test
    fun `sensor assigned elsewhere cannot populate an unbound tyre`() = runTest {
        ownedElsewhere = setOf(2)
        assertEquals(listOf(1), read(Location.Wheel.FRONT_LEFT, tyre(2), tyre(1)).map { it.sensorId })
    }

    @Test
    fun `unbound sensor uses matching wheel axle or side`() = runTest {
        assertEquals(emptyList(), read(Location.Wheel.REAR_RIGHT, tyre(1)))
        assertEquals(Location.Axle.FRONT, read(Location.Axle.FRONT, tyre(1)).single().location)
        assertEquals(Location.Side.LEFT, read(Location.Side.LEFT, tyre(1)).single().location)
        assertEquals(emptyList(), read(Location.Wheel.FRONT_LEFT, tyre(1, null)))
    }

    @Test
    fun `explicit binding overrides embedded position but cannot escape its own location`() = runTest {
        bound.value = Sensor(1, Location.Axle.REAR)
        assertEquals(Location.Axle.REAR, read(Location.Axle.REAR, tyre(1)).single().location)
        assertEquals(emptyList(), read(Location.Wheel.FRONT_LEFT, tyre(1)))
    }
}
