package com.masselis.tpmsadvanced.data.vehicle.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class SensorCalibrationTest {
    @Test fun `identity and calibrated units are predictable`() {
        assertEquals(Pressure(230f), SensorCalibration().pressure(Pressure(230f)))
        val calibration = SensorCalibration(-10f, 1.1f, -2f, 1.2f)
        assertEquals(Pressure(210f), calibration.pressure(Pressure(200f)))
        assertEquals(Temperature(22f), calibration.temperature(Temperature(20f)))
    }
    @Test fun `open air and sensor alarm cannot become normal pressure through an offset`() {
        assertEquals(Pressure(0f), SensorCalibration(100f).pressure(Pressure(0f)))
        assertEquals(Pressure(0f), SensorCalibration(-100f).pressure(Pressure(10f)))
    }
    @Test fun `rejects invalid numbers and unreasonable multipliers`() {
        assertTrue(SensorCalibration().isValid())
        assertFalse(SensorCalibration(pressureOffset = Float.NaN).isValid())
        assertFalse(SensorCalibration(pressureMultiplier = Float.POSITIVE_INFINITY).isValid())
        assertFalse(SensorCalibration(temperatureMultiplier = -1f).isValid())
    }
}
