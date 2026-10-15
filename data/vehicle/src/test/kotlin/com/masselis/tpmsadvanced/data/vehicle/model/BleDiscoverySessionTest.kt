package com.masselis.tpmsadvanced.data.vehicle.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class BleDiscoverySessionTest {
    private fun packet(address: String = "AA:BB:CC:DD:EE:01", time: Double = 1.0, pressure: Float? = null) =
        BleAdvertisement(time, (time * 1e9).toLong(), address, null, -65, "02ff0000", mapOf("0x1234" to "0000"),
            listOf("0000a827-0000-1000-8000-00805f9b34fb"), emptyMap(), false, 0,
            reading = pressure?.let { Tyre.Unlocated(time, -65, -1, Pressure(it), Temperature(20f), 0u, false) })

    @Test fun `unchanged unnamed packets update last seen and remain in capture`() {
        val session = BleDiscoverySession()
        session.observe(packet()); session.observe(packet(time = 20.0))
        assertEquals(20.0, session.devices().single().latest.timestamp)
        assertEquals(2L, session.devices().single().packets)
        assertEquals(2, session.capture().size)
    }

    @Test fun `install mark differentiates new pressure change and mere payload change`() {
        val session = BleDiscoverySession()
        session.observe(packet(pressure = 0f)); session.markInstallation()
        session.observe(packet(time = 2.0, pressure = 1f))
        assertFalse(session.devices().single().pressureChanged)
        session.observe(packet(time = 3.0, pressure = 230f))
        assertTrue(session.devices().single().pressureChanged)
        assertFalse(session.devices().single().newSinceMark)
        session.observe(packet(address = "AA:BB:CC:DD:EE:02"))
        assertTrue(session.devices().first().newSinceMark)
        session.markInstallation()
        assertTrue(session.devices().none { it.newSinceMark || it.pressureChanged || it.payloadChanged })
        session.observe(packet(address = "AA:BB:CC:DD:EE:02").copy(bytes = "010200"))
        assertTrue(session.devices().first().payloadChanged)
        assertFalse(session.devices().first().pressureChanged)
    }

    @Test fun `unknown packets preserve last decoded reading with its original age`() {
        val session = BleDiscoverySession()
        session.observe(packet(pressure = 230f))
        session.observe(packet(time = 90.0))
        assertEquals(1.0, session.devices().single().decoded?.timestamp)
        assertEquals(90.0, session.devices().single().latest.timestamp)
    }

    @Test fun `noisy environments have bounded devices and packet retention`() {
        val session = BleDiscoverySession(deviceLimit = 2, packetLimit = 3)
        repeat(10) { session.observe(packet(address = "device$it", time = it.toDouble())) }
        assertEquals(2, session.devices().size)
        assertEquals(listOf("device7", "device8", "device9"), session.capture().map { it.address })
        assertEquals(10L, session.received)
        assertEquals(listOf("device9"), session.capture("device9").map { it.address })
    }

    @Test fun `export preserves zeros unnamed devices unsigned IDs and escapes packet names`() {
        val value = packet(pressure = 230f).copy(name = "quote\"\nline").jsonLine()
        assertFalse(value.contains('\n'))
        val json = Json.parseToJsonElement(value).jsonObject
        assertEquals("02ff0000", json.getValue("bytes").jsonPrimitive.content)
        assertEquals("4294967295", json.getValue("sensorId").jsonPrimitive.content)
        assertEquals("quote\"\nline", json.getValue("name").jsonPrimitive.content)
        assertEquals("0000", json.getValue("manufacturerData").jsonObject.getValue("0x1234").jsonPrimitive.content)
    }
}
