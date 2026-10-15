package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import co.touchlab.kermit.CommonWriter
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.data.vehicle.interfaces.impl.utils.mockScanRecord
import com.masselis.tpmsadvanced.data.vehicle.interfaces.impl.utils.mockScanResult
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBattery
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class MockSensorPacketTest {
    @Before
    fun logging() { Logger.setLogWriters(CommonWriter()) }

    private fun decode(bytes: ByteArray): Tyre {
        val fields = mutableMapOf<Int, ByteArray>()
        var index = 0
        while (index < bytes.size) {
            val length = bytes[index].toInt() and 0xFF
            fields[bytes[index + 1].toInt() and 0xFF] = bytes.copyOfRange(index + 2, index + length + 1)
            index += length + 1
        }
        val record = mockScanRecord(
            mockDeviceName = fields[8]?.decodeToString() ?: "",
            mockAdvertiseFlags = fields[1]?.firstOrNull()?.toInt() ?: -1,
            mockBytes = bytes,
            mockManufacturerData = fields.getValue(0xFF).drop(2).toByteArray()
        )
        return assertIs<Tyre>(assertNotNull(decodeAdvertisement(mockScanResult(record))).asTyre())
    }

    @Test
    fun `Bekubee generated packet reaches production decoder with intended readings`() {
        listOf(0f, 50f, 230f, 1000f).forEach { pressure ->
            val tyre = decode(MockSensorPacket.bekubee(0x562D00, pressure, 21f, 3f))
            assertEquals(0x562D00, tyre.sensorId)
            assertEquals(pressure, tyre.pressure.kpa)
            assertEquals(21f, tyre.temperature.celsius)
            assertEquals(30.toUShort(), tyre.battery)
            assertEquals(false, tyre.isAlarm)
        }
    }

    @Test
    fun `Sysgration generator preserves position alarm and percentage`() {
        Location.Wheel.entries.forEachIndexed { wheel, location ->
            val tyre = assertIs<Tyre.Located>(decode(MockSensorPacket.sysgration(
                0x123400, 222.5f, -10f, 25, wheel, true
            )))
            assertEquals(location, tyre.location)
            assertEquals(0x123400, tyre.sensorId)
            assertEquals(222.5f, tyre.pressure.kpa)
            assertEquals(-10f, tyre.temperature.celsius)
            assertEquals(25.toUShort(), tyre.battery)
            assertEquals(true, tyre.isAlarm)
        }
    }

    @Test
    fun `low battery retains measured pressure and its original unit`() {
        val voltage = decode(MockSensorPacket.bekubee(0x562D00, 230f, 21f, 2f))
        assertEquals(SensorBattery.Voltage(2f), voltage.sensorBattery)
        assertEquals(true, voltage.sensorBattery?.isLow)
        assertEquals(230f, voltage.pressure.kpa)
        assertEquals(false, voltage.isAlarm)
        val percentage = decode(MockSensorPacket.sysgration(0x123400, 230f, 21f, 5, 0, false))
        assertEquals(SensorBattery.Percentage(5), percentage.sensorBattery)
        assertEquals(true, percentage.sensorBattery?.isLow)
        assertEquals(false, percentage.isAlarm)
    }

    @Test
    fun `replays an actual captured Bekubee packet`() {
        val tyre = decode("050854504D530CFF0200AF4500A7002D56A8B4030327A8".hexToByteArray())
        assertEquals(0x562D00, tyre.sensorId)
        assertEquals(67f, tyre.pressure.kpa)
        assertEquals(19f, tyre.temperature.celsius)
    }

    @Test
    fun `out of range and non-finite values are rejected`() {
        assertFailsWith<IllegalArgumentException> { MockSensorPacket.bekubee(1, Float.NaN, 20f, 3f) }
        assertFailsWith<IllegalArgumentException> { MockSensorPacket.bekubee(1, 200f, 20f, 4f) }
        assertFailsWith<IllegalArgumentException> { MockSensorPacket.sysgration(1, 200f, 20f, 50, 0, false) }
    }

    @Test
    fun `truncated packets are dropped instead of terminating the scanner`() {
        assertNull(decodeAdvertisement(mockScanResult(mockScanRecord(
            mockDeviceName = "BR", mockBytes = byteArrayOf(1, 2)
        ))))
    }
}
