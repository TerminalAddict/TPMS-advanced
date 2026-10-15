package com.masselis.tpmsadvanced.data.vehicle.model

import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class ConfigurationBackupTest {
    private fun vehicle(kind: Vehicle.Kind = Vehicle.Kind.CAR_WITH_SPARE) = BackupVehicle(
        UUID.randomUUID().toString(), kind.name, "Paul's vehicle", true,
        180f, 300f, false, 190f, 320f, -20f, 45f, 90f,
        kind.locations.mapIndexed { index, location -> BackupSensor(Int.MIN_VALUE + index, backupLocation(location)) }
    )
    private fun backup(vehicle: BackupVehicle = vehicle()) = ConfigurationBackup(vehicles = listOf(vehicle),
        settings = BackupSettings(pressureUnit = "PSI", temperatureUnit = "FAHRENHEIT", alertSound = "SPEECH",
            calibrations = mapOf(Int.MIN_VALUE to BackupCalibration(4f, 1.1f, -2f, 1f)),
            triggers = mapOf(vehicle.uuid to BackupRideTrigger("AA:BB:CC:DD:EE:FF", true, -75))))

    @Test fun `round trip preserves unsigned sensor bits calibrations and all vehicle kinds`() {
        Vehicle.Kind.entries.forEach { kind ->
            val original = backup(vehicle(kind))
            assertEquals(original, ConfigurationBackup.decode(original.encode()))
            assertTrue(original.encode().contains("\"version\": 1"))
        }
    }

    @Test fun `unsupported versions malformed JSON and oversized input are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ConfigurationBackup.decode(backup().encode().replace("\"version\": 1", "\"version\": 2"))
        }
        assertFailsWith<IllegalArgumentException> { ConfigurationBackup.decode("{broken}") }
        assertFailsWith<IllegalArgumentException> {
            ConfigurationBackup.decode(" ".repeat(ConfigurationBackup.MAX_CHARACTERS + 1))
        }
    }

    @Test fun `duplicate sensor ownership is rejected across vehicles`() {
        val first = vehicle()
        val second = vehicle().copy(current = false)
        assertFailsWith<IllegalArgumentException> { backup(first).copy(vehicles = listOf(first, second)).validate() }
    }

    @Test fun `invalid UUID wheel and duplicate positions are rejected`() {
        val source = vehicle()
        listOf(source.copy(uuid = "1-1-1-1-1"), source.copy(kind = "SPACE_SHIP"),
            source.copy(sensors = listOf(BackupSensor(1, "aux:SINGLE"))),
            source.copy(sensors = listOf(BackupSensor(1, "wheel:FRONT_LEFT"), BackupSensor(2, "wheel:FRONT_LEFT"))))
            .forEach { invalid -> assertFailsWith<IllegalArgumentException> { backup(invalid).validate() } }
    }

    @Test fun `thresholds nonfinite calibration and invalid trigger are rejected`() {
        val source = backup()
        listOf(source.copy(vehicles = listOf(source.vehicles.single().copy(lowPressure = 400f))),
            source.copy(vehicles = listOf(source.vehicles.single().copy(highTemp = Float.NaN))),
            source.copy(settings = source.settings.copy(calibrations = mapOf(1 to BackupCalibration(0f,
                Float.POSITIVE_INFINITY, 0f, 1f)))),
            source.copy(settings = source.settings.copy(triggers = mapOf("unknown" to BackupRideTrigger("bad", true,
                127)))))
            .forEach { assertFailsWith<IllegalArgumentException> { it.validate() } }
    }

    @Test fun `exactly one current vehicle is required`() {
        val first = vehicle()
        assertFailsWith<IllegalArgumentException> { backup(first.copy(current = false)).validate() }
        assertFailsWith<IllegalArgumentException> { backup(first).copy(vehicles = listOf(first,
            vehicle().copy(sensors = emptyList()))).validate() }
    }
}
