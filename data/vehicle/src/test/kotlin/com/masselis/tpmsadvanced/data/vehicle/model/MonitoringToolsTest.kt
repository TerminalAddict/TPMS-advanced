package com.masselis.tpmsadvanced.data.vehicle.model

import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith

internal class MonitoringToolsTest {
    @Test fun `maintenance expires on deadline reboot and clock changes`() {
        val window = MaintenanceWindow("vehicle", 600_000, 1600.0, 3)
        assertTrue(window.active(100_000, 1100.0, 3))
        assertFalse(window.active(600_000, 1600.0, 3))
        assertFalse(window.active(100_000, 1100.0, 4))
        assertFalse(window.active(100_000, 1100.0, -1))
        assertFalse(window.active(100_000, 1601.0, 3))
        assertFalse(window.active(100_000, -3000.0, 3))
    }
    @Test fun `active combination always gets full routing while secondary policy is respected`() {
        ParkedAlertRoute.entries.forEach { assertEquals(AlertDelivery(true, true), alertDelivery(true, it, false)) }
        assertEquals(AlertDelivery(true, true), alertDelivery(false, ParkedAlertRoute.ALL, false))
        assertEquals(AlertDelivery(true, false), alertDelivery(false, ParkedAlertRoute.NOTIFICATION_ONLY, true))
        assertEquals(AlertDelivery(false, false), alertDelivery(false, ParkedAlertRoute.CRITICAL_ONLY, false))
        assertEquals(AlertDelivery(true, true), alertDelivery(false, ParkedAlertRoute.CRITICAL_ONLY, true))
        assertEquals(AlertDelivery(false, false), alertDelivery(false, ParkedAlertRoute.OFF, true))
    }
    @Test fun `gauge suggestion preserves multipliers and temperature calibration`() {
        val old = SensorCalibration(10f, 1.1f, 2f, .9f)
        val result = compareGauge(200f, 240f, old)
        assertEquals(230f, result.correctedKpa, .001f)
        assertEquals(10f, result.differenceKpa, .001f)
        val suggestion = assertNotNull(result.suggested)
        assertEquals(20f, suggestion.pressureOffset)
        assertEquals(old.copy(pressureOffset = 20f), suggestion)
        assertEquals(240f, suggestion.pressure(Pressure(200f)).kpa, .001f)
    }
    @Test fun `gauge comparison rejects missing alarm invalid or extreme data`() {
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, 2001f).forEach { invalid ->
            assertFailsWith<IllegalArgumentException> { compareGauge(invalid, 230f, SensorCalibration()) }
            assertFailsWith<IllegalArgumentException> { compareGauge(230f, invalid, SensorCalibration()) }
        }
        assertNull(compareGauge(200f, 400f, SensorCalibration()).suggested)
    }
    private fun reading(time: Double, sensor: Int, kpa: Float = 220f, temp: Float = 20f) =
        Tyre.Located(time, -50, sensor, Pressure(kpa), Temperature(temp), 30u, false, Location.Axle.FRONT)
    @Test fun `ride report preserves replacement identities counts gaps and never reported wheels`() {
        val report = RideSummaryBuilder(setOf("axle:FRONT", "axle:REAR"), 60.0)
        report.add(reading(100.0, 1))
        report.add(reading(100.0, 1))
        report.add(reading(99.0, 1))
        report.add(reading(200.0, 1, 240f, 40f))
        report.add(reading(220.0, 2, 245f, 35f))
        val summary = report.finish(3, 0.0, 300.0)
        assertEquals(2, summary.wheels.size)
        assertEquals(2L, summary.wheels.first().readings)
        assertEquals(220f, summary.wheels.first().startKpa)
        assertEquals(240f, summary.wheels.first().endKpa)
        assertEquals(40f, summary.wheels.first().maxCelsius)
        assertEquals(3, summary.wheels.first().longGaps)
        assertEquals(listOf("axle:REAR"), summary.neverReported)
        assertEquals(3L, summary.alerts)
    }
    @Test fun `empty ride does not invent readings and old settings retain defaults`() {
        val summary = RideSummaryBuilder(setOf("axle:FRONT"), 60.0).finish(0)
        assertTrue(summary.wheels.isEmpty()); assertEquals(listOf("axle:FRONT"), summary.neverReported)
        assertEquals(MonitoringTools(), Json.decodeFromString<RideToolsData>("{}").monitoring)
        assertFailsWith<IllegalArgumentException> { MonitoringTools(readingDays = -1).validate() }
        assertFailsWith<IllegalArgumentException> { MonitoringTools(reportDays = 3651).validate() }
    }
}
