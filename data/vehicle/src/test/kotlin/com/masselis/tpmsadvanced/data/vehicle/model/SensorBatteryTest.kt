package com.masselis.tpmsadvanced.data.vehicle.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class SensorBatteryTest {
    @Test
    fun `warning boundaries keep voltage and percentage separate`() {
        assertTrue(SensorBattery.Voltage(2.59f).isLow)
        assertFalse(SensorBattery.Voltage(2.6f).isLow)
        assertTrue(SensorBattery.Percentage(10).isLow)
        assertFalse(SensorBattery.Percentage(11).isLow)
    }

    @Test
    fun `cache decoding preserves units and leaves legacy or invalid values unknown`() {
        assertEquals(SensorBattery.Voltage(2.97f), SensorBattery.fromStored(2.97, null))
        assertEquals(SensorBattery.Percentage(35), SensorBattery.fromStored(null, 35))
        assertNull(SensorBattery.fromStored(null, null))
        assertNull(SensorBattery.fromStored(null, 255))
        assertNull(SensorBattery.fromStored(Double.NaN, null))
    }
}
