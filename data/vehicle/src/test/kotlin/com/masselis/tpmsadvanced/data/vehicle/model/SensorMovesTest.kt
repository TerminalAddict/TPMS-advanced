package com.masselis.tpmsadvanced.data.vehicle.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class SensorMovesTest {
    private val wheels = Vehicle.Kind.CAR.locations.toList()
    private val sensors = wheels.mapIndexed { index, wheel -> Sensor(index, wheel) }
    @Test fun `swaps preserve both sensor identities`() {
        val moved = moveSensors(sensors, wheels.toSet(), mapOf(wheels[0] to wheels[1], wheels[1] to wheels[0]))
        assertEquals(wheels[1], moved.first { it.id == 0 }.location)
        assertEquals(wheels[0], moved.first { it.id == 1 }.location)
        assertEquals(sensors.map { it.id }, moved.map { it.id })
    }
    @Test fun `rotation and inverse restore all assignments`() {
        val rotation = wheels.mapIndexed { index, wheel -> wheel to wheels[(index + 1) % wheels.size] }.toMap()
        val moved = moveSensors(sensors, wheels.toSet(), rotation)
        assertEquals(sensors, moveSensors(moved, wheels.toSet(), rotation.entries.associate { it.value to it.key }))
    }
    @Test fun `occupied destinations and unsupported wheels are rejected before writes`() {
        assertFailsWith<IllegalArgumentException> { moveSensors(sensors, wheels.toSet(),
            mapOf(wheels[0] to wheels[1])) }
        assertFailsWith<IllegalArgumentException> {
            moveSensors(sensors, wheels.toSet(), mapOf(wheels[0] to Location.Axle.FRONT))
        }
    }
    @Test fun `a sensor can move into an empty wheel`() {
        assertEquals(listOf(Sensor(0, wheels[1])), moveSensors(sensors.take(1), wheels.toSet(),
            mapOf(wheels[0] to wheels[1])))
    }
    @Test fun `guided identification keeps separate baselines for nearby sensors`() {
        val tracker = SensorIdentification()
        fun reading(id: Int, pressure: Float) = Tyre.Unlocated(0.0, -60, id, Pressure(pressure), Temperature(20f),
            0u, false)
        assertFalse(tracker.observe(reading(1, 230f)))
        assertFalse(tracker.observe(reading(2, 200f)))
        assertFalse(tracker.observe(reading(2, 205f)))
        assertTrue(tracker.observe(reading(1, 0f)))
        assertTrue(tracker.observe(reading(1, 230f)))
    }
}
