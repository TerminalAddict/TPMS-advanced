package com.masselis.tpmsadvanced.data.vehicle.model

import org.junit.Test
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class RideToolsTest {
    private val id = UUID.randomUUID()
    private val vehicle = Vehicle(id, Vehicle.Kind.CAR, "Test car", Pressure(180f), Pressure(300f),
        true, Pressure(220f), Pressure(350f), Temperature(-20f), Temperature(45f), Temperature(90f), null)
    private fun reading(time: Double = 1000.0, pressure: Float = 230f, battery: SensorBattery? = null) =
        TyreAtmosphere(time, 123, Pressure(pressure), Temperature(25f), battery, null)

    @Test fun `never seen and sleeping sensors are not confirmed healthy`() {
        assertEquals(WheelReadiness.UNSEEN, wheelHealth(vehicle, Location.Wheel.FRONT_LEFT, null, 1100.0).state)
        assertEquals(WheelReadiness.HEALTHY, wheelHealth(vehicle, Location.Wheel.FRONT_LEFT, reading(), 1120.0).state)
        assertEquals(WheelReadiness.SLEEPING, wheelHealth(vehicle, Location.Wheel.FRONT_LEFT, reading(), 1120.1).state)
        assertEquals(WheelReadiness.SLEEPING, wheelHealth(vehicle, Location.Wheel.FRONT_LEFT, reading(), 999.0).state)
    }

    @Test fun `rear limits battery and temperature all affect readiness`() {
        assertEquals(WheelReadiness.HEALTHY, wheelHealth(vehicle, Location.Wheel.FRONT_LEFT,
            reading(pressure = 200f), 1000.0).state)
        val rear = wheelHealth(vehicle, Location.Wheel.REAR_LEFT, reading(pressure = 200f), 1000.0)
        assertEquals(listOf("Pressure outside limits"), rear.warnings)
        val hot = wheelHealth(vehicle, Location.Wheel.FRONT_LEFT, reading(battery = SensorBattery.Percentage(9))
            .copy(temperature = Temperature(100f)), 1000.0)
        assertEquals(2, hot.warnings.size)
        assertEquals(WheelReadiness.WARNING, hot.state)
        assertEquals(WheelReadiness.MISSING, wheelHealth(vehicle, Location.Wheel.FRONT_LEFT, reading(), 2000.0,
            true).state)
    }

    @Test fun `only readings received this ride enroll in missing detection`() {
        val tracker = MissingSensorTracker()
        assertTrue(tracker.evaluate(mapOf("old" to 90.0, "live" to 101.0), 100.0, true, 0, 60000).isEmpty())
        assertTrue(tracker.evaluate(mapOf("old" to 90.0, "live" to 101.0), 100.0, true, 59999, 60000).isEmpty())
        assertEquals(setOf("live"), tracker.evaluate(mapOf("old" to 90.0, "live" to 101.0), 100.0, true, 60000, 60000))
        assertTrue(tracker.evaluate(mapOf("live" to 161.0), 100.0, true, 60001, 60000).isEmpty())
    }

    @Test fun `bluetooth outages grant a new grace period without losing previously seen sensors`() {
        val tracker = MissingSensorTracker()
        tracker.evaluate(mapOf("a" to 101.0), 100.0, true, 0, 60000)
        assertTrue(tracker.evaluate(mapOf("a" to 101.0), 100.0, false, 90000, 60000).isEmpty())
        assertTrue(tracker.evaluate(mapOf("a" to 101.0), 100.0, true, 100000, 60000).isEmpty())
        assertEquals(setOf("a"), tracker.evaluate(mapOf("a" to 101.0), 100.0, true, 160000, 60000))
    }

    @Test fun `parked stop new ride and removed bindings clear missing warnings`() {
        val tracker = MissingSensorTracker()
        tracker.evaluate(mapOf("a" to 101.0), 100.0, true, 0, 60000)
        assertTrue(tracker.evaluate(mapOf("a" to 101.0), null, true, 90000, 60000).isEmpty())
        assertTrue(tracker.evaluate(mapOf("a" to 101.0), 200.0, true, 100000, 60000).isEmpty())
        tracker.evaluate(mapOf("a" to 201.0), 200.0, true, 101000, 60000)
        assertTrue(tracker.evaluate(emptyMap(), 200.0, true, 200000, 60000).isEmpty())
    }

    @Test fun `profiles reject invalid bounds and compare current front and rear limits`() {
        val profile = PressureProfile(name = "Towing", low = 180f, high = 300f, separateRear = true,
            rearLow = 220f, rearHigh = 350f)
        profile.validate()
        assertTrue(profile.matches(vehicle))
        assertFalse(profile.matches(vehicle.copy(rearLowPressure = Pressure(210f))))
        listOf(profile.copy(low = Float.NaN), profile.copy(low = 400f), profile.copy(rearHigh = 200f),
            profile.copy(name = " ")).forEach { assertFailsWith<IllegalArgumentException> { it.validate() } }
    }

    @Test fun `automatic selection declines ambiguities and respects manual override`() {
        assertNull(automaticVehicleCandidate(emptyList(), false))
        assertNull(automaticVehicleCandidate(listOf("car", "bike"), false))
        assertNull(automaticVehicleCandidate(listOf("car"), true))
        assertEquals("car", automaticVehicleCandidate(listOf("car", "car"), false))
        assertFailsWith<IllegalArgumentException> { RideToolsData(vehicles = mapOf(id.toString() to
            VehicleRideTools(accessoryAddress = "AA:BB:CC:DD:EE:FF"), UUID.randomUUID().toString() to
            VehicleRideTools(accessoryAddress = "AA:BB:CC:DD:EE:FF"))).validate() }
    }

    @Test fun `NFC and shortcuts accept only canonical vehicle URIs`() {
        assertEquals(id, vehicleLinkId("tpmsadvanced://vehicle/$id"))
        listOf(null, "invalid", "tpmsadvanced://vehicle/1-1-1-1-1", "tpmsadvanced://vehicle/",
            "https://vehicle/$id", "tpmsadvanced://other/$id", "tpmsadvanced://vehicle/$id/extra",
            "tpmsadvanced://vehicle/$id?delete=true").forEach { assertNull(vehicleLinkId(it)) }
    }

    @Test fun `backup merge rejects conflicting accessories before writing a restore journal`() {
        val other = UUID.randomUUID().toString()
        val mapping = VehicleRideTools(accessoryAddress = "AA:BB:CC:DD:EE:FF")
        val previous = RideToolsData(vehicles = mapOf(other to mapping))
        val imported = RideToolsData(vehicles = mapOf(id.toString() to mapping))
        assertFailsWith<IllegalArgumentException> { mergeRideTools(previous, imported, listOf(id.toString())) }
        assertEquals(imported, mergeRideTools(previous, imported, listOf(id.toString(), other)))
    }

    @Test fun `reminders use either date or mileage and can be disabled or rescheduled`() {
        val reminder = MaintenanceReminder(dueDate = "2026-10-15", dueKm = 1000f, currentKm = 999f, enabled = true)
        reminder.validate()
        assertFalse(reminder.isDue(LocalDate.parse("2026-10-14")))
        assertTrue(reminder.isDue(LocalDate.parse("2026-10-15")))
        assertTrue(reminder.copy(currentKm = 1000f).isDue(LocalDate.parse("2026-10-14")))
        assertFalse(reminder.copy(enabled = false).isDue(LocalDate.parse("2027-01-01")))
        assertFailsWith<IllegalArgumentException> { MaintenanceReminder(enabled = true).validate() }
        assertFailsWith<java.time.format.DateTimeParseException> { reminder.copy(dueDate = "bad").validate() }
    }

    @Test fun `maintenance rejects nonfinite values and backup preserves all ride tools`() {
        val record = MaintenanceRecord(wheel = "wheel:FRONT_LEFT", date = "2026-10-15", kind = "Installation",
            brandModel = "Test tyre", odometerKm = 1000f, treadMm = 6f, pressureKpa = 230f)
        record.validate()
        assertFailsWith<IllegalArgumentException> { record.copy(treadMm = Float.NaN).validate() }
        val tools = RideToolsData(vehicles = mapOf(id.toString() to VehicleRideTools(
            profiles = listOf(PressureProfile(name = "Solo", low = 180f, high = 300f)), records = listOf(record),
            reminder = MaintenanceReminder(dueDate = "2026-11-15", enabled = true))), spokenDiagnostics = true)
        val backup = ConfigurationBackup(vehicles = listOf(BackupVehicle(id.toString(), "CAR", "Car", true,
            180f, 300f, false, 180f, 300f, -20f, 45f, 90f, emptyList())), settings = BackupSettings(rideTools = tools))
        assertEquals(backup, ConfigurationBackup.decode(backup.encode()))
        assertEquals(RideToolsData(), BackupSettings().rideTools)
    }
}
