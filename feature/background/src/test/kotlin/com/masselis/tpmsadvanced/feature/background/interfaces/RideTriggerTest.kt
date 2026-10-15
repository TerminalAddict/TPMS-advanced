package com.masselis.tpmsadvanced.feature.background.interfaces

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class RideTriggerTest {
    @Test fun `requires two separated strong beacon packets`() {
        val presence = BeaconPresence(-80)
        presence.received(0, -90)
        presence.received(1000, -70)
        assertFalse(presence.isPresent(1000))
        presence.received(1500, -70)
        assertFalse(presence.isPresent(1500))
        presence.received(2000, -70)
        assertTrue(presence.isPresent(2000))
    }

    @Test fun `weak packets do not prolong presence and a new ride needs confirmation`() {
        val presence = BeaconPresence(-80)
        presence.received(0, -70)
        presence.received(1000, -70)
        presence.received(60000, -90)
        assertTrue(presence.isPresent(61000))
        assertFalse(presence.isPresent(61001))
        presence.received(62000, -70)
        assertFalse(presence.isPresent(62000))
        presence.received(63000, -70)
        assertTrue(presence.isPresent(63000))
    }

    @Test fun `rejects invalid addresses and signal thresholds`() {
        assertTrue(RideTrigger("AA:BB:CC:DD:EE:FF", true).isValid())
        assertFalse(RideTrigger("AA:BB:CC", true).isValid())
        assertFalse(RideTrigger("AA:BB:CC:DD:EE:FF", true, 0).isValid())
    }

    @Test fun `short disconnect cancels pending stop but sustained loss stops`() = runTest {
        val source = MutableSharedFlow<RidePresence>()
        var monitoring = false
        val waiting = RidePresence(false, "Waiting")
        val connected = RidePresence(true, "Connected")
        source.withDisconnectGrace { monitoring }.test {
            source.emit(waiting)
            assertEquals(waiting, awaitItem())
            source.emit(connected)
            assertEquals(connected, awaitItem())
            monitoring = true
            source.emit(waiting)
            advanceTimeBy(19000)
            expectNoEvents()
            source.emit(connected)
            assertEquals(connected, awaitItem())
            advanceTimeBy(2000)
            expectNoEvents()
            source.emit(waiting)
            advanceTimeBy(20000)
            runCurrent()
            assertEquals(waiting, awaitItem())
        }
    }
}
