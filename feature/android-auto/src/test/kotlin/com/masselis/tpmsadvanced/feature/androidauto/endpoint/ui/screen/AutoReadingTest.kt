package com.masselis.tpmsadvanced.feature.androidauto.endpoint.ui.screen

import com.masselis.tpmsadvanced.data.unit.model.PressureUnit
import com.masselis.tpmsadvanced.data.unit.model.TemperatureUnit
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow.State
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class AutoReadingTest {
    private val reading = State.Normal(1000.0, 1, Pressure(230f), PressureUnit.PSI,
        Temperature(25f), TemperatureUnit.CELSIUS)
    @Test fun `age updates without a new packet and stale readings are explicit`() {
        assertEquals("Last seen 120s ago", autoFreshness(reading, 1120.0))
        assertEquals("STALE · last seen 2 min ago", autoFreshness(reading, 1121.0))
        assertEquals("Freshness unconfirmed", autoFreshness(reading, 999.0))
        assertEquals("Freshness unconfirmed", autoFreshness(reading.copy(timestamp = 0.0), 1000.0))
    }
    @Test fun `missing readings and warnings have text labels independent of colour`() {
        assertEquals("Sensor not detected", autoReading(State.NotDetected))
        assertEquals("Assign or wake sensor while parked", autoFreshness(State.NotDetected, 1000.0))
        assertTrue(autoReading(reading).contains("psi"))
        assertTrue(autoReading(State.Alerting(1000.0, 1, Pressure(100f), PressureUnit.PSI,
            Temperature(25f), TemperatureUnit.CELSIUS)).startsWith("WARNING"))
    }
}
