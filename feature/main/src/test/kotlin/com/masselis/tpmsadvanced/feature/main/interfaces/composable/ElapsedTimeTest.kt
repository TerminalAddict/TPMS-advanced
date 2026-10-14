
package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

internal class ElapsedTimeTest {

    private val timestamp = 1_700_000_000.0

    @Test
    fun `formats elapsed time correctly`() {
        assertEquals("<1 min", elapsedLabel(timestamp, timestamp + 25))
        assertEquals("5 min", elapsedLabel(timestamp, timestamp + 5 * 60))
        assertEquals("119 min", elapsedLabel(timestamp, timestamp + 119 * 60))
        assertEquals("2 hours", elapsedLabel(timestamp, timestamp + 120 * 60))
        assertEquals("47 hours", elapsedLabel(timestamp, timestamp + 47 * 3600))
        assertEquals("2 days", elapsedLabel(timestamp, timestamp + 48 * 3600))
        assertEquals("3 days", elapsedLabel(timestamp, timestamp + 72 * 3600))
    }

    @Test
    fun `future timestamps do not produce negative elapsed time`() {
        assertEquals("<1 min", elapsedLabel(timestamp, timestamp - 10))
    }

    @Test
    fun `timer wakes at the next minute boundary`() {
        assertEquals(35.seconds, nextElapsedTick(timestamp, timestamp + 25))
        assertEquals(30.seconds, nextElapsedTick(timestamp, timestamp + 90))
    }

    @Test
    fun `timer wakes at the next hour boundary`() {
        assertEquals(
            3570.seconds,
            nextElapsedTick(timestamp, timestamp + 2 * 3600 + 30)
        )
    }

    @Test
    fun `timer wakes at the next day boundary`() {
        assertEquals(
            (23 * 3600).seconds,
            nextElapsedTick(timestamp, timestamp + 49 * 3600)
        )
    }
}

