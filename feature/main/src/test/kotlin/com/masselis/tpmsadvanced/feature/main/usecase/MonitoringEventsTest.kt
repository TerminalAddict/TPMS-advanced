package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.data.vehicle.model.TyreAtmosphere
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.WheelHealth
import com.masselis.tpmsadvanced.data.vehicle.model.WheelReadiness
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class MonitoringEventsTest {
    private val vehicle = Vehicle(UUID.randomUUID(), Vehicle.Kind.CAR, "Car", Pressure(200f), Pressure(300f),
        true, Pressure(250f), Pressure(350f), Temperature(-20f), Temperature(45f), Temperature(80f), null)
    private fun health(state: WheelReadiness = WheelReadiness.WARNING, location: Location = Location.Wheel.FRONT_LEFT) =
        VehicleHealth(vehicle, listOf(WheelHealth(location,
            TyreAtmosphere(100.0, -1, Pressure(150f), Temperature(101f)), state,
            listOf("Pressure outside limits", "Temperature above limit", "Low sensor battery",
                "Possible pressure loss"))), null)

    @Test fun `maintenance retains heat and battery events without hiding raw health warnings`() {
        val health = health()
        assertEquals(listOf("Temperature above limit", "Low sensor battery"), health.monitoringEvents(true).map {
            it.kind })
        assertEquals(4, health.wheels.single().warnings.size)
        assertEquals(4, health.monitoringEvents(false).size)
        assertTrue(health.monitoringEvents(false).first().critical)
        assertTrue(health.monitoringEvents(true).first().critical)
        assertEquals(-1, health.monitoringEvents(true).first().sensor)
    }
    @Test fun `stale warnings are not new alerts and maintenance suppresses missing sensors`() {
        assertTrue(health(WheelReadiness.SLEEPING).monitoringEvents(false).isEmpty())
        assertEquals("Sensor missing", health(WheelReadiness.MISSING).monitoringEvents(false).single().kind)
        assertTrue(health(WheelReadiness.MISSING).monitoringEvents(true).isEmpty())
    }
    @Test fun `rear thresholds determine criticality and severity change creates a new event identity`() {
        val health = health(location = Location.Wheel.REAR_LEFT)
        val lessLow = health.copy(wheels = health.wheels.map { it.copy(reading = it.reading!!.copy(pressure =
            Pressure(200f))) })
        val previous = health.monitoringEvents(false).first()
        val next = lessLow.monitoringEvents(false).first()
        assertTrue(previous.critical); assertFalse(next.critical)
        assertTrue(previous.key != next.key)
    }
}
