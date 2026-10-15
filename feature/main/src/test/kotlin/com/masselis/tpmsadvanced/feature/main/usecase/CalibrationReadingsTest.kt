package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.SensorCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

internal class CalibrationReadingsTest {
    @Test fun `calibration corrects cached values while preserving raw data and reception time`() = runTest {
        val raw = Tyre.Located(Tyre.Unlocated(123.0, -60, 1, Pressure(200f), Temperature(20f), 0u, false),
            Location.Axle.FRONT)
        val records = MutableStateFlow(raw)
        val source = mockk<ListenTyreUseCase> { every { listen() } returns records }
        val settings = MutableStateFlow(emptyMap<Int, SensorCalibration>())
        val readings = TyreAtmosphereUseCase(source, calibrations = settings)
        assertEquals(200f, readings.listen().first().pressure.kpa)
        settings.value = mapOf(1 to SensorCalibration(10f, 1.1f, -2f, 1f))
        val corrected = readings.listen().first()
        assertEquals(230f, corrected.pressure.kpa)
        assertEquals(18f, corrected.temperature.celsius)
        assertEquals(123.0, corrected.timestamp)
        assertEquals(200f, records.value.pressure.kpa)
        settings.value = mapOf(2 to SensorCalibration(10f, 1.1f, -2f, 1f))
        assertEquals(200f, readings.listen().first().pressure.kpa)
    }
}
