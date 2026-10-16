package com.masselis.tpmsadvanced.data.vehicle.model

import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class SensorLibraryTest {
    @Test fun `zero pressure and genuine hot or high readings are not silently invalidated`() {
        assertTrue(suspiciousReading(0f, 120f, SensorBattery.Voltage(2.5f)).isEmpty())
        assertTrue(suspiciousReading(1200f, 120f, null).isEmpty())
        assertEquals(listOf("Pressure field is implausible"), suspiciousReading(-10f, 20f, null))
        assertEquals(2, suspiciousReading(Float.NaN, Float.POSITIVE_INFINITY, null).size)
        assertEquals(3, suspiciousReading(65535f, -100f, SensorBattery.Percentage(255)).size)
    }
    @Test fun `scanner off periods and process restarts do not count as missing broadcasts`() {
        val tracker = HealthReceptionTracker()
        assertEquals(ReceptionInterval(), tracker.record(1, 0, 60))
        tracker.available(true)
        tracker.record(1, 1_000_000_000, 60)
        assertEquals(ReceptionInterval(180.0, 2), tracker.record(1, 181_000_000_000, 60))
        tracker.available(false); tracker.available(true)
        assertEquals(ReceptionInterval(), tracker.record(1, 1000_000_000_000, 60))
        assertEquals(ReceptionInterval(60.0, 0), tracker.record(1, 1060_000_000_000, 60))
        val fresh = HealthReceptionTracker().apply { available(true) }
        assertEquals(ReceptionInterval(), fresh.record(1, 2000_000_000_000, 60))
    }
    @Test fun `missing estimates require known cadence and ignore reordered callbacks`() {
        val tracker = HealthReceptionTracker().apply { available(true) }
        tracker.record(1, 200_000_000_000, null)
        assertEquals(ReceptionInterval(), tracker.record(1, 100_000_000_000, null))
        assertEquals(ReceptionInterval(180.0, 0), tracker.record(1, 380_000_000_000, null))
        assertEquals(ReceptionInterval(180.0, 0), tracker.record(1, 560_000_000_000, 60))
        assertEquals(ReceptionInterval(180.0, 2), tracker.record(1, 740_000_000_000, 60))
    }
    private fun day(day: Long, volts: Double? = null, percent: Double? = null, rssi: Double = -60.0) =
        SensorHealthDay(day, 10, rssi, volts, percent, 600.0, 0, 60.0, 0)
    @Test fun `health trends need time and do not mix percentage with voltage`() {
        assertTrue(sensorHealthFindings(listOf(day(0, 3.0), day(1, 2.5))).single().contains("week"))
        val falling = sensorHealthFindings(listOf(day(0, 3.1), day(7, 3.0), day(30, 2.7)))
        assertTrue(falling.any { it.contains("Battery decreased") })
        val mixed = sensorHealthFindings(listOf(day(0, 3.0), day(7, percent = 40.0), day(30, percent = 20.0)))
        assertTrue(mixed.none { it.contains("Battery decreased") })
    }
    @Test fun `battery replacement is reported as rise not deterioration`() {
        val notes = sensorHealthFindings(listOf(day(0, 2.5), day(7, 2.4), day(30, 3.1)))
        assertTrue(notes.any { it.contains("increased") })
        assertTrue(notes.none { it.contains("decreased") })
    }
    @Test fun `library validates uint backed ids and optional expected cadence`() {
        SensorMetadata(-1, "Spare").validate()
        SensorMetadata(0).validate()
        assertFailsWith<IllegalArgumentException> { DrivingInsights(broadcastSeconds = mapOf(1 to 0)).validate() }
        assertFailsWith<IllegalArgumentException> { DrivingInsights(broadcastSeconds = mapOf(1 to 3601)).validate() }
        assertFailsWith<IllegalArgumentException> { SensorMetadata(1, notes = "x".repeat(1001)).validate() }
    }
    @Test fun `specification comparisons are advisory and old settings still decode`() {
        val spec = TyreSpecification(load = "Towing", frontSize = "225/65R17", frontKpa = 240f,
            rearKpa = 300f, source = "Handbook p42").also { it.validate() }
        val old = PressureProfile(name = "Solo", low = 200f, high = 260f)
        assertEquals(listOf("Rear cold recommendation is outside this profile's limits"),
            specificationComparison(spec, old))
        assertEquals(260f, old.high)
        assertTrue(specificationComparison(spec, old.copy(separateRear = true, rearHigh = 350f)).single()
            .contains("within"))
        assertTrue(Json.decodeFromString<VehicleRideTools>("{}").specifications.isEmpty())
        assertFailsWith<IllegalArgumentException> { spec.copy(frontKpa = Float.NaN).validate() }
        assertFailsWith<IllegalArgumentException> { spec.copy(source = "").validate() }
    }
}
