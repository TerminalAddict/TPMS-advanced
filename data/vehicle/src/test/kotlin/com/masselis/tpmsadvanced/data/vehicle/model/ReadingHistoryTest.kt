package com.masselis.tpmsadvanced.data.vehicle.model

import org.junit.Test
import kotlin.test.assertEquals

internal class ReadingHistoryTest {
    private fun reading(time: Double) = Tyre.Located(time, -50, 1, Pressure(200f), Temperature(20f),
        0u, false, Location.Axle.FRONT)
    @Test fun `gaps split sessions and readings from several tyres remain in one session`() {
        val sessions = readingSessions(listOf(reading(2_000.0), reading(100.0), reading(105.0), reading(106.0)))
        assertEquals(listOf(ReadingSession(100.0, 106.0, 3), ReadingSession(2_000.0, 2_000.0, 1)), sessions)
    }
    @Test fun `CSV escapes names quotes and spreadsheet formulas`() {
        assertEquals("\"My, \"\"bike\"\"\"", csvCell("My, \"bike\""))
        assertEquals("\"'=HYPERLINK(1)\"", csvCell("=HYPERLINK(1)"))
    }
}
