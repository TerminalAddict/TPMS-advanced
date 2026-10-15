package com.masselis.tpmsadvanced.data.vehicle.model

import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class PressureLossTest {
    private val tracker = PressureLossTracker()
    private fun reading(time: Double, pressure: Float, temp: Float = 25f, id: Int = 1) =
        Tyre.Unlocated(time, -60, id, pressure.kpa, temp.celsius, 50u, false)

    @Test fun `repeated loss is confirmed even while warming`() {
        assertNull(tracker.next(reading(0.0, 240f)))
        assertNull(tracker.next(reading(10.0, 233f, 26f)))
        assertNull(tracker.next(reading(14.0, 231f, 27f)))
        assertEquals(PressureLoss(10f.kpa, 0.0), tracker.next(reading(15.0, 230f, 27f)))
    }

    @Test fun `duplicates and old timestamps cannot confirm a drop`() {
        tracker.next(reading(0.0, 240f))
        tracker.next(reading(10.0, 230f))
        assertNull(tracker.next(reading(10.0, 230f)))
        assertNull(tracker.next(reading(5.0, 220f)))
        assertNotNull(tracker.next(reading(15.0, 230f)))
    }

    @Test fun `small fluctuations and isolated drop do not warn`() {
        tracker.next(reading(0.0, 240f))
        assertNull(tracker.next(reading(10.0, 234f)))
        assertNull(tracker.next(reading(20.0, 232f)))
        assertNull(tracker.next(reading(30.0, 238f)))
        assertNull(tracker.next(reading(40.0, 232f)))
    }

    @Test fun `cooling starts a new baseline`() {
        tracker.next(reading(0.0, 240f))
        assertNull(tracker.next(reading(10.0, 230f, 23f)))
        assertNull(tracker.next(reading(20.0, 230f, 23f)))
    }

    @Test fun `gaps sensor changes and removal clear the trend`() {
        tracker.next(reading(0.0, 240f))
        tracker.next(reading(10.0, 230f))
        assertNull(tracker.next(reading(610.0, 220f)))
        assertNull(tracker.next(reading(620.0, 210f, id = 2)))
        assertNull(tracker.next(reading(630.0, 0f, id = 2)))
        assertNull(tracker.next(reading(640.0, 200f, id = 2)))
    }

    @Test fun `recovery and invalid data clear an existing warning`() {
        tracker.next(reading(0.0, 240f))
        tracker.next(reading(10.0, 230f))
        assertNotNull(tracker.next(reading(15.0, 230f)))
        assertNull(tracker.next(reading(20.0, 240f)))
        tracker.next(reading(30.0, 230f))
        assertNotNull(tracker.next(reading(35.0, 230f)))
        assertNull(tracker.next(reading(40.0, Float.NaN)))
        assertNull(tracker.next(reading(50.0, 200f)))
    }
}
