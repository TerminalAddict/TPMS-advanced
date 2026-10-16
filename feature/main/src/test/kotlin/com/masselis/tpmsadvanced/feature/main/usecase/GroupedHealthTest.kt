package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.WheelHealth
import com.masselis.tpmsadvanced.data.vehicle.model.WheelReadiness
import org.junit.Test
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.test.assertNotNull

internal class GroupedHealthTest {
    private val vehicle = Vehicle(UUID.randomUUID(), Vehicle.Kind.UTE, "Ute", Pressure(180f), Pressure(300f),
        false, Pressure(180f), Pressure(300f), Temperature(-20f), Temperature(45f), Temperature(90f), null)
    private fun health(state: WheelReadiness) = VehicleHealth(vehicle,
        listOf(WheelHealth(Location.Wheel.FRONT_LEFT, null, state, emptyList())), null)

    @Test fun `a stale trailer prevents the combination being ready`() {
        val group = assertNotNull(groupedVehicleHealth(listOf(health(WheelReadiness.HEALTHY),
            health(WheelReadiness.SLEEPING))))
        assertFalse(group.ready)
        assertTrue(assertNotNull(groupedVehicleHealth(listOf(health(WheelReadiness.HEALTHY),
            health(WheelReadiness.HEALTHY)))).ready)
        assertNull(groupedVehicleHealth(emptyList()))
    }
    @Test fun `a warning on either vehicle affects the combined summary`() {
        assertTrue(assertNotNull(groupedVehicleHealth(listOf(health(WheelReadiness.HEALTHY),
            health(WheelReadiness.WARNING)))).warning)
        assertTrue(assertNotNull(groupedVehicleHealth(listOf(health(WheelReadiness.HEALTHY),
            health(WheelReadiness.HEALTHY).copy(maintenance = "Check tread")))).warning)
    }
}
