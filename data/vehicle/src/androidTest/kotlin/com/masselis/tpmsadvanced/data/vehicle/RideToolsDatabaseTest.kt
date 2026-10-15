package com.masselis.tpmsadvanced.data.vehicle

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.data.vehicle.ioc.VehicleToolsDependencies
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.PressureProfile
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
internal class RideToolsDatabaseTest {
    private val deps get() = appGraph as VehicleToolsDependencies

    @Test fun profileUpdatesAllLimitsAndRejectsInvalidTrailerProfile() = runTest {
        val id = UUID.randomUUID()
        deps.toolsVehicles.insert(id, Vehicle.Kind.DOUBLE_AXLE_TRAILER, "Profile test", false)
        try {
            val profile = PressureProfile(name = "Towing", low = 200f, high = 400f,
                separateRear = true, rearLow = 250f, rearHigh = 450f)
            deps.toolsVehicles.applyProfile(id, profile)
            assertTrue(profile.matches(deps.toolsVehicles.selectByUuid(id).execute()))
            assertFailsWith<IllegalArgumentException> { deps.toolsVehicles.applyProfile(id, profile.copy(low = 500f)) }
            assertTrue(profile.matches(deps.toolsVehicles.selectByUuid(id).execute()))
        } finally { deps.toolsVehicles.delete(id) }
        val trailer = UUID.randomUUID()
        deps.toolsVehicles.insert(trailer, Vehicle.Kind.SINGLE_AXLE_TRAILER, "Single axle", false)
        try {
            val before = deps.toolsVehicles.selectByUuid(trailer).execute()
            assertFailsWith<IllegalArgumentException> {
                deps.toolsVehicles.applyProfile(trailer, PressureProfile(name = "Invalid", low = 180f, high = 300f,
                    separateRear = true))
            }
            assertEquals(before, deps.toolsVehicles.selectByUuid(trailer).execute())
        } finally { deps.toolsVehicles.delete(trailer) }
    }

    @Test fun fleetPacketWritesOnceAndTracksMovedBinding() = runTest {
        val id = UUID.randomUUID()
        deps.toolsVehicles.insert(id, Vehicle.Kind.CAR, "Fleet test", false)
        val sensor = UUID.randomUUID().hashCode()
        try {
            deps.toolsSensors.upsert(Sensor(sensor, Location.Wheel.FRONT_LEFT), id)
            val packet = Tyre.Unlocated(1000.0, -60, sensor, Pressure(230f), Temperature(25f), 0u, false)
            deps.toolsTyres.insertBound(packet)
            deps.toolsTyres.insert(Tyre.Located(packet, Location.Wheel.FRONT_LEFT), id)
            assertEquals(1, deps.toolsTyres.history(id, 0.0, 2000.0).size)
            deps.toolsSensors.upsert(Sensor(sensor, Location.Wheel.REAR_RIGHT), id)
            deps.toolsTyres.insertBound(packet.copy(timestamp = 1001.0))
            assertEquals(Location.Wheel.REAR_RIGHT, deps.toolsTyres.history(id, 0.0, 2000.0).last().location)
        } finally { deps.toolsVehicles.delete(id) }
    }
}
