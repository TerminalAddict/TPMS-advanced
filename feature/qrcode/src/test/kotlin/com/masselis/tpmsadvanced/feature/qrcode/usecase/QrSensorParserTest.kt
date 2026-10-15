package com.masselis.tpmsadvanced.feature.qrcode.usecase

import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeSensors
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal class QrSensorParserTest {
    @Test
    fun `double axle trailer retains all four kit sensors and their wheel positions`() {
        val kit = QrSensorParser.parse("11AD8B&21563D&31A4F0&41A552")
        val sensors = kit.forVehicle(Kind.DOUBLE_AXLE_TRAILER)
        assertEquals(4, sensors.map { it.id }.toSet().size)
        assertEquals(Kind.DOUBLE_AXLE_TRAILER.locations, sensors.map { it.location }.toSet())
        assertEquals(kit.map { it.id to it.wheel }, sensors.map { it.id to it.location })
    }

    @Test
    fun `four sensor kits retain normal wheels for spare cars vans and utes`() {
        val kit = QrSensorParser.parse("11AD8B&21563D&31A4F0&41A552")
        listOf(Kind.CAR_WITH_SPARE, Kind.VAN, Kind.UTE).forEach { kind ->
            assertEquals(Kind.CAR.locations, kit.forVehicle(kind).map { it.location }.toSet())
        }
        assertFailsWith<ConflictingLocations> { kit.forVehicle(Kind.MONOWHEEL) }
    }

    @Test
    fun `kit and single sensor IDs retain their different byte layouts`() {
        val kit = QrSensorParser.parse(" 11ad8b&21563d&31a4f0&41a552 ")
        assertEquals(-1951592192, kit.first().id)
        assertEquals(Kind.CAR.locations, kit.forVehicle(Kind.CAR).map { it.location }.toSet())
        val single = assertFailsWith<QrCodeSensorUseCase.SingleSensorQrCode> { QrSensorParser.parse("002D56") }
        assertEquals(0x562D00, single.sensorId)
    }

    @Test
    fun `invalid and partial codes are rejected`() {
        listOf("", "https://example.com/11AD8B&21563D", "11AD8B&21563D&31A4F0", "11AD8B&ZZZZZZ", "11AD8B&21563D&")
            .forEach { code -> assertFailsWith<QrSensorParser.InvalidCode> { QrSensorParser.parse(code) } }
    }

    @Test
    fun `duplicate sensors and wheel labels are rejected`() {
        assertFailsWith<QrCodeSensors.DuplicateId> { QrSensorParser.parse("11AD8B&11AD8B") }
        assertFailsWith<QrCodeSensors.DuplicateWheelLocation> { QrSensorParser.parse("11AD8B&11563D") }
    }

    @Test
    fun `conversion cannot silently overwrite the same tyre`() {
        val frontPair = QrSensorParser.parse("11AD8B&21563D")
        val conflict = assertFailsWith<ConflictingLocations> { frontPair.forVehicle(Kind.MOTORCYCLE) }
        assertEquals(setOf(Location.Axle.FRONT), conflict.locations)
        val four = QrSensorParser.parse("11AD8B&21563D&31A4F0&41A552")
        listOf(Kind.MOTORCYCLE, Kind.SINGLE_AXLE_TRAILER, Kind.TADPOLE_THREE_WHEELER, Kind.DELTA_THREE_WHEELER)
            .forEach { kind -> assertFailsWith<ConflictingLocations> { four.forVehicle(kind) } }
    }

    @Test
    fun `compatible motorcycle and trailer pairs still bind`() {
        val diagonal = QrSensorParser.parse("11AD8B&41A552")
        assertEquals(Kind.MOTORCYCLE.locations, diagonal.forVehicle(Kind.MOTORCYCLE).map { it.location }.toSet())
        assertEquals(Kind.SINGLE_AXLE_TRAILER.locations, diagonal.forVehicle(Kind.SINGLE_AXLE_TRAILER)
            .map { it.location }.toSet())
    }
}
