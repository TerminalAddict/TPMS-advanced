package com.masselis.tpmsadvanced.data.vehicle.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.test.assertNotNull

internal class DrivingInsightsTest {
    private val car = UUID.randomUUID().toString()
    private val trailer = UUID.randomUUID().toString()
    private val combo = TowingCombination(name = "Ute and trailer", vehicle = car, trailer = trailer)
    private val baseline = ColdBaseline(1000.0, mapOf("wheel:FRONT_LEFT" to ColdSample(1, 990.0, 200f, 20f)))
    private fun wheel(location: Location, pressure: Float, timestamp: Double = 1000.0) =
        WheelHealth(location, TyreAtmosphere(timestamp, location.hashCode(), Pressure(pressure), Temperature(25f)),
            WheelReadiness.HEALTHY, emptyList())
    private val pair = listOf(wheel(Location.Wheel.FRONT_LEFT, 200f), wheel(Location.Wheel.FRONT_RIGHT, 230f))

    @Test fun `imbalance warns inside normal pressure range at inclusive threshold`() {
        assertEquals(30f, axleImbalances(pair, 30f, 1000.0).single().differenceKpa)
        assertTrue(axleImbalances(pair, 31f, 1000.0).isEmpty())
        assertTrue(axleImbalances(pair, null, 1000.0).isEmpty())
    }
    @Test fun `front rear and spare are never compared to each other`() {
        val mixed = listOf(wheel(Location.Wheel.FRONT_LEFT, 100f), wheel(Location.Wheel.REAR_RIGHT, 300f),
            wheel(Location.Auxiliary.SPARE, 500f))
        assertTrue(axleImbalances(mixed, 5f, 1000.0).isEmpty())
    }
    @Test fun `both trailer axle styles can detect imbalance`() {
        val single = listOf(wheel(Location.Side.LEFT, 200f), wheel(Location.Side.RIGHT, 240f))
        assertEquals("Trailer", axleImbalances(single, 20f, 1000.0).single().axle)
        val tandem = pair + listOf(wheel(Location.Wheel.REAR_LEFT, 210f), wheel(Location.Wheel.REAR_RIGHT, 250f))
        assertEquals(listOf("Front", "Rear"), axleImbalances(tandem, 20f, 1000.0).map { it.axle })
    }
    @Test fun `stale future alarm and mismatched times cannot create imbalance`() {
        assertTrue(axleImbalances(pair, 10f, 1120.1).isEmpty())
        assertTrue(axleImbalances(pair, 10f, 999.0).isEmpty())
        assertTrue(axleImbalances(listOf(pair.first(), wheel(Location.Wheel.FRONT_RIGHT, 0f)), 10f, 1000.0).isEmpty())
        val mismatched = listOf(pair.first(), wheel(Location.Wheel.FRONT_RIGHT, 250f, 939.0))
        assertTrue(axleImbalances(mismatched, 10f, 1000.0).isEmpty())
    }
    @Test fun `cold comparison applies current calibration consistently and preserves negative changes`() {
        val calibration = SensorCalibration(pressureOffset = 10f, pressureMultiplier = 1.1f,
            temperatureOffset = 2f, temperatureMultiplier = 1f)
        val reading = TyreAtmosphere(1100.0, 1, calibration.pressure(Pressure(220f)),
            calibration.temperature(Temperature(18f)))
        val change = assertNotNull(thermalChange(baseline, Location.Wheel.FRONT_LEFT, reading, calibration, 1100.0))
        assertEquals(22f, change.pressureRiseKpa, .001f)
        assertEquals(-2f, change.temperatureRiseCelsius)
    }
    @Test fun `cold comparison rejects replaced sensor expired baseline stale and nonpositive readings`() {
        val reading = TyreAtmosphere(1100.0, 1, Pressure(220f), Temperature(30f))
        fun compare(value: TyreAtmosphere = reading, time: Double = 1100.0) =
            thermalChange(baseline, Location.Wheel.FRONT_LEFT, value, SensorCalibration(), time)
        assertNull(compare(reading.copy(sensorId = 2)))
        assertNull(compare(reading.copy(timestamp = 990.0)))
        assertNull(compare(time = 1220.1))
        assertNull(compare(reading.copy(timestamp = 45000.0), 45000.0))
        assertNull(compare(reading.copy(pressure = Pressure(0f))))
    }
    @Test fun `cold baseline requires fresh unique sensor samples and finite values`() {
        baseline.validate()
        assertFailsWith<IllegalArgumentException> { baseline.copy(confirmedAt = 1111.0).validate() }
        assertFailsWith<IllegalArgumentException> { baseline.copy(samples = baseline.samples +
            ("wheel:FRONT_RIGHT" to baseline.samples.values.single())).validate() }
        assertFailsWith<IllegalArgumentException> { baseline.copy(confirmedAt = Double.NaN).validate() }
        assertFailsWith<IllegalArgumentException> { baseline.copy(confirmedAt = Double.MAX_VALUE,
            samples = mapOf("wheel:FRONT_LEFT" to ColdSample(1, Double.MAX_VALUE, 200f, 20f))).validate() }
    }
    @Test fun `combinations restrict tow and trailer kinds and prune deleted vehicles`() {
        val kinds = mapOf(car to Vehicle.Kind.UTE, trailer to Vehicle.Kind.DOUBLE_AXLE_TRAILER)
        combo.validate(); assertTrue(combo.isValidFor(kinds))
        assertFalse(combo.isValidFor(kinds + (trailer to Vehicle.Kind.CAR)))
        val insight = DrivingInsights(listOf(combo), combo.id, mapOf(car to 20f), cold = mapOf(car to baseline))
        assertEquals(combo, insight.activeFor(UUID.fromString(car), kinds))
        assertNull(insight.activeFor(UUID.fromString(trailer), kinds))
        assertNull(insight.activeFor(UUID.fromString(car), kinds - trailer))
        assertEquals(insight, insight.forVehicles(kinds))
        val pruned = insight.forVehicles(mapOf(trailer to Vehicle.Kind.DOUBLE_AXLE_TRAILER))
        assertTrue(pruned.combinations.isEmpty()); assertNull(pruned.activeCombination)
        assertTrue(pruned.cold.isEmpty()); assertTrue(pruned.imbalanceKpa.isEmpty())
    }
    @Test fun `settings validate limits duplicates and backwards compatible defaults`() {
        assertEquals(DrivingInsights(), Json.decodeFromString<RideToolsData>("{}").insights)
        // Existing installations with enlarged text keep the diagram when this new option is absent.
        assertFalse(Json.decodeFromString<DrivingInsights>(
            """{"highVisibility":true,"readoutScale":1.5}""").readingCards)
        listOf(DrivingInsights(readoutScale = Float.NaN), DrivingInsights(readoutScale = 3f),
            DrivingInsights(broadcastSeconds = mapOf(1 to 0)), DrivingInsights(activeCombination = combo.id),
            DrivingInsights(combinations = listOf(combo, combo.copy(id = UUID.randomUUID().toString()))),
            DrivingInsights(imbalanceKpa = mapOf(car to Float.POSITIVE_INFINITY)))
            .forEach { assertFailsWith<IllegalArgumentException> { it.validate() } }
    }
    @Test fun `new insight settings survive serialization and configuration backup`() {
        val insight = DrivingInsights(listOf(combo), combo.id, mapOf(car to 20f), mapOf(1 to 30),
            mapOf(car to baseline), true, 1.5f, readingCards = true)
        val settings = BackupSettings(rideTools = RideToolsData(insights = insight))
        fun vehicle(id: String, kind: String, current: Boolean) = BackupVehicle(id, kind, kind, current,
            100f, 400f, false, 100f, 400f, -20f, 40f, 90f, emptyList())
        val backup = ConfigurationBackup(vehicles = listOf(vehicle(car, "UTE", true),
            vehicle(trailer, "DOUBLE_AXLE_TRAILER", false)), settings = settings)
        assertEquals(backup, ConfigurationBackup.decode(backup.encode()))
        assertEquals(insight, Json.decodeFromString<DrivingInsights>(Json.encodeToString(insight)))
        assertFailsWith<IllegalArgumentException> { backup.copy(vehicles = backup.vehicles.take(1)).validate() }
    }
    @Test fun `merge drops combinations touching replaced vehicles and retains independent records`() {
        val existing = RideToolsData(insights = DrivingInsights(listOf(combo), combo.id, mapOf(car to 20f)))
        assertTrue(mergeRideTools(existing, RideToolsData(), listOf(car)).insights.combinations.isEmpty())
        assertEquals(listOf(combo), mergeRideTools(existing, RideToolsData(), emptyList()).insights.combinations)
    }
}
