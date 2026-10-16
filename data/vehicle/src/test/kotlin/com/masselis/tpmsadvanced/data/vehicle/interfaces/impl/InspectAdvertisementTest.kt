package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.os.ParcelUuid
import co.touchlab.kermit.CommonWriter
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class InspectAdvertisementTest {
    @Before fun logging() { Logger.setLogWriters(CommonWriter()) }

    private fun result(name: String?, data: String, bytes: String, service: Boolean = true): ScanResult {
        val uuid = mockk<ParcelUuid>()
        every { uuid.toString() } returns "0000a827-0000-1000-8000-00805f9b34fb"
        val record = mockk<ScanRecord> {
            every { deviceName } returns name
            every { advertiseFlags } returns 0
            every { this@mockk.bytes } returns bytes.hexToByteArray()
            every { serviceUuids } returns mockk {
                every { contains(any()) } returns service
                every { size } returns if (service) 1 else 0
                every { iterator() } answers {
                    if (service) mutableListOf(uuid).iterator() else mutableListOf<ParcelUuid>().iterator()
                }
            }
            every { serviceData } returns emptyMap<ParcelUuid, ByteArray>()
            every { manufacturerSpecificData } returns mockk {
                every { size() } returns 1
                every { keyAt(0) } returns 2
                every { valueAt(0) } returns data.hexToByteArray()
            }
        }
        return mockk {
            every { scanRecord } returns record
            every { device } returns mockk { every { address } returns "AA:BB:CC:DD:EE:01" }
            every { rssi } returns -65
            every { timestampNanos } returns 123L
            every { isConnectable } returns false
            every { dataStatus } returns 0
        }
    }

    @Test fun `captured Bekubee packet is recognised with complete diagnostic evidence`() {
        val packet = inspectAdvertisement(result("TPMS", "AF4500A7002D56A8B4",
            "050854504D530CFF0200AF4500A7002D56A8B4030327A80000"))
        assertEquals("Bekubee TPMS (A827)", packet.decoder)
        val tyre = assertIs<Tyre>(packet.reading)
        assertEquals(0x562D00, tyre.sensorId)
        assertEquals(67f, tyre.pressure.kpa)
        assertEquals(19f, tyre.temperature.celsius)
        assertEquals("af4500a7002d56a8b4", packet.manufacturerData["0x0002"])
        assertEquals(true, packet.bytes.endsWith("0000"))
    }

    @Test fun `unnamed unrelated advertisements remain visible and cannot be assigned`() {
        val packet = inspectAdvertisement(result(null, "010203", "06ff0200010203", false))
        assertNull(packet.name)
        assertNull(packet.reading)
        assertEquals("06ff0200010203", packet.bytes)
        assertTrue(packet.qualityWarnings.isEmpty())
    }

    @Test fun `truncated KY BR and TPMS packets cannot terminate discovery`() {
        listOf("KY", "BR", "TPMS").forEach { name ->
            val packet = inspectAdvertisement(result(name, "00", "0100"))
            assertNull(packet.reading)
            assertEquals("0100", packet.bytes)
            assertTrue(packet.qualityWarnings.any { it.contains("could not be decoded") })
        }
    }

    @Test fun `implausible finite packet fields remain usable for emergency alerts with visible warnings`() {
        val packet = inspectAdvertisement(result("TPMS", "AFFFFFFF002D56A8B4", "0100"))
        val tyre = assertIs<Tyre>(packet.reading)
        assertEquals(65435f, tyre.pressure.kpa)
        assertEquals(205f, tyre.temperature.celsius)
        assertEquals(2, packet.qualityWarnings.size)
        assertTrue(packet.jsonLine().contains("qualityWarnings"))
    }

    @Test fun `zero pressure reaches existing alarm logic without an implausible-pressure warning`() {
        val packet = inspectAdvertisement(result("TPMS", "AF450064002D56A8B4", "0100"))
        assertEquals(0f, assertIs<Tyre>(packet.reading).pressure.kpa)
        assertTrue(packet.qualityWarnings.isEmpty())
    }

    @Test fun `Sysgration address-like data with invalid length or wheel stays unknown`() {
        listOf("80eaca", "00eaca00000000000000000000000000").forEach { data ->
            assertNull(inspectAdvertisement(result(null, data, "0100", false)).reading)
        }
    }
}
