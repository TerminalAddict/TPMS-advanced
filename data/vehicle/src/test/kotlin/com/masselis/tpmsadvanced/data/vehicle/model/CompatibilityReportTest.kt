package com.masselis.tpmsadvanced.data.vehicle.model

import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

internal class CompatibilityReportTest {
    private fun packet(bytes: String, time: Long = 1000000000) = BleAdvertisement(1760000000.0, time,
        "AA:BB:CC:DD:EE:FF", "Secret sensor", -60, bytes, mapOf("0xBEEF" to "DEADBEEF"),
        listOf("secret-uuid"), mapOf("secret-service" to "DEADBEEF"), false, 0)
    @Test fun `AD fields follow length type structure and retain offsets`() {
        val structure = advertisementStructure("02010605ffdeadbeef00")
        assertFalse(structure.truncated)
        assertEquals(listOf(0, 3), structure.fields.map { it.offset })
        assertEquals(listOf(1, 255), structure.fields.map { it.type })
        assertEquals("deadbeef", structure.fields.last().payload)
    }
    @Test fun `malformed or truncated data stays safe to inspect`() {
        listOf("1", "GG", "-1", "+1", "-1ff", "05ff12", "ff", "ab".repeat(5000)).forEach {
            assertTrue(advertisementStructure(it).truncated)
        }
        assertTrue(advertisementStructure("").fields.isEmpty())
    }
    @Test fun `varying offsets compare only like length packets`() {
        val offsets = varyingByteOffsets(listOf(packet("020106"), packet("020105"), packet("0101")))
        assertEquals(listOf(2), offsets[3]); assertEquals(emptyList(), offsets[2])
    }
    @Test fun `anonymous reports strip identifiers even when embedded in unknown payloads`() {
        val report = anonymisedCompatibilityReport(listOf(packet("05ffdeadbeef"), packet("05ffdeadbe00", 3000000000)))
        Json.parseToJsonElement(report)
        listOf("AA:BB", "Secret sensor", "deadbeef", "DEADBEEF", "secret-uuid", "secret-service", "1760000000")
            .forEach { assertFalse(report.contains(it), it) }
        assertTrue(report.contains("sensor-1")); assertTrue(report.contains("relativeSeconds\":2.0"))
        assertTrue(report.contains("\"offsets\":[5]"))
    }
}
